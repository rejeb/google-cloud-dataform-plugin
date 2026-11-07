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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.BrowserLink;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ActionDetailsTabPanelTest extends BasePlatformTestCase {

    private static final String SCRIPT =
            "BEGIN\n  CREATE SCHEMA IF NOT EXISTS x;\n  SELECT 1;\n  MERGE t\n  USING s\n  ON SUM(a);\nEND";
    private static final List<String> STATEMENTS =
            List.of("CREATE SCHEMA IF NOT EXISTS x", "SELECT 1", "MERGE t\n  USING s\n  ON SUM(a)");
    private static final Instant START = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant END = START.plusMillis(21_400);

    private final List<String> calls = new ArrayList<>();
    private ActionDetailsTabPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new ActionDetailsTabPanel(getProject(), () -> calls.add("executed sql"));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(panel);
        } finally {
            super.tearDown();
        }
    }

    public void testNoJobShowsTheEmptyState() {
        panel.showDetails(action(SCRIPT), null);

        assertEquals("empty", panel.shownCard());
    }

    public void testASingleStatementJobPointsToTheExecutedSql() {
        panel.showDetails(action(SCRIPT), detailsWithoutChildren());

        assertEquals("single", panel.shownCard());
        assertTrue(panel.scriptLineText(), panel.scriptLineText().startsWith("Job "));
        panel.openExecutedSql();
        assertEquals(List.of("executed sql"), calls);
    }

    public void testTheScriptLineSummarisesTheScript() {
        panel.showDetails(action(SCRIPT), succeededDetails());

        assertEquals("content", panel.shownCard());
        assertEquals("Script job job1 · 3 statements · 21.4 s", panel.scriptLineText());
    }

    public void testJobIdsAreLinksInTheIdStyle() {
        panel.showDetails(action(SCRIPT), failedDetails());

        List<BrowserLink> links = new ArrayList<>();
        collectBrowserLinks(panel, links);

        assertEquals(List.of("job1", "c"), links.stream().map(BrowserLink::getText).toList());
        links.forEach(IdLinksTest::assertIdStyle);
    }

    public void testTheSelectedStatementShowsItsSqlVerbatim() {
        panel.showDetails(action(SCRIPT), detailsWithLastQuery("select  1\n   from t"));

        assertEquals("select  1\n   from t", panel.viewer().text());
    }

    public void testTheHeaderDescribesTheSelectedStatement() {
        panel.showDetails(action(SCRIPT), failedDetails());

        String header = panel.header().text();
        assertTrue(header, header.startsWith("MERGE p.d.orders_daily c "));
    }

    public void testAFailedStatementOpensWithItsErrorMarked() {
        panel.showDetails(action(SCRIPT), failedDetails());

        assertEquals("c", selectedJobId());
        assertEquals(1, blockInlays(panel.viewer()).size());
    }

    public void testAnotherStatementShowsNoErrorMark() {
        panel.showDetails(action(SCRIPT), failedDetails());

        panel.timeline().setRowSelectionInterval(0, 0);

        assertEquals("CREATE SCHEMA IF NOT EXISTS x", panel.viewer().text());
        assertEmpty(blockInlays(panel.viewer()));
    }

    public void testSelectingTheFailedStatementAgainMarksItAgain() {
        panel.showDetails(action(SCRIPT), failedDetails());

        panel.timeline().setRowSelectionInterval(0, 0);
        panel.timeline().setRowSelectionInterval(2, 2);

        assertEquals(1, blockInlays(panel.viewer()).size());
    }

    public void testAnUnmappableErrorLeavesTheSqlUnmarked() {
        panel.showDetails(action(null), failedDetails());

        assertEquals("c", selectedJobId());
        assertEmpty(blockInlays(panel.viewer()));
    }

    public void testAStatementWithoutSqlShowsTheNoSqlText() {
        panel.showDetails(action(SCRIPT), detailsWithLastQuery(null));

        assertTrue(panel.showsNoSqlText());
    }

    public void testTheTimelineIsFittedToItsRows() {
        panel.setSize(900, 600);
        panel.showDetails(action(SCRIPT), failedDetails());

        layOut(panel);

        assertEquals(panel.timeline().fittedHeight(ChildJobsTimeline.FITTED_ROWS),
                panel.splitter().getFirstComponent().getHeight(), 2);
    }

    public void testADraggedDividerIsKeptForTheNextAction() {
        panel.setSize(900, 600);
        panel.showDetails(action(SCRIPT), failedDetails());
        layOut(panel);

        panel.splitter().setProportion(0.5f);
        panel.showDetails(action(SCRIPT), succeededDetails());
        layOut(panel);

        assertEquals(0.5f, panel.splitter().getProportion(), 0.001f);
    }

    private String selectedJobId() {
        BigQueryChildJob job = panel.timeline().selectedJob();
        assertNotNull(job);
        return job.jobId();
    }

    private static List<Inlay<?>> blockInlays(SqlViewer viewer) {
        EditorEx editor = viewer.editor();
        assertNotNull(editor);
        return new ArrayList<>(editor.getInlayModel().getBlockElementsInRange(0, editor.getDocument().getTextLength()));
    }

    private static void collectBrowserLinks(Component root, List<BrowserLink> links) {
        if (root instanceof BrowserLink link) links.add(link);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collectBrowserLinks(child, links);
        }
    }

    private static void layOut(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    private static InvocationActionResult action(String script) {
        return new InvocationActionResult("p.d.orders_daily", InvocationActionState.FAILED, "The action failed.",
                START, END, "job1", "p", "EU", null, script);
    }

    private static BigQueryJobDetails failedDetails() {
        return details("FAILED", "No matching signature for SUM at [6:6]", List.of(
                child("a", "DONE", 0, "CREATE_SCHEMA", null, STATEMENTS.get(0)),
                child("b", "DONE", 1, "SELECT", null, STATEMENTS.get(1)),
                child("c", "FAILED", 2, "MERGE", "p.d.orders_daily", STATEMENTS.get(2))));
    }

    private static BigQueryJobDetails succeededDetails() {
        return detailsWithLastQuery(STATEMENTS.get(2));
    }

    private static BigQueryJobDetails detailsWithLastQuery(String query) {
        return details("DONE", null, List.of(
                child("a", "DONE", 0, "CREATE_SCHEMA", null, STATEMENTS.get(0)),
                child("b", "DONE", 1, "SELECT", null, STATEMENTS.get(1)),
                child("c", "DONE", 2, "MERGE", "p.d.orders_daily", query)));
    }

    private static BigQueryJobDetails detailsWithoutChildren() {
        return details("DONE", null, List.of());
    }

    private static BigQueryJobDetails details(String status, String error, List<BigQueryChildJob> children) {
        return new BigQueryJobDetails("job1", "p", "EU", status, error, null, null, START, END,
                Math.max(1, children.size()), children, null);
    }

    private static BigQueryChildJob child(String id, String status, int second, String type, String target,
                                          String query) {
        Instant start = START.plusSeconds(second);
        return new BigQueryChildJob(id, status, start, start.plusSeconds(1), query, null, type, target, null);
    }
}
