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
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.RunConfigUiUtils;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessRun;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedProcess;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures;
import io.github.rejeb.dataform.language.lineage.view.LineageViewFixtures;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class RunsPanelTest extends BasePlatformTestCase {

    private static final Instant NOW = DataplexViewFixtures.LOADED_AT;
    private static final String RAW = LineageNode.idOf("other.landing.raw");
    private static final String ALPHA = LineageViewFixtures.ALPHA.id();
    private static final String BETA = LineageViewFixtures.BETA.id();

    private DataplexViewFixtures.ScriptedService service;
    private DataplexViewFixtures.ManualExecutor executor;
    private final List<String> opened = new ArrayList<>();
    private LineageModel model;
    private RunsPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        service = new DataplexViewFixtures.ScriptedService();
        executor = new DataplexViewFixtures.ManualExecutor();
        model = LineageViewFixtures.model();
        panel = new RunsPanel(() -> service, executor, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> {
                }, opened::add);
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

    private static ProcessRun run(String process, String jobId, Instant start, String source, String target) {
        return new ProcessRun(new RecordedRun("processes/" + process + "/runs/" + start, RecordedRun.State.COMPLETED,
                start, start.plusSeconds(12)), new RecordedProcess("processes/" + process, process, "BIGQUERY", jobId),
                List.of(EntityNames.fqnOf(source)), EntityNames.fqnOf(target));
    }

    public void testWithoutALineageEditorItPointsAtKnowledgeCatalog() {
        panel.show(null);

        assertEquals(LineageToolWindowState.NO_EDITOR, panel.state());
        assertEquals("Runs, trace and differences come from Knowledge Catalog", EmptyStatusTexts.primary(panel.emptyStatus()));
        assertEquals("Show the Knowledge Catalog lineage", EmptyStatusTexts.link(panel.emptyStatus()));
        assertEquals("", panel.header());
        assertEquals(0, panel.rows().getRowCount());
    }

    public void testTheDataformSourcePointsAtKnowledgeCatalogToo() {
        panel.show(model);

        assertEquals(LineageToolWindowState.DATAFORM_SOURCE, panel.state());
        assertEquals("Show the Knowledge Catalog lineage", EmptyStatusTexts.link(panel.emptyStatus()));
    }

    public void testKnowledgeCatalogWithoutASelectionAsksForATable() {
        recorded();

        assertEquals(LineageToolWindowState.NO_SELECTION, panel.state());
        assertEquals("Select a table in the lineage view", EmptyStatusTexts.primary(panel.emptyStatus()));
        assertNull(EmptyStatusTexts.link(panel.emptyStatus()));
    }

    public void testTheRunsHeaderNamesTheSelectedTable() {
        recorded();

        model.select(ALPHA);

        assertEquals("Runs that wrote alpha", panel.header());
        assertEquals("Last 30 days", panel.rangeText());
        assertEquals("Loading…", EmptyStatusTexts.primary(panel.emptyStatus()));
        settle();
        assertEquals(List.of(ALPHA), service.runsAsked);
    }

    public void testTheRunsOfTheTableAreListedOnceRead() {
        recorded();
        service.runs = List.of(run("workflow", "job_1", NOW.minusSeconds(3_600), "other.landing.raw", "p.a.alpha"));

        model.select(ALPHA);
        assertEquals(0, panel.rows().getRowCount());
        settle();

        assertEquals(1, panel.rows().getRowCount());
        assertEquals("workflow", panel.rows().getValueAt(0, RunsTableModel.PROCESS));
        assertEquals("raw → alpha", panel.rows().getValueAt(0, RunsTableModel.MOVED));
        assertEquals("Today 09:00", panel.rows().getValueAt(0, RunsTableModel.STARTED));
    }

    public void testASelectedLinkShowsTheRunsOfThatLink() {
        recorded();
        service.runsOf.put(RAW + ">" + ALPHA, List.of(run("load", null, NOW.minusSeconds(60), "other.landing.raw", "p.a.alpha")));

        model.selectLink(new LinkRef(RAW, ALPHA));
        settle();

        assertEquals("Runs of the link raw → alpha", panel.header());
        assertEquals(List.of(RAW + ">" + ALPHA), service.runsAsked);
        assertEquals("load", panel.rows().getValueAt(0, RunsTableModel.PROCESS));
    }

    public void testATableWithoutRunSaysSo() {
        recorded();

        model.select(ALPHA);
        settle();

        assertEquals("No run wrote alpha in the last 30 days", EmptyStatusTexts.primary(panel.emptyStatus()));
    }

    public void testALinkWithoutRunSaysSo() {
        recorded();

        model.selectLink(new LinkRef(RAW, ALPHA));
        settle();

        assertEquals("No run read raw to write alpha in the last 30 days", EmptyStatusTexts.primary(panel.emptyStatus()));
    }

    public void testAFailureSaysWhyKnowledgeCatalogCannotAnswer() {
        recorded();
        service.runsFailure = new DataplexLineageException(DataplexLineageException.Kind.PERMISSION_DENIED, "403", null);

        model.select(ALPHA);
        settle();

        assertEquals(LineageToolWindowState.CANNOT_ANSWER, panel.state());
        assertEquals("Knowledge Catalog cannot answer: your account cannot read the lineage", EmptyStatusTexts.primary(panel.emptyStatus()));
        assertEquals("", panel.header());
    }

    public void testAnotherSelectionAfterAFailureAsksAgain() {
        recorded();
        service.runsFailure = new DataplexLineageException(DataplexLineageException.Kind.OTHER, "boom", null);
        model.select(ALPHA);
        settle();
        service.runsFailure = null;

        model.select(BETA);
        settle();

        assertEquals(LineageToolWindowState.READY, panel.state());
        assertEquals("Runs that wrote beta", panel.header());
    }

    public void testAnOlderAnswerIsDiscarded() {
        recorded();
        service.runsOf.put(ALPHA, List.of(run("for alpha", null, NOW.minusSeconds(60), "other.landing.raw", "p.a.alpha")));
        service.runsOf.put(BETA, List.of(run("for beta", null, NOW.minusSeconds(60), "p.a.alpha", "p.b.beta")));
        model.select(ALPHA);
        model.select(BETA);

        executor.run(1);
        executor.run(0);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals("for beta", panel.rows().getValueAt(0, RunsTableModel.PROCESS));
    }

    public void testAChangeThatKeepsTheSelectionDoesNotReadAgain() {
        recorded();
        model.select(ALPHA);
        settle();

        model.setHover(BETA);
        model.toggleDensity();

        assertEquals(List.of(ALPHA), service.runsAsked);
    }

    public void testAnotherTimeRangeIsNamedAndReadAgain() {
        recorded();
        model.select(ALPHA);
        settle();
        DataplexView shorter = new DataplexView(model.dataplexView().snapshot(), model.dataplexView().graph(),
                TimeRange.LAST_7_DAYS, model.dataplexView().scope(), model.dataplexView().repository(),
                model.dataplexView().assertionNodeIds());

        model.setDataplexView(shorter);
        settle();

        assertEquals("Last 7 days", panel.rangeText());
        assertEquals(List.of(ALPHA, ALPHA), service.runsAsked);
    }

    public void testSwitchingBackToDataformClearsTheRuns() {
        recorded();
        service.runs = List.of(run("workflow", null, NOW.minusSeconds(60), "other.landing.raw", "p.a.alpha"));
        model.select(ALPHA);
        settle();

        model.setSource(LineageSource.DATAFORM);

        assertEquals(LineageToolWindowState.DATAFORM_SOURCE, panel.state());
        assertEquals(0, panel.rows().getRowCount());
        assertEquals("", panel.header());
    }

    public void testOpenLatestJobOpensTheBigQueryJobOfTheRow() {
        recorded();
        service.runs = List.of(run("workflow", "job_1", NOW.minusSeconds(60), "other.landing.raw", "p.a.alpha"),
                run("query", null, NOW.minusSeconds(120), "other.landing.raw", "p.a.alpha"));
        model.select(ALPHA);
        settle();

        panel.openJob(0);
        panel.openJob(1);

        assertEquals(List.of(RunConfigUiUtils.bigQueryJobUrl("p", "EU", "job_1")), opened);
    }
}
