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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.rejeb.dataform.language.lineage.dataplex.FakeLineageClient.fqn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataplexGraphLoaderTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final LineageScope SCOPE = new LineageScope("p", "eu");

    private static DataplexGraphLoader loader(FakeLineageClient client) {
        return new DataplexGraphLoader(client, SCOPE, Clock.fixed(NOW, ZoneOffset.UTC), ParallelCalls.direct());
    }

    private static DataplexGraphLoader parallelLoader(FakeLineageClient client) {
        return new DataplexGraphLoader(client, SCOPE, Clock.fixed(NOW, ZoneOffset.UTC), ParallelCalls.shared());
    }

    private static Set<String> fqns(String... tables) {
        Set<String> names = new HashSet<>();
        for (String table : tables) names.add(fqn(table));
        return names;
    }

    private static Set<String> ends(List<RecordedLink> links) {
        Set<String> ends = new HashSet<>();
        for (RecordedLink link : links) ends.add(link.sourceFqn() + ">" + link.targetFqn());
        return ends;
    }






    @Test
    void aSelfLinkAddsNoEdgeAndDoesNotLoop() {
        DataplexSnapshot snapshot = loader(new FakeLineageClient().link("a", "a").link("b", "a"))
                .loadAll(List.of(fqn("a")), null);

        assertEquals(fqns("a", "b"), snapshot.entities());
        assertEquals(Set.of(fqn("b") + ">" + fqn("a")), ends(snapshot.links()));
        assertTrue(snapshot.pendingLinks().isEmpty());
    }


    @Test
    void aCycleTerminates() {
        DataplexSnapshot snapshot = loader(new FakeLineageClient().link("a", "b").link("b", "a"))
                .loadAll(List.of(fqn("a")), null);

        assertEquals(fqns("a", "b"), snapshot.entities());
        assertEquals(2, snapshot.links().size());
    }





    @Test
    void expandingAddsThePendingNeighboursOfThatSideOnlyAndCountsTheirs() {
        FakeLineageClient client = new FakeLineageClient()
                .link("b", "a").link("c", "b").link("d", "c").link("x", "d").link("a", "e").link("e", "f").link("f", "g");
        DataplexGraphLoader loader = loader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a")), null);
        assertEquals(1, loaded.hiddenUpstream(fqn("c")));
        assertEquals(1, loaded.hiddenDownstream(fqn("f")));
        client.calls.clear();

        DataplexSnapshot expanded = loader.expand(loaded, fqn("c"), Direction.UPSTREAM);

        assertEquals(fqns("a", "b", "c", "d", "e", "f"), expanded.entities());
        assertEquals(0, expanded.hiddenUpstream(fqn("c")));
        assertEquals(1, expanded.hiddenUpstream(fqn("d")));
        assertEquals(1, expanded.hiddenDownstream(fqn("f")));
        assertEquals(List.of(Direction.UPSTREAM + " " + fqn("d")), client.calls);
        assertEquals(Set.of(fqn("a")), expanded.rootFqns());
    }


    @Test
    void expandingAnEntityWithNothingHiddenChangesNothing() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a");
        DataplexGraphLoader loader = loader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a")), null);
        client.calls.clear();

        DataplexSnapshot expanded = loader.expand(loaded, fqn("b"), Direction.UPSTREAM);

        assertEquals(loaded, expanded);
        assertTrue(client.calls.isEmpty());
    }


    @Test
    void expandingAnEntityTheCapLeftUnqueriedQueriesItFirst() {
        FakeLineageClient client = new FakeLineageClient().link("s0", "a").link("deep", "s0");
        DataplexSnapshot loaded = new DataplexSnapshot(Set.of(fqn("a")), new java.util.LinkedHashSet<>(List.of(fqn("a"), fqn("s0"))),
                List.of(RecordedLink.streamed(fqn("s0"), fqn("a"))), List.of(), Set.of(fqn("s0")), Set.of(), Set.of(),
                Set.of(fqn("a"), fqn("s0")), true, NOW);
        DataplexGraphLoader loader = loader(client);

        DataplexSnapshot expanded = loader.expand(loaded, fqn("s0"), Direction.UPSTREAM);

        assertTrue(expanded.entities().contains(fqn("deep")));
        assertEquals(0, expanded.hiddenUpstream(fqn("s0")));
        assertEquals(List.of(Direction.UPSTREAM + " " + fqn("s0"), Direction.UPSTREAM + " " + fqn("deep")), client.calls);
    }


    @Test
    void anEntityWithNoLinkGivesASnapshotOfItselfOnly() {
        DataplexSnapshot snapshot = loader(new FakeLineageClient()).loadAll(List.of(fqn("a")), null);

        assertEquals(fqns("a"), snapshot.entities());
        assertTrue(snapshot.links().isEmpty());
        assertTrue(snapshot.pendingLinks().isEmpty());
        assertEquals(0, snapshot.hiddenUpstream(fqn("a")));
        assertEquals(0, snapshot.hiddenDownstream(fqn("a")));
    }




    @Test
    void expandingUpstreamReadsTheUpstreamSideOfWhatItAdds() {
        FakeLineageClient client = new FakeLineageClient().link("b", "a").link("c", "b").link("d", "c").link("x", "d");
        DataplexGraphLoader loader = loader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a")), null);
        assertEquals(Set.of(fqn("a"), fqn("b"), fqn("c")), loaded.upstreamRead());

        DataplexSnapshot expanded = loader.expand(loaded, fqn("c"), Direction.UPSTREAM);

        assertEquals(Set.of(fqn("a"), fqn("b"), fqn("c"), fqn("d")), expanded.upstreamRead());
    }



    @Test
    void expandAsksTheAddedNeighboursTogether() {
        FakeLineageClient client = new FakeLineageClient()
                .link("b", "a").link("w", "b").link("c", "w").link("d", "w").link("e", "w")
                .delay(fqn("c"), 60).delay(fqn("d"), 60).delay(fqn("e"), 60);
        DataplexGraphLoader loader = parallelLoader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a")), null);
        client.highestConcurrency.set(0);

        loader.expand(loaded, fqn("w"), Direction.UPSTREAM);

        assertTrue(client.highestConcurrency.get() >= 2, "highest " + client.highestConcurrency.get());
    }


    @Test
    void expandAddsTheNeighboursInLinkOrderWhateverTheAnswerOrder() {
        FakeLineageClient client = new FakeLineageClient()
                .link("b", "a").link("w", "b").link("c", "w").link("d", "w").link("e", "w")
                .link("x", "c").link("y", "d").link("z", "e")
                .delay(fqn("c"), 90).delay(fqn("d"), 45);
        DataplexGraphLoader loader = parallelLoader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a")), null);

        DataplexSnapshot expanded = loader.expand(loaded, fqn("w"), Direction.UPSTREAM);

        assertEquals(List.of(fqn("a"), fqn("b"), fqn("w"), fqn("c"), fqn("d"), fqn("e")), List.copyOf(expanded.entities()));
        assertEquals(List.of(fqn("x") + ">" + fqn("c"), fqn("y") + ">" + fqn("d"), fqn("z") + ">" + fqn("e")),
                expanded.pendingLinks().stream().map(l -> l.sourceFqn() + ">" + l.targetFqn()).toList());
    }


    private static FakeLineageClient twoRoots() {
        return new FakeLineageClient()
                .link("x", "a").link("y", "x").link("z", "y")
                .link("b", "o1").link("o1", "o2").link("o2", "o3")
                .link("a", "b");
    }

    private static List<String> pairs(List<RecordedLink> links) {
        return links.stream().map(l -> l.sourceFqn() + ">" + l.targetFqn()).toList();
    }

    @Test
    void everyRootIsAnEntityEvenWithoutLinks() {
        DataplexSnapshot snapshot = loader(twoRoots()).loadAll(List.of(fqn("a"), fqn("b"), fqn("lonely")), null);

        assertTrue(snapshot.entities().contains(fqn("lonely")));
        assertEquals(List.of(fqn("a"), fqn("b"), fqn("lonely")), List.copyOf(snapshot.rootFqns()));
    }

    @Test
    void outsideEntitiesUpToTwoLevelsAreLoadedAndTheThirdLevelIsPending() {
        DataplexSnapshot snapshot = loader(twoRoots()).loadAll(List.of(fqn("a"), fqn("b")), null);

        assertEquals(fqns("a", "b", "x", "y", "o1", "o2"), snapshot.entities());
        assertEquals(new HashSet<>(List.of(fqn("z") + ">" + fqn("y"), fqn("o2") + ">" + fqn("o3"))),
                new HashSet<>(pairs(snapshot.pendingLinks())));
        assertEquals(1, snapshot.hiddenUpstream(fqn("y")));
        assertEquals(1, snapshot.hiddenDownstream(fqn("o2")));
        assertEquals(0, snapshot.hiddenUpstream(fqn("x")));
        assertFalse(snapshot.truncated());
    }

    @Test
    void aLinkBetweenTwoRootsIsKeptOnce() {
        DataplexSnapshot snapshot = loader(twoRoots()).loadAll(List.of(fqn("a"), fqn("b")), null);

        assertEquals(1, pairs(snapshot.links()).stream().filter((fqn("a") + ">" + fqn("b"))::equals).count());
        assertEquals(5, snapshot.links().size());
    }

    @Test
    void streamedLinksHaveNoNameAndNoTimes() {
        DataplexSnapshot snapshot = loader(twoRoots()).loadAll(List.of(fqn("a"), fqn("b")), null);

        RecordedLink link = snapshot.links().getFirst();
        assertEquals("", link.name());
        assertEquals(null, link.firstSeen());
        assertEquals(null, link.lastSeen());
    }

    @Test
    void outsideEntitiesAreCappedAndRootsAreNot() {
        FakeLineageClient client = new FakeLineageClient();
        for (int i = 0; i < DataplexGraphLoader.MAX_ENTITIES + 5; i++) client.link("s" + i, "a");

        DataplexSnapshot snapshot = loader(client).loadAll(List.of(fqn("a"), fqn("b")), null);

        assertEquals(DataplexGraphLoader.MAX_ENTITIES + 2, snapshot.entities().size());
        assertTrue(snapshot.entities().containsAll(fqns("a", "b")));
        assertTrue(snapshot.truncated());
    }

    @Test
    void upstreamReadHoldsTheRootsAndTheEntitiesReachedUpstream() {
        DataplexSnapshot snapshot = loader(twoRoots()).loadAll(List.of(fqn("a"), fqn("b")), null);

        assertEquals(fqns("a", "b", "x", "y"), snapshot.upstreamRead());
    }

    @Test
    void theLoadIsOneStreamingCallForBothDirections() {
        FakeLineageClient client = twoRoots();

        loader(client).loadAll(List.of(fqn("a"), fqn("b")), null);

        String roots = "[" + fqn("a") + ", " + fqn("b") + "]";
        assertEquals(List.of("STREAM UPSTREAM " + roots + " depth=3", "STREAM DOWNSTREAM " + roots + " depth=3"),
                client.calls);
    }

    @Test
    void linksLastSeenBeforeTheStartAreLeftOut() {
        FakeLineageClient client = new FakeLineageClient()
                .link("x", "a", NOW.minusSeconds(90_000), NOW.minusSeconds(80_000)).link("y", "a");

        DataplexSnapshot snapshot = loader(client).loadAll(List.of(fqn("a")), NOW.minusSeconds(40_000));

        assertEquals(fqns("a", "y"), snapshot.entities());
    }

    @Test
    void includeAddsAnEntityWithItsDirectLinks() {
        FakeLineageClient client = twoRoots().link("w", "z");
        DataplexGraphLoader loader = loader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a"), fqn("b")), null);
        client.calls.clear();

        DataplexSnapshot included = loader.include(loaded, fqn("z"));

        assertTrue(included.entities().contains(fqn("z")));
        assertTrue(pairs(included.links()).contains(fqn("z") + ">" + fqn("y")));
        assertEquals(1, included.hiddenUpstream(fqn("z")));
        assertEquals(List.of("UPSTREAM " + fqn("z"), "DOWNSTREAM " + fqn("z")), client.calls);
    }

    @Test
    void expandWorksOnAStreamedSnapshot() {
        FakeLineageClient client = twoRoots();
        DataplexGraphLoader loader = loader(client);
        DataplexSnapshot loaded = loader.loadAll(List.of(fqn("a"), fqn("b")), null);

        DataplexSnapshot expanded = loader.expand(loaded, fqn("o2"), Direction.DOWNSTREAM);

        assertTrue(expanded.entities().contains(fqn("o3")));
        List<String> all = pairs(expanded.links());
        assertEquals(1, all.stream().filter((fqn("o1") + ">" + fqn("o2"))::equals).count());
        assertEquals(1, all.stream().filter((fqn("o2") + ">" + fqn("o3"))::equals).count());
    }
}
