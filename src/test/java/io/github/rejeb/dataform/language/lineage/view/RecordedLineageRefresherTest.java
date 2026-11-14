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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures.ManualExecutor;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures.ScriptedService;
import io.github.rejeb.dataform.language.lineage.model.RecordedActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedLineageRefresherTest {

    private static final CompiledGraph COMPILED = new CompiledGraph();
    private static final DataplexView CACHED = DataplexViewFixtures.view(LineageGraph.builder().build(), "p.d.a");
    private static final DataplexView FRESH = DataplexViewFixtures.view(LineageGraph.builder().build(), "p.d.b");

    private final ScriptedService service = new ScriptedService();
    private final ManualExecutor executor = new ManualExecutor();
    private final List<String> events = new ArrayList<>();
    private final RecordedLineageRefresher refresher = new RecordedLineageRefresher(service, executor, Runnable::run,
            new RecordedLineageRefresher.Listener() {
                @Override
                public void onView(@NotNull DataplexView view, boolean update) {
                    events.add("VIEW " + (view == CACHED ? "cached" : view == FRESH ? "fresh" : "other") + " " + update);
                }

                @Override
                public void onActivity(@NotNull RecordedActivity activity) {
                    events.add("ACTIVITY " + activity);
                }

                @Override
                public void onFailure(@NotNull DataplexLineageException.Kind kind, @Nullable String detail, boolean viewShown) {
                    events.add("FAILURE " + kind + " " + viewShown);
                }
            });

    private List<String> logOf(String prefix) {
        return service.log.stream().filter(entry -> entry.startsWith(prefix)).toList();
    }

    @Test
    void aCachedViewIsShownBeforeTheCheck() {
        service.cached = CACHED;

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("VIEW cached false", "ACTIVITY CHECKING", "ACTIVITY NONE"), events);
        assertEquals(List.of("RETAIN LAST_30_DAYS", "CACHED LAST_30_DAYS", "CHECK LAST_30_DAYS"), service.log);
    }

    @Test
    void anUnchangedCheckDoesNotReload() {
        service.cached = CACHED;

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertTrue(logOf("RELOAD").isEmpty());
    }

    @Test
    void aChangedCheckReloadsAndDeliversAnUpdate() {
        service.cached = CACHED;
        service.stale = true;
        service.answer(FRESH);

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("VIEW cached false", "ACTIVITY CHECKING", "ACTIVITY UPDATING", "VIEW fresh true",
                "ACTIVITY NONE"), events);
    }

    @Test
    void withoutCacheItLoadsWithTheLoadingActivity() {
        service.answer(FRESH);

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("ACTIVITY LOADING", "VIEW fresh false", "ACTIVITY NONE"), events);
        assertTrue(logOf("CHECK").isEmpty());
    }

    @Test
    void aFailureWithAShownViewSaysSo() {
        service.cached = CACHED;
        service.stale = true;
        service.fail(DataplexLineageException.Kind.PERMISSION_DENIED);

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertTrue(events.contains("FAILURE PERMISSION_DENIED true"), events.toString());
        assertEquals("ACTIVITY NONE", events.getLast());
    }

    @Test
    void aFailureWithoutAViewSaysSo() {
        service.fail(DataplexLineageException.Kind.API_DISABLED);

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertTrue(events.contains("FAILURE API_DISABLED false"), events.toString());
    }

    @Test
    void aFailedCheckIsSilent() {
        service.cached = CACHED;
        service.checkFailure = new DataplexLineageException(DataplexLineageException.Kind.OTHER, "offline", null);

        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("VIEW cached false", "ACTIVITY CHECKING", "ACTIVITY NONE"), events);
    }

    @Test
    void hideDropsAPendingAnswerAndReleases() {
        service.answer(FRESH);
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();
        events.clear();
        service.stale = true;
        service.cached = CACHED;
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);

        refresher.hide();
        executor.runAll();

        assertFalse(events.stream().anyMatch(event -> event.startsWith("VIEW")), events.toString());
        assertEquals(List.of("RELEASE LAST_30_DAYS"), logOf("RELEASE"));
    }

    @Test
    void showRetainsOnceAndAnotherRangeReleasesTheFirst() {
        service.cached = CACHED;
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("RETAIN LAST_30_DAYS"), logOf("RETAIN"));

        refresher.show(COMPILED, TimeRange.LAST_7_DAYS);
        executor.runAll();

        assertEquals(List.of("RETAIN LAST_30_DAYS", "RETAIN LAST_7_DAYS"), logOf("RETAIN"));
        assertEquals(List.of("RELEASE LAST_30_DAYS"), logOf("RELEASE"));
    }

    @Test
    void theReloadButtonSkipsTheCheck() {
        service.cached = CACHED;
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();
        events.clear();
        service.answer(FRESH);

        refresher.reload(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("ACTIVITY UPDATING", "VIEW fresh true", "ACTIVITY NONE"), events);
        assertEquals(1, logOf("CHECK").size());
    }

    @Test
    void aNewEntryReadForAnotherViewIsShownAsAnUpdate() {
        service.cached = CACHED;
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();
        events.clear();

        service.announce(FRESH);

        assertEquals(List.of("VIEW fresh true"), events);
    }

    @Test
    void aNewEntryOfAnotherRangeOrAfterHideIsNotShown() {
        service.cached = CACHED;
        refresher.show(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();
        events.clear();

        service.lastReceiver.accept(new DataplexView(FRESH.snapshot(), FRESH.graph(), TimeRange.LAST_7_DAYS,
                FRESH.scope(), FRESH.repository(), FRESH.assertionNodeIds()));
        refresher.hide();
        service.lastReceiver.accept(FRESH);

        assertTrue(events.isEmpty(), events.toString());
    }

    @Test
    void theReloadButtonWithNothingShownLoads() {
        service.answer(FRESH);

        refresher.reload(COMPILED, TimeRange.LAST_30_DAYS);
        executor.runAll();

        assertEquals(List.of("ACTIVITY LOADING", "VIEW fresh false", "ACTIVITY NONE"), events);
    }
}
