/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.lineage.dataplex;

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.service.DataformProjectDefaults;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.DataplexLineageCache;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.DataplexLineageCacheImpl;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.Expansion;
import io.github.rejeb.dataform.language.lineage.dataplex.cache.LineageCacheEntry;
import io.github.rejeb.dataform.language.util.concurrent.ParallelCalls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.github.rejeb.dataform.language.lineage.dataplex.FakeLineageClient.fqn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataplexLineageServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final String ROOT = LineageNode.idOf("p.d.a");

    private static String table(String name, String type) {
        return "{\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"" + name + "\"},\"enumType\":\"" + type
                + "\",\"fileName\":\"definitions/" + name + ".sqlx\"}";
    }

    private static CompiledGraph compiled(String... tables) {
        return new Gson().fromJson("{\"tables\":[" + String.join(",", tables) + "],"
                + "\"assertions\":[{\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"check\"},\"query\":\"select 1\"}]}",
                CompiledGraph.class);
    }

    private static CompiledGraph compiled() {
        return compiled(table("a", "table"), table("v", "view"));
    }

    private static DataplexLineageServiceImpl service(FakeLineageClient client) {
        return service(client, new DataformProjectDefaults("p", "d", "EU", null));
    }

    private static DataplexLineageServiceImpl service(FakeLineageClient client, DataformProjectDefaults defaults) {
        return new DataplexLineageServiceImpl(client, Clock.fixed(NOW, ZoneOffset.UTC), graph -> defaults);
    }

    private static DataplexLineageServiceImpl service(FakeLineageClient client, DataplexLineageCache cache) {
        return new DataplexLineageServiceImpl(client, Clock.fixed(NOW, ZoneOffset.UTC),
                graph -> new DataformProjectDefaults("p", "d", "EU", null), cache, ParallelCalls.direct());
    }

    private static final class CountingCache implements DataplexLineageCache {
        private final DataplexLineageCacheImpl delegate = new DataplexLineageCacheImpl();
        private final java.util.concurrent.atomic.AtomicInteger gets = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public @Nullable LineageCacheEntry get(@NotNull LineageScope scope, @NotNull TimeRange range) {
            gets.incrementAndGet();
            return delegate.get(scope, range);
        }

        @Override
        public void put(@NotNull LineageCacheEntry entry) {
            delegate.put(entry);
        }

        @Override
        public void remove(@NotNull LineageScope scope, @NotNull TimeRange range) {
            delegate.remove(scope, range);
        }
    }

    private static final LineageScope EU = new LineageScope("p", "EU");
    private static final java.util.function.Consumer<DataplexView> IGNORE = view -> {
    };

    private static long count(List<String> calls, String call) {
        return calls.stream().filter(call::equals).count();
    }

    private static DataplexLineageServiceImpl parallelService(FakeLineageClient client) {
        return new DataplexLineageServiceImpl(client, Clock.fixed(NOW, ZoneOffset.UTC),
                graph -> new DataformProjectDefaults("p", "d", "EU", null), ParallelCalls.shared());
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    @Test
    void aFullLoadGivesTheRepositoryTablesAndTheirRecordedNeighbours() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("a", "v");

        DataplexView view = service(client).reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(TimeRange.LAST_30_DAYS, view.range());
        assertEquals(3, view.graph().nodes().size());
        assertEquals("table", view.graph().node(ROOT).dataformType());
        assertEquals(DataplexGraphBuilder.TYPE_OUTSIDE, view.graph().node(LineageNode.idOf("p.d.src")).dataformType());
        assertEquals("view", view.graph().node(LineageNode.idOf("p.d.v")).dataformType());
    }

    @Test
    void assertionViewsAreLeftOut() {
        FakeLineageClient client = new FakeLineageClient().link("a", "check");

        DataplexView view = service(client).reload(compiled(), TimeRange.LAST_30_DAYS);

        assertNull(view.graph().node(LineageNode.idOf("p.d.check")));
    }

    @Test
    void linksOlderThanTheRangeAreDropped() {
        FakeLineageClient client = new FakeLineageClient()
                .link("old", "a", daysAgo(20), daysAgo(10)).link("recent", "a", daysAgo(20), daysAgo(2));

        DataplexView view = service(client).reload(compiled(), TimeRange.LAST_7_DAYS);

        assertNull(view.graph().node(LineageNode.idOf("p.d.old")));
        assertNotNull(view.graph().node(LineageNode.idOf("p.d.recent")));
    }






    @Test
    void expandingAddsTheHiddenNeighboursToTheGraphAndToTheCache() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a").link("c", "b").link("d", "c").link("e", "d");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        assertNull(view.graph().node(LineageNode.idOf("p.d.d")));

        DataplexView expanded = service.expand(view, LineageNode.idOf("p.d.c"), Direction.UPSTREAM);

        assertNotNull(expanded.graph().node(LineageNode.idOf("p.d.d")));
        assertEquals(1, expanded.snapshot().hiddenUpstream(fqn("d")));
    }

    @Test
    void freshnessIsTheLatestIncomingLinkAndItsFirstProcess() {
        FakeLineageClient client = new FakeLineageClient()
                .link("src", "a", daysAgo(9), daysAgo(3)).link("other", "a", daysAgo(9), daysAgo(1));
        client.process(client.linkName("other", "a"), new RecordedProcess("processes/1", "nightly load", "BIGQUERY", null));
        client.process(client.linkName("other", "a"), new RecordedProcess("processes/2", "second", "BIGQUERY", null));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        Freshness freshness = service.freshness(view, ROOT);

        assertEquals(new Freshness(daysAgo(1), "nightly load", false), freshness);
    }

    @Test
    void freshnessOfADownstreamTableAsksForItsOwnIncomingLinks() {
        FakeLineageClient client = new FakeLineageClient()
                .link("a", "v", daysAgo(9), daysAgo(5)).link("elsewhere", "v", daysAgo(9), daysAgo(1));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(daysAgo(1), service.freshness(view, LineageNode.idOf("p.d.v")).lastWrite());
    }

    @Test
    void freshnessIsAskedOnce() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        service.freshness(view, ROOT);
        client.calls.clear();

        service.freshness(view, ROOT);

        assertTrue(client.calls.isEmpty());
    }

    @Test
    void freshnessOfATableWithoutIncomingLinkHasNoDate() {
        FakeLineageClient client = new FakeLineageClient().link("a", "v");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(new Freshness(null, null, false), service.freshness(view, ROOT));
    }

    @Test
    void freshnessOfAViewIsMarkedAsView() {
        FakeLineageClient client = new FakeLineageClient().link("a", "v", daysAgo(4), daysAgo(4));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        Freshness freshness = service.freshness(view, LineageNode.idOf("p.d.v"));

        assertTrue(freshness.view());
        assertEquals(daysAgo(4), freshness.lastWrite());
    }

    @Test
    void linkDetailsListProcessesAndTheirRunsNewestFirst() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a", daysAgo(9), daysAgo(1));
        RecordedProcess first = new RecordedProcess("processes/1", "nightly load", "BIGQUERY", "job_1");
        RecordedProcess second = new RecordedProcess("processes/2", "backfill", "BIGQUERY", null);
        client.process(client.linkName("src", "a"), first).process(client.linkName("src", "a"), second);
        RecordedRun older = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(3), daysAgo(3));
        RecordedRun newest = new RecordedRun("processes/1/runs/2", RecordedRun.State.FAILED, daysAgo(1), daysAgo(1));
        RecordedRun middle = new RecordedRun("processes/2/runs/1", RecordedRun.State.COMPLETED, daysAgo(2), daysAgo(2));
        client.runs("processes/1", List.of(newest, older)).runs("processes/2", List.of(middle));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        LinkDetails details = service.linkDetails(view, LineageNode.idOf("p.d.src"), ROOT);

        assertEquals(daysAgo(9), details.firstSeen());
        assertEquals(daysAgo(1), details.lastSeen());
        assertEquals(List.of(first, second), details.processes());
        assertEquals(List.of(newest, middle, older), details.runs());
    }

    @Test
    void theDetailsOfALinkThatIsNotLoadedAreEmpty() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        LinkDetails details = service.linkDetails(view, ROOT, LineageNode.idOf("p.d.src"));

        assertTrue(details.processes().isEmpty());
        assertTrue(details.runs().isEmpty());
    }

    @Test
    void theRunsWritingATableAreThoseOfItsIncomingLinksNewestFirst() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("other", "a").link("a", "v");
        client.process(client.linkName("src", "a"), new RecordedProcess("processes/1", "one", "BIGQUERY", null));
        client.process(client.linkName("other", "a"), new RecordedProcess("processes/2", "two", "BIGQUERY", null));
        client.process(client.linkName("a", "v"), new RecordedProcess("processes/3", "reader", "BIGQUERY", null));
        RecordedRun one = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(3), daysAgo(3));
        RecordedRun two = new RecordedRun("processes/2/runs/1", RecordedRun.State.COMPLETED, daysAgo(1), daysAgo(1));
        RecordedRun reader = new RecordedRun("processes/3/runs/1", RecordedRun.State.COMPLETED, daysAgo(0), daysAgo(0));
        client.runs("processes/1", List.of(one)).runs("processes/2", List.of(two)).runs("processes/3", List.of(reader));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of(two, one), service.runsWriting(view, ROOT).stream().map(ProcessRun::run).toList());
    }

    @Test
    void aRunWritingATableNamesItsProcessAndEverythingTheProcessRead() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("other", "a");
        RecordedProcess workflow = new RecordedProcess("processes/1", "workflow", "BIGQUERY", "job_1");
        client.process(client.linkName("src", "a"), workflow).process(client.linkName("other", "a"), workflow);
        RecordedRun run = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(1), daysAgo(1));
        client.runs("processes/1", List.of(run));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of(new ProcessRun(run, workflow, List.of(fqn("src"), fqn("other")), fqn("a"))),
                service.runsWriting(view, ROOT));
    }

    @Test
    void theRunsOfALinkNameOnlyTheSourceOfThatLink() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("other", "a");
        RecordedProcess workflow = new RecordedProcess("processes/1", "workflow", "BIGQUERY", null);
        client.process(client.linkName("src", "a"), workflow).process(client.linkName("other", "a"), workflow);
        RecordedRun older = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(2), daysAgo(2));
        RecordedRun newer = new RecordedRun("processes/1/runs/2", RecordedRun.State.FAILED, daysAgo(1), daysAgo(1));
        client.runs("processes/1", List.of(older, newer));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of(new ProcessRun(newer, workflow, List.of(fqn("src")), fqn("a")),
                        new ProcessRun(older, workflow, List.of(fqn("src")), fqn("a"))),
                service.runsOfLink(view, LineageNode.idOf("p.d.src"), ROOT));
    }

    @Test
    void aLinkThatIsNotLoadedHasNoRun() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertTrue(service.runsOfLink(view, ROOT, LineageNode.idOf("p.d.src")).isEmpty());
    }

    @Test
    void theScopeIsTheWorkspaceProjectAndTheLowerCasedDefaultLocation() {
        FakeLineageClient client = new FakeLineageClient();

        service(client, new DataformProjectDefaults("my-project", "d", "EU", null))
                .reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals("projects/my-project/locations/eu", client.scopes.getFirst().parent());
    }

    @Test
    void withoutADefaultLocationTheScopeIsTheUsMultiRegion() {
        FakeLineageClient client = new FakeLineageClient();

        service(client, new DataformProjectDefaults("my-project", "d", null, null))
                .reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals("projects/my-project/locations/us", client.scopes.getFirst().parent());
    }

    @Test
    void aMissingProjectIdIsNotSignedInOrConfigured() {
        DataplexLineageServiceImpl service = service(new FakeLineageClient(), new DataformProjectDefaults(null, "d", "EU", null));

        DataplexLineageException failure = assertThrows(DataplexLineageException.class,
                () -> service.reload(compiled(), TimeRange.LAST_30_DAYS));

        assertEquals(DataplexLineageException.Kind.OTHER, failure.kind());
        assertTrue(failure.getMessage().contains("workflow_settings.yaml"), failure.getMessage());
    }

    @Test
    void theRangesAreLabelledAsInTheToolbar() {
        assertEquals("Last 30 days", TimeRange.LAST_30_DAYS.label());
        assertEquals("Last 7 days", TimeRange.LAST_7_DAYS.label());
        assertEquals("Last 24 hours", TimeRange.LAST_24_HOURS.label());
        assertEquals(Duration.ofHours(24), TimeRange.LAST_24_HOURS.length());
    }

    @Test
    void theProcessesOfATableAreThoseOfItsLinksLatestFirst() {
        FakeLineageClient client = new FakeLineageClient()
                .link("src", "a", daysAgo(9), daysAgo(2))
                .link("a", "v", daysAgo(5), daysAgo(1));
        client.process(client.linkName("src", "a"), new RecordedProcess("processes/1", "nightly load", "BIGQUERY", null));
        client.process(client.linkName("a", "v"), new RecordedProcess("processes/2", "BigQuery job", "BIGQUERY", null));
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of(new ProcessUse("BigQuery job", false, daysAgo(1)), new ProcessUse("nightly load", true, daysAgo(2))),
                service.processesOf(view, ROOT));
    }

    @Test
    void theSameProcessOnSeveralLinksIsListedOnceWithItsLatestDate() {
        FakeLineageClient client = new FakeLineageClient()
                .link("src", "a", daysAgo(9), daysAgo(4))
                .link("other", "a", daysAgo(9), daysAgo(2));
        RecordedProcess load = new RecordedProcess("processes/1", "nightly load", "BIGQUERY", null);
        client.process(client.linkName("src", "a"), load).process(client.linkName("other", "a"), load);
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of(new ProcessUse("nightly load", true, daysAgo(2))), service.processesOf(view, ROOT));
    }



    private static List<String> tables(List<TraceEntry> entries) {
        return entries.stream().map(e -> EntityNames.shortNameOf(e.fqn()) + "@" + e.level()).toList();
    }

    @Test
    void traceStopsAtFiveLevels() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a").link("c", "b").link("d", "c").link("e", "d")
                .link("f", "e").link("g", "f").link("h", "g");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of("b@1", "c@2", "d@3", "e@4", "f@5"), tables(service.trace(view, ROOT, Direction.UPSTREAM)));
    }

    @Test
    void traceIncludesEntitiesThatAreNotOnTheGraph() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a").link("c", "b").link("d", "c");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        assertFalse(view.snapshot().entities().contains(fqn("d")));

        List<TraceEntry> trace = service.trace(view, ROOT, Direction.UPSTREAM);

        assertTrue(trace.contains(new TraceEntry(fqn("d"), 3)));
    }

    @Test
    void traceFollowsTheAskedDirectionOnly() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("a", "v").link("v", "report");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of("v@1", "report@2"), tables(service.trace(view, ROOT, Direction.DOWNSTREAM)));
        assertEquals(List.of("src@1"), tables(service.trace(view, ROOT, Direction.UPSTREAM)));
    }

    @Test
    void anEntityReachedTwiceKeepsItsShortestLevelAndTheRootIsLeftOut() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a").link("c", "a").link("c", "b").link("a", "c");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of("b@1", "c@1"), tables(service.trace(view, ROOT, Direction.UPSTREAM)));
    }

    @Test
    void traceLeavesTheAssertionViewsOut() {
        FakeLineageClient client = new FakeLineageClient().link("a", "check").link("a", "v");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of("v@1"), tables(service.trace(view, ROOT, Direction.DOWNSTREAM)));
    }

    @Test
    void traceStopsAtTheEntityCap() {
        FakeLineageClient client = new FakeLineageClient();
        for (int i = 0; i < DataplexGraphLoader.MAX_ENTITIES + 20; i++) client.link("s" + i, "a");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(DataplexGraphLoader.MAX_ENTITIES, service.trace(view, ROOT, Direction.UPSTREAM).size());
    }

    @Test
    void aTraceIsReadOnce() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        List<TraceEntry> first = service.trace(view, ROOT, Direction.UPSTREAM);
        int calls = client.calls.size();

        assertSame(first, service.trace(view, ROOT, Direction.UPSTREAM));
        assertEquals(calls, client.calls.size());
    }

    @Test
    void aTraceReadsBothDirectionsInOneSearchAndKeepsTheOtherForLater() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("a", "v");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.calls.clear();

        List<TraceEntry> upstream = service.trace(view, ROOT, Direction.UPSTREAM);
        List<String> afterFirst = List.copyOf(client.calls);
        List<TraceEntry> downstream = service.trace(view, ROOT, Direction.DOWNSTREAM);

        assertEquals(List.of("src@1"), tables(upstream));
        assertEquals(List.of("v@1"), tables(downstream));
        assertEquals(List.of("STREAM UPSTREAM [" + fqn("a") + "] depth=5", "STREAM DOWNSTREAM [" + fqn("a") + "] depth=5"),
                afterFirst);
        assertEquals(afterFirst, client.calls);
    }

    @Test
    void theColumnLineageIsWalkedWithinTheLoadedTablesOfTheView() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("a", "v")
                .columnLink("src", "x", "a", "y").columnLink("a", "y", "v", "z").columnLink("v", "z", "far", "w");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        DataplexColumnLineage.Result result = service.columnLineage(view,
                List.of(new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.d.a", "y")));

        assertEquals(java.util.Set.of("p.d.src#x"), result.graph().upstream("p.d.a#y"));
        assertEquals(java.util.Set.of("p.d.v#z"), result.graph().downstream("p.d.a#y"));
        assertEquals("projects/p/locations/eu", client.columnScopes.getFirst().parent());
    }

    @Test
    void theColumnLineageOfTheSameRootsIsReadOnce() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").columnLink("src", "x", "a", "y");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        List<io.github.rejeb.dataform.language.columns.model.ColumnRef> roots =
                List.of(new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.d.a", "y"));
        DataplexColumnLineage.Result first = service.columnLineage(view, roots);
        client.calls.clear();

        assertSame(first, service.columnLineage(view, roots));
        assertTrue(client.calls.isEmpty());

        service.columnLineage(view, List.of(new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.d.src", "x")));
        assertFalse(client.calls.isEmpty());
    }

    @Test
    void theTraceIsOneStreamingSearchOfBothDirections() {
        FakeLineageClient client = new FakeLineageClient().link("a", "b").link("b", "c").link("c", "d");
        DataplexLineageServiceImpl service = service(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.calls.clear();

        List<TraceEntry> trace = service.trace(view, ROOT, Direction.DOWNSTREAM);

        assertEquals(List.of("b@1", "c@2", "d@3"), tables(trace));
        assertEquals(List.of("STREAM UPSTREAM [" + fqn("a") + "] depth=" + DataplexLineageServiceImpl.TRACE_DEPTH,
                "STREAM DOWNSTREAM [" + fqn("a") + "] depth=" + DataplexLineageServiceImpl.TRACE_DEPTH), client.calls);
    }

    @Test
    void theRunsOfSeveralProcessesAreReadTogetherAndKeptNewestFirst() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a", daysAgo(9), daysAgo(1));
        RecordedProcess first = new RecordedProcess("processes/1", "nightly load", "BIGQUERY", "job_1");
        RecordedProcess second = new RecordedProcess("processes/2", "backfill", "BIGQUERY", null);
        client.process(client.linkName("src", "a"), first).process(client.linkName("src", "a"), second);
        RecordedRun older = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(3), daysAgo(3));
        RecordedRun middle = new RecordedRun("processes/2/runs/1", RecordedRun.State.COMPLETED, daysAgo(2), daysAgo(2));
        client.runs("processes/1", List.of(older)).runs("processes/2", List.of(middle))
                .delay("processes/1", 60).delay("processes/2", 60);
        DataplexLineageServiceImpl service = parallelService(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.highestConcurrency.set(0);

        LinkDetails details = service.linkDetails(view, LineageNode.idOf("p.d.src"), ROOT);

        assertEquals(List.of(middle, older), details.runs());
        assertTrue(client.highestConcurrency.get() >= 2, "highest " + client.highestConcurrency.get());
    }

    @Test
    void theRunsOfAProcessAreReadWhileItsDetailsAre() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a", daysAgo(9), daysAgo(1));
        RecordedProcess process = new RecordedProcess("processes/1", "nightly load", "BIGQUERY", "job_1");
        RecordedRun run = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(2), daysAgo(2));
        client.process(client.linkName("src", "a"), process).runs("processes/1", List.of(run)).delay("processes/1", 100);
        DataplexLineageServiceImpl service = parallelService(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.highestConcurrency.set(0);

        List<ProcessRun> runs = service.runsWriting(view, ROOT);

        assertEquals(List.of(new ProcessRun(run, process, List.of(fqn("src")), fqn("a"))), runs);
        assertTrue(client.highestConcurrency.get() >= 2, "highest " + client.highestConcurrency.get());
    }

    @Test
    void theRunsOfALinkAreReadWhileItsProcessDetailsAre() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a", daysAgo(9), daysAgo(1));
        RecordedProcess process = new RecordedProcess("processes/1", "nightly load", "BIGQUERY", "job_1");
        RecordedRun run = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(2), daysAgo(2));
        client.process(client.linkName("src", "a"), process).runs("processes/1", List.of(run)).delay("processes/1", 100);
        DataplexLineageServiceImpl service = parallelService(client);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.highestConcurrency.set(0);

        LinkDetails details = service.linkDetails(view, LineageNode.idOf("p.d.src"), ROOT);

        assertEquals(List.of(process), details.processes());
        assertEquals(List.of(run), details.runs());
        assertTrue(client.highestConcurrency.get() >= 2, "highest " + client.highestConcurrency.get());
    }

    @Test
    void withoutAnEntryThereIsNoCachedViewAndAReloadIsNeeded() {
        FakeLineageClient client = new FakeLineageClient().newest("processes/1");
        DataplexLineageServiceImpl service = service(client, new CountingCache());

        assertNull(service.cachedView(compiled(), TimeRange.LAST_30_DAYS));
        assertTrue(service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
        assertTrue(client.calls.isEmpty());
    }

    @Test
    void theRootsAreTheRepositoryTablesWithoutAssertions() {
        FakeLineageClient client = new FakeLineageClient();
        DataplexLineageServiceImpl service = service(client, new CountingCache());

        service.reload(compiled(), TimeRange.LAST_30_DAYS);

        String roots = "[" + fqn("a") + ", " + fqn("v") + "]";
        assertEquals(List.of("NEWEST", "STREAM UPSTREAM " + roots + " depth=3", "STREAM DOWNSTREAM " + roots + " depth=3"),
                client.calls);
    }

    @Test
    void aReloadPutsTheEntryWithTheMarkerReadBeforeTheLoad() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").newest("processes/1");
        client.onStream = () -> client.newest("processes/2");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);

        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertTrue(view.snapshot().entities().contains(fqn("src")));
        assertEquals("processes/1", cache.get(EU, TimeRange.LAST_30_DAYS).marker());
        assertTrue(service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
    }

    @Test
    void anUnchangedMarkerNeedsNoReloadAndCostsOneCall() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").newest("processes/1");
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.calls.clear();

        assertFalse(service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
        assertEquals(List.of("NEWEST"), client.calls);
    }

    @Test
    void changedRepositoryTargetsNeedAReload() {
        FakeLineageClient client = new FakeLineageClient().newest("processes/1");
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertTrue(service.needsReload(compiled(table("a", "table"), table("v", "view"), table("w", "table")),
                TimeRange.LAST_30_DAYS));
    }

    @Test
    void theCachedViewIsDecodedAgainAfterARelease() {
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(new FakeLineageClient().link("src", "a"), cache);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView loaded = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        int before = cache.gets.get();

        assertSame(loaded, service.cachedView(compiled(), TimeRange.LAST_30_DAYS));
        assertEquals(before, cache.gets.get());

        service.release(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView decoded = service.cachedView(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(before + 1, cache.gets.get());
        assertEquals(loaded.snapshot(), decoded.snapshot());
    }

    @Test
    void releaseDropsTheAnswersOfThatScopeAndRange() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a");
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        service.freshness(view, ROOT);
        service.freshness(view, ROOT);
        assertEquals(1, count(client.calls, "UPSTREAM " + fqn("a")));

        service.release(EU, TimeRange.LAST_30_DAYS, IGNORE);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        service.freshness(view, ROOT);

        assertEquals(2, count(client.calls, "UPSTREAM " + fqn("a")));
    }

    @Test
    void aSecondShownViewKeepsTheAnswersWhenTheFirstIsReleased() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a");
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        service.freshness(view, ROOT);

        service.release(EU, TimeRange.LAST_30_DAYS, IGNORE);
        service.freshness(view, ROOT);

        assertEquals(1, count(client.calls, "UPSTREAM " + fqn("a")));
    }

    @Test
    void aReloadFinishingWithNoShownViewOnlyFillsTheCache() {
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(new FakeLineageClient().link("src", "a"), cache);

        service.reload(compiled(), TimeRange.LAST_30_DAYS);
        int before = cache.gets.get();

        assertNotNull(service.cachedView(compiled(), TimeRange.LAST_30_DAYS));
        assertEquals(before + 1, cache.gets.get());
    }

    @Test
    void aNewEntryIsGivenToEveryViewShowingItsScopeAndRange() {
        FakeLineageClient client = new FakeLineageClient().link("x1", "a").link("x2", "x1").link("x3", "x2");
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        List<DataplexView> project = new java.util.ArrayList<>();
        List<DataplexView> file = new java.util.ArrayList<>();
        List<DataplexView> week = new java.util.ArrayList<>();
        java.util.function.Consumer<DataplexView> projectView = project::add;
        java.util.function.Consumer<DataplexView> fileView = file::add;
        java.util.function.Consumer<DataplexView> weekView = week::add;
        service.retain(EU, TimeRange.LAST_30_DAYS, projectView);
        service.retain(EU, TimeRange.LAST_30_DAYS, fileView);
        service.retain(EU, TimeRange.LAST_7_DAYS, weekView);

        DataplexView first = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        service.expand(first, EntityNames.nodeIdOf(fqn("x2")), Direction.UPSTREAM);
        service.release(EU, TimeRange.LAST_30_DAYS, fileView);
        DataplexView second = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(List.of(first, second), project);
        assertEquals(List.of(first), file);
        assertTrue(week.isEmpty());
    }

    @Test
    void anExpansionFinishingAfterANewerEntryDoesNotOverwriteIt() {
        FakeLineageClient client = new FakeLineageClient()
                .link("x1", "a").link("x2", "x1").link("x3", "x2").link("x4", "x3");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView older = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.link("src", "a");
        DataplexView newer = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        service.expand(older, EntityNames.nodeIdOf(fqn("x2")), Direction.UPSTREAM);

        LineageCacheEntry entry = cache.get(EU, TimeRange.LAST_30_DAYS);
        assertTrue(entry.snapshot().entities().contains(fqn("src")));
        assertEquals(List.of(new Expansion(fqn("x2"), Direction.UPSTREAM)), entry.expansions());
        assertSame(newer, service.cachedView(compiled(), TimeRange.LAST_30_DAYS));
        assertTrue(service.reload(compiled(), TimeRange.LAST_30_DAYS).snapshot().entities().contains(fqn("x3")));
    }

    @Test
    void anExpansionMadeWhileAReloadRunsIsKeptByIt() throws Exception {
        FakeLineageClient client = new FakeLineageClient()
                .link("x1", "a").link("x2", "x1").link("x3", "x2").link("x4", "x3")
                .link("a", "d1").link("d1", "d2").link("d2", "d3").link("d3", "d4");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView loaded = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        DataplexView expanded = service.expand(loaded, EntityNames.nodeIdOf(fqn("x2")), Direction.UPSTREAM);
        client.delay(fqn("x3"), 400);
        Thread other = new Thread(() -> service.reload(compiled(), TimeRange.LAST_30_DAYS));
        other.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            synchronized (client.calls) {
                if (count(client.calls, "UPSTREAM " + fqn("x3")) >= 2) break;
            }
            Thread.onSpinWait();
        }

        service.expand(expanded, EntityNames.nodeIdOf(fqn("d2")), Direction.DOWNSTREAM);
        other.join(5_000);

        assertEquals(List.of(new Expansion(fqn("x2"), Direction.UPSTREAM), new Expansion(fqn("d2"), Direction.DOWNSTREAM)),
                cache.get(EU, TimeRange.LAST_30_DAYS).expansions());
        assertTrue(service.reload(compiled(), TimeRange.LAST_30_DAYS).snapshot().entities().contains(fqn("d3")));
    }

    @Test
    void expansionsAreSavedAndReappliedByAReload() {
        FakeLineageClient client = new FakeLineageClient()
                .link("x1", "a").link("x2", "x1").link("x3", "x2").link("x4", "x3");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        assertFalse(view.snapshot().entities().contains(fqn("x3")));

        DataplexView expanded = service.expand(view, EntityNames.nodeIdOf(fqn("x2")), Direction.UPSTREAM);

        assertTrue(expanded.snapshot().entities().contains(fqn("x3")));
        assertEquals(List.of(new Expansion(fqn("x2"), Direction.UPSTREAM)),
                cache.get(EU, TimeRange.LAST_30_DAYS).expansions());
        assertTrue(cache.get(EU, TimeRange.LAST_30_DAYS).snapshot().entities().contains(fqn("x3")));

        DataplexView reloaded = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertTrue(reloaded.snapshot().entities().contains(fqn("x3")));
    }

    @Test
    void twoConcurrentReloadsShareOneLoad() throws Exception {
        FakeLineageClient client = new FakeLineageClient().link("src", "a");
        CountDownLatch entered = new CountDownLatch(1);
        client.onStream = () -> {
            entered.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        DataplexView[] first = new DataplexView[1];
        Thread other = new Thread(() -> first[0] = service.reload(compiled(), TimeRange.LAST_30_DAYS));
        other.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        DataplexView second = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        other.join(5_000);

        assertSame(first[0], second);
        assertEquals(1, client.calls.stream().filter(call -> call.startsWith("STREAM UPSTREAM")).count());
    }

    @Test
    void aReloadForOtherTablesWhileAnotherRunsReadsThoseTables() throws Exception {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").link("a", "w");
        CountDownLatch entered = new CountDownLatch(1);
        client.onStream = () -> {
            entered.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        Thread other = new Thread(() -> service.reload(compiled(), TimeRange.LAST_30_DAYS));
        other.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        CompiledGraph newer = compiled(table("a", "table"), table("v", "view"), table("w", "table"));
        DataplexView view = service.reload(newer, TimeRange.LAST_30_DAYS);
        other.join(5_000);

        assertTrue(view.snapshot().rootFqns().contains(fqn("w")));
        assertEquals("table", view.graph().node(LineageNode.idOf("p.d.w")).dataformType());
        assertFalse(service.needsReload(newer, TimeRange.LAST_30_DAYS));
    }

    @Test
    void aReloadRightAfterACheckKeepsTheMarkerTheCheckRead() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").newest("processes/1");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);
        service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.newest("processes/2");
        client.calls.clear();

        assertTrue(service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
        client.onStream = () -> client.newest("processes/3");
        service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(1, count(client.calls, "NEWEST"));
        assertEquals("processes/2", cache.get(EU, TimeRange.LAST_30_DAYS).marker());
        assertTrue(service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
    }

    @Test
    void aMarkerReadByACheckIsUsedByOneReloadOnly() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a").newest("processes/1");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);
        service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.newest("processes/2");
        assertTrue(service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
        service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.newest("processes/3");
        client.calls.clear();

        service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertEquals(1, count(client.calls, "NEWEST"));
        assertEquals("processes/3", cache.get(EU, TimeRange.LAST_30_DAYS).marker());
    }

    @Test
    void twoConcurrentChecksShareOneCall() throws Exception {
        FakeLineageClient client = new FakeLineageClient().newest("processes/1");
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        service.reload(compiled(), TimeRange.LAST_30_DAYS);
        client.calls.clear();
        CountDownLatch entered = new CountDownLatch(1);
        client.onNewest = () -> {
            entered.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        boolean[] first = new boolean[1];
        Thread other = new Thread(() -> first[0] = service.needsReload(compiled(), TimeRange.LAST_30_DAYS));
        other.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        boolean second = service.needsReload(compiled(), TimeRange.LAST_30_DAYS);
        other.join(5_000);

        assertFalse(first[0]);
        assertFalse(second);
        assertEquals(List.of("NEWEST"), client.calls);
    }

    @Test
    void linkDetailsReadTheNamedLinkOnDemand() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a", daysAgo(9), daysAgo(1));
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        assertNull(view.snapshot().links().getFirst().lastSeen());
        client.calls.clear();

        LinkDetails details = service.linkDetails(view, LineageNode.idOf("p.d.src"), ROOT);

        assertEquals(daysAgo(9), details.firstSeen());
        assertEquals(daysAgo(1), details.lastSeen());
        assertTrue(client.calls.contains("UPSTREAM " + fqn("a")));
    }

    @Test
    void theRunsOfALinkAreReadFromTheNamedLinkOnDemand() {
        FakeLineageClient client = new FakeLineageClient().link("src", "a", daysAgo(9), daysAgo(1));
        RecordedProcess process = new RecordedProcess("processes/1", "nightly load", "BIGQUERY", "job_1");
        client.process(client.linkName("src", "a"), process);
        RecordedRun run = new RecordedRun("processes/1/runs/1", RecordedRun.State.COMPLETED, daysAgo(1), daysAgo(1));
        client.runs("processes/1", List.of(run));
        DataplexLineageServiceImpl service = service(client, new CountingCache());
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);

        List<ProcessRun> runs = service.runsOfLink(view, LineageNode.idOf("p.d.src"), ROOT);

        assertEquals(List.of(run), runs.stream().map(ProcessRun::run).toList());
        assertEquals(List.of("nightly load"), service.processesOf(view, ROOT).stream().map(ProcessUse::displayName).toList());
    }

    @Test
    void rangesHaveTheirOwnEntries() {
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(new FakeLineageClient().link("src", "a"), cache);

        service.reload(compiled(), TimeRange.LAST_30_DAYS);

        assertNull(service.cachedView(compiled(), TimeRange.LAST_7_DAYS));
        service.reload(compiled(), TimeRange.LAST_7_DAYS);
        assertNotNull(cache.get(EU, TimeRange.LAST_30_DAYS));
        assertNotNull(cache.get(EU, TimeRange.LAST_7_DAYS));
    }

    @Test
    void includeAddsATableNotOnTheGraphAndKeepsItInTheEntry() {
        FakeLineageClient client = new FakeLineageClient().link("far", "near").link("near", "x").link("x", "a");
        CountingCache cache = new CountingCache();
        DataplexLineageServiceImpl service = service(client, cache);
        service.retain(EU, TimeRange.LAST_30_DAYS, IGNORE);
        DataplexView view = service.reload(compiled(), TimeRange.LAST_30_DAYS);
        assertNull(view.fqnOf(EntityNames.nodeIdOf(fqn("far"))));

        DataplexView larger = service.include(view, EntityNames.nodeIdOf(fqn("far")));

        assertEquals(fqn("far"), larger.fqnOf(EntityNames.nodeIdOf(fqn("far"))));
        assertTrue(cache.get(EU, TimeRange.LAST_30_DAYS).snapshot().entities().contains(fqn("far")));
    }
}
