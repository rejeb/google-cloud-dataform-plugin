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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.dataplex.TraceEntry;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures;
import io.github.rejeb.dataform.language.lineage.view.LineageViewFixtures;

import java.util.ArrayList;
import java.util.List;

public class TracePanelTest extends BasePlatformTestCase {

    private static final String RAW = LineageNode.idOf("other.landing.raw");
    private static final String ALPHA = LineageViewFixtures.ALPHA.id();
    private static final String BETA = LineageViewFixtures.BETA.id();

    private DataplexViewFixtures.ScriptedService service;
    private DataplexViewFixtures.ManualExecutor executor;
    private LineageModel model;
    private TracePanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        service = new DataplexViewFixtures.ScriptedService();
        executor = new DataplexViewFixtures.ManualExecutor();
        model = LineageViewFixtures.model();
        panel = new TracePanel(() -> service, executor, () -> {
        });
        model.addListener(panel::show);
    }

    private void recorded() {
        DataplexView view = DataplexViewFixtures.view(model.graph(), "p.a.alpha",
                "other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
        model.setSource(LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
    }

    private void settle() {
        executor.runAll();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static TraceEntry entry(String fullName, int level) {
        return new TraceEntry(EntityNames.fqnOf(fullName), level);
    }

    public void testWithoutASelectionItAsksForATable() {
        recorded();

        assertEquals(LineageToolWindowState.NO_SELECTION, panel.state());
        assertEquals("Select a table in the lineage view", EmptyStatusTexts.primary(panel.emptyStatus()));
        assertEquals("", panel.header());
    }

    public void testWithTheDataformSourceItPointsAtKnowledgeCatalog() {
        panel.show(model);

        assertEquals(LineageToolWindowState.DATAFORM_SOURCE, panel.state());
        assertEquals("Show the Knowledge Catalog lineage", EmptyStatusTexts.link(panel.emptyStatus()));
    }

    public void testTheHeaderNamesTheSelectedTableAndStartsUpstream() {
        recorded();

        model.select(ALPHA);

        assertEquals("Trace of alpha", panel.header());
        assertEquals(Direction.UPSTREAM, panel.directionSwitch().selected());
        assertEquals("Up to 5 levels, including what is not on the graph yet", panel.hint());
        assertEquals("Loading…", EmptyStatusTexts.primary(panel.emptyStatus()));
        settle();
        assertEquals(List.of("UPSTREAM " + ALPHA), service.traces);
    }

    public void testEntriesAreListedUnderTheirProjectWithTheirLevel() {
        recorded();
        service.trace = List.of(entry("other.landing.raw", 1), entry("p.staging.stg_orders", 1),
                entry("other.ingest.files", 2));

        model.select(ALPHA);
        settle();

        assertEquals(List.of("p (1)", "stg_orders · p.staging · Level 1",
                "other (2)", "raw · other.landing · Level 1", "files · other.ingest · Level 2"), panel.rowTexts());
    }

    public void testASelectedLinkTracesTheTableItWrites() {
        recorded();

        model.selectLink(new LinkRef(RAW, ALPHA));
        settle();

        assertEquals("Trace of alpha", panel.header());
        assertEquals(List.of("UPSTREAM " + ALPHA), service.traces);
    }

    public void testTheSwitchTracesDownstream() {
        recorded();
        model.select(ALPHA);
        settle();

        panel.directionSwitch().select(Direction.DOWNSTREAM);
        settle();

        assertEquals(List.of("UPSTREAM " + ALPHA, "DOWNSTREAM " + ALPHA), service.traces);
    }

    public void testAnEmptyTraceSaysNothingWasRecordedInTheRange() {
        recorded();

        model.select(ALPHA);
        settle();
        assertEquals("Nothing upstream of alpha was recorded in the last 30 days", EmptyStatusTexts.primary(panel.emptyStatus()));

        panel.directionSwitch().select(Direction.DOWNSTREAM);
        settle();
        assertEquals("Nothing downstream of alpha was recorded in the last 30 days", EmptyStatusTexts.primary(panel.emptyStatus()));
    }

    public void testClickingATraceRowLoadsTheEntityOntoTheGraphAndSelectsIt() {
        recorded();
        List<String> requested = new ArrayList<>();
        model.setNodeRequestHandler(requested::add);
        service.trace = List.of(entry("other.landing.raw", 1), entry("other.ingest.files", 2));
        model.select(ALPHA);
        settle();
        assertEquals(List.of("other (2)", "raw · other.landing · Level 1", "files · other.ingest · Level 2"), panel.rowTexts());

        panel.open(2);

        assertEquals(List.of(LineageNode.idOf("other.ingest.files")), requested);
        assertEquals(ALPHA, model.selectedId());
    }

    public void testClickingARowOfAnEntityOnTheGraphSelectsIt() {
        recorded();
        service.trace = List.of(entry("other.landing.raw", 1));
        model.select(ALPHA);
        settle();

        panel.open(1);

        assertEquals(RAW, model.selectedId());
    }

    public void testAGroupRowOpensNothing() {
        recorded();
        service.trace = List.of(entry("other.landing.raw", 1));
        model.select(ALPHA);
        settle();

        panel.open(0);
        panel.open(7);

        assertEquals(ALPHA, model.selectedId());
    }

    public void testAFailureSaysWhyKnowledgeCatalogCannotAnswer() {
        recorded();
        service.traceFailure = new DataplexLineageException(DataplexLineageException.Kind.API_DISABLED, "403", null);

        model.select(ALPHA);
        settle();

        assertEquals(LineageToolWindowState.CANNOT_ANSWER, panel.state());
        assertEquals("Knowledge Catalog cannot answer: the Data Lineage API is not enabled",
                EmptyStatusTexts.primary(panel.emptyStatus()));
        assertTrue(panel.rowTexts().isEmpty());
    }

    public void testAnOlderAnswerIsDiscarded() {
        recorded();
        model.select(ALPHA);
        service.trace = List.of(entry("p.a.alpha", 1));
        model.select(BETA);

        executor.run(1);
        service.trace = List.of(entry("other.landing.raw", 1));
        executor.run(0);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals("Trace of beta", panel.header());
        assertEquals(List.of("p (1)", "alpha · p.a · Level 1"), panel.rowTexts());
    }

    public void testAChangeThatKeepsTheSelectionDoesNotTraceAgain() {
        recorded();
        model.select(ALPHA);
        settle();

        model.setHover(BETA);
        model.toggleDensity();
        settle();

        assertEquals(List.of("UPSTREAM " + ALPHA), service.traces);
    }
}
