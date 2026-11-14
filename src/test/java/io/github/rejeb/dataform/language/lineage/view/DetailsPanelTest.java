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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.TrimmedTextField;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DetailsPanelTest extends BasePlatformTestCase {

    private DetailsPanel showDetailsOf(LineageModel model, String id) {
        DetailsPanel panel = new DetailsPanel(getProject(), model);
        model.select(id);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        return panel;
    }

    private static List<String> visibleLabels(DetailsPanel panel) {
        return LineageViewFixtures.all(panel, JBLabel.class).stream()
                .filter(label -> isVisibleUpTo(label, panel))
                .map(JBLabel::getText).toList();
    }

    private static boolean isVisibleUpTo(java.awt.Component component, java.awt.Component root) {
        for (java.awt.Component c = component; c != null && c != root; c = c.getParent()) {
            if (!c.isVisible()) return false;
        }
        return true;
    }

    public void testNothingSelectedShowsTheEmptyState() {
        DetailsPanel panel = new DetailsPanel(getProject(), LineageViewFixtures.model());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertTrue(panel.isEmptyStateShown());
        assertEquals("Select a node to see its details", panel.getEmptyText().getText());
    }

    public void testTheMinusButtonHidesTheDetailsWithoutASelection() {
        DetailsPanel panel = new DetailsPanel(getProject(), LineageViewFixtures.model());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        AtomicBoolean hidden = new AtomicBoolean();
        panel.setHideHandler(() -> hidden.set(true));

        List<InplaceButton> buttons = LineageViewFixtures.all(panel, InplaceButton.class).stream()
                .filter(button -> isVisibleUpTo(button, panel)).toList();
        assertEquals(1, buttons.size());
        assertEquals("Hide details", buttons.getFirst().getToolTipText());
        buttons.getFirst().doClick();

        assertTrue(hidden.get());
        assertTrue(panel.isEmptyStateShown());
    }

    public void testTheEmptyStateKeepsOnlyTheMinusButton() {
        DetailsPanel panel = new DetailsPanel(getProject(), LineageViewFixtures.model());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertFalse(isVisibleUpTo(panel.toolbar().getComponent(), panel));
        assertNull(panel.headerTitle());
    }

    public void testTheHeaderShowsTheNameThenTheSchemaAndType() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        assertFalse(panel.isEmptyStateShown());
        assertEquals("beta", panel.headerTitle());
        assertEquals("b · view", panel.headerSubtitle());
        assertInstanceOf(panel.headerIcon(), TypeGlyphIcon.class);
    }

    public void testTheRowsAreFileSchemaAndTags() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        List<String> labels = visibleLabels(panel);
        assertTrue(labels.toString(), labels.containsAll(List.of("File", "Schema", "Tags")));
        assertFalse(labels.toString(), labels.contains("Full name"));
        assertFalse(labels.toString(), labels.contains("Type"));
        List<String> links = LineageViewFixtures.all(panel, ActionLink.class).stream().map(ActionLink::getText).toList();
        assertTrue(links.toString(), links.contains("definitions/beta.sqlx"));
        assertEquals("y, x", panel.valueOf("Tags"));
    }

    public void testAnActionWithoutTagsShowsADash() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.GAMMA.id());

        assertEquals("—", panel.valueOf("Tags"));
    }

    public void testTheDependenciesAreCountedListsWithAnEmptyNote() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        assertEquals(List.of(LineageViewFixtures.ALPHA.id()), items(panel.upstream()));
        assertEquals(List.of(), items(panel.downstream()));
        List<String> labels = visibleLabels(panel);
        assertTrue(labels.toString(), labels.containsAll(List.of("Upstream", "1", "Downstream", "0", "No downstream actions")));
        assertFalse(labels.toString(), labels.contains("No upstream actions"));
    }

    public void testASelectedColumnShowsItsNameAndTableOnTheirOwnRows() {
        ColumnRef column = new ColumnRef("p.analytics.orders_enriched", "customer_id");
        LineageNode table = LineageViewFixtures.node("p.analytics.orders_enriched", "analytics", "table", List.of(), null);
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(table).build());
        model.setColumnGraph(ColumnLineageGraph.builder().addColumn(column).build());
        DetailsPanel panel = new DetailsPanel(getProject(), model);

        model.selectColumn(column.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        List<String> labels = visibleLabels(panel);
        assertTrue(labels.toString(), labels.contains("Column") && labels.contains("Table"));
        List<String> fields = LineageViewFixtures.all(panel, JBTextField.class).stream().map(JBTextField::getText).toList();
        assertTrue(fields.toString(), fields.contains("customer_id"));
        assertTrue(fields.toString(), fields.contains("analytics.orders_enriched"));
        JBTextField tableField = LineageViewFixtures.all(panel, JBTextField.class).stream()
                .filter(field -> field.getText().equals("analytics.orders_enriched")).findFirst().orElseThrow();
        assertEquals("p.analytics.orders_enriched", tableField.getToolTipText());
    }

    public void testAClickSelectsTheDependency() {
        LineageModel model = LineageViewFixtures.model();
        DetailsPanel panel = showDetailsOf(model, LineageViewFixtures.BETA.id());
        JBList<String> list = panel.upstream();
        list.setSize(200, 100);

        long now = System.currentTimeMillis();
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_PRESSED, now, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_RELEASED, now, 0, 5, 5, 1, false, MouseEvent.BUTTON1));
        list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, now, 0, 5, 5, 1, false, MouseEvent.BUTTON1));

        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testOpenSqlxRunAndFocusAreTextButtons() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());

        List<AnAction> actions = actions(panel);
        assertEquals(List.of("Open SQLX", "Run", "Focus"),
                actions.stream().map(a -> a.getTemplatePresentation().getText()).toList());
        for (AnAction action : actions) {
            assertEquals(Boolean.TRUE, action.getTemplatePresentation().getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
        }
    }

    public void testFocusReadsExitFocusWhileFocused() {
        LineageModel model = LineageViewFixtures.model();
        DetailsPanel panel = showDetailsOf(model, LineageViewFixtures.BETA.id());
        model.focusOn(LineageViewFixtures.BETA.id());
        AnAction focus = actions(panel).get(2);
        AnActionEvent event = TestActionEvent.createTestEvent(focus);

        focus.update(event);

        assertEquals("Exit focus", event.getPresentation().getText());
    }

    public void testADeclarationCannotBeRun() {
        LineageNode source = LineageViewFixtures.node("p.raw.orders", "raw", "declaration", List.of(), "definitions/orders.sqlx");
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(source).build());
        DetailsPanel panel = showDetailsOf(model, source.id());
        AnAction run = actions(panel).get(1);
        AnActionEvent event = TestActionEvent.createTestEvent(run);

        run.update(event);

        assertFalse(event.getPresentation().isVisible());
    }

    public void testTheMinusButtonHidesTheDetails() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.BETA.id());
        AtomicBoolean hidden = new AtomicBoolean();
        panel.setHideHandler(() -> hidden.set(true));
        InplaceButton minus = LineageViewFixtures.all(panel, InplaceButton.class).getFirst();

        assertEquals("Hide details", minus.getToolTipText());
        minus.doClick();

        assertTrue(hidden.get());
    }

    public void testALongFileNameIsCutToThePanelWidth() {
        LineageNode deep = LineageViewFixtures.node("p.analytics.orders_enriched", "analytics", "table", List.of(),
                "definitions/analytics/very/deep/folder/structure/orders_enriched_with_a_long_name.sqlx");
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(deep).build());
        DetailsPanel panel = showDetailsOf(model, deep.id());
        panel.setSize(300, 600);
        layout(panel);
        layout(panel);

        ActionLink link = LineageViewFixtures.all(panel, ActionLink.class).getFirst();
        java.awt.Point corner = javax.swing.SwingUtilities.convertPoint(link, link.getWidth(), 0, panel);
        assertTrue("link ends at " + corner.x, corner.x <= 300);
    }

    private static void layout(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component child : k.getComponents()) layout(child);
        }
    }

    private static List<AnAction> actions(DetailsPanel panel) {
        DefaultActionGroup group = (DefaultActionGroup) panel.toolbar().getActionGroup();
        return Arrays.asList(group.getChildActionsOrStubs());
    }

    private static List<String> items(JBList<String> list) {
        return java.util.stream.IntStream.range(0, list.getModel().getSize()).mapToObj(list.getModel()::getElementAt).toList();
    }

    private static final java.time.Instant NOW = DataplexViewFixtures.LOADED_AT;
    private static final String RAW = LineageNode.idOf("other.landing.raw");

    private DataplexViewFixtures.ScriptedService service;
    private DataplexViewFixtures.ManualExecutor executor;
    private final java.util.List<String> opened = new java.util.ArrayList<>();
    private LineageModel recorded;

    private DetailsPanel recordedDetails(String... ends) {
        service = new DataplexViewFixtures.ScriptedService();
        executor = new DataplexViewFixtures.ManualExecutor();
        recorded = LineageViewFixtures.model();
        io.github.rejeb.dataform.language.lineage.dataplex.DataplexView view =
                DataplexViewFixtures.view(recorded.graph(), "p.a.alpha", ends);
        DetailsPanel panel = new DetailsPanel(getProject(), recorded, service, executor,
                java.time.Clock.fixed(NOW, java.time.ZoneId.of("UTC")), opened::add);
        recorded.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        recorded.setDataplexView(view);
        recorded.setGraph(view.graph());
        return panel;
    }

    private void settle() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        executor.runAll();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static List<String> texts(DetailsPanel panel) {
        return actions(panel).stream().map(a -> a.getTemplatePresentation().getText()).toList();
    }

    private void perform(DetailsPanel panel, String text) {
        AnAction action = actions(panel).stream().filter(a -> text.equals(a.getTemplatePresentation().getText()))
                .findFirst().orElseThrow();
        action.actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent(action));
    }

    public void testARepositoryTableInDataplexModeShowsBigQueryNameAndLastWriteAndOpenInKnowledgeCatalog() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
        service.freshness = new io.github.rejeb.dataform.language.lineage.dataplex.Freshness(
                NOW.minusSeconds(7_200), "Scheduled query", false);
        recorded.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals("p.a.alpha", panel.valueOf("BigQuery"));
        assertEquals("Loading…", panel.valueOf("Last write"));
        assertEquals(List.of("Open SQLX", "Run", "Focus", "Open in Knowledge Catalog"), texts(panel));

        settle();

        assertEquals("Today 08:00 · 2 hours ago", panel.valueOf("Last write"));
        perform(panel, "Open in Knowledge Catalog");
        assertEquals(List.of(io.github.rejeb.dataform.language.gcp.common.BigQueryConsoleUrls.lineageUrl("p.a.alpha")), opened);
    }

    public void testATableWithNoRecordedWriteSaysSo() {
        DetailsPanel panel = recordedDetails("p.a.alpha", "p.b.beta");
        recorded.select(LineageViewFixtures.ALPHA.id());
        settle();

        assertEquals("Not recorded in the last 30 days", panel.valueOf("Last write"));
    }

    public void testAViewReadsCreatedOrReplacedInsteadOfLastWrite() {
        DetailsPanel panel = recordedDetails("p.a.alpha", "p.b.beta");
        service.freshness = new io.github.rejeb.dataform.language.lineage.dataplex.Freshness(
                NOW.minusSeconds(3 * 86_400), "BigQuery job", true);
        recorded.select(LineageViewFixtures.BETA.id());
        settle();

        assertNull(panel.valueOf("Last write"));
        assertEquals("9 Nov 10:00 · 3 days ago", panel.valueOf("Created or replaced"));
    }

    public void testAnOutsideTableShowsProjectAndDatasetAndOpenInBigQuery() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha");
        recorded.select(RAW);
        settle();

        assertEquals("raw", panel.headerTitle());
        assertEquals("other.landing · outside this repository", panel.headerSubtitle());
        assertEquals("other", panel.valueOf("Project"));
        assertEquals("landing", panel.valueOf("Dataset"));
        assertNull(panel.valueOf("File"));
        assertNotNull(panel.valueOf("Last write"));
        assertEquals(List.of("Open in BigQuery", "Focus", "Copy name"), texts(panel));

        perform(panel, "Open in BigQuery");
        assertEquals(List.of(io.github.rejeb.dataform.language.gcp.common.BigQueryConsoleUrls.tableUrl("other.landing.raw")), opened);
    }

    public void testAnEntityThatIsNotABigQueryTableShowsItsKindAndName() {
        DetailsPanel panel = recordedDetails("gcs:bucket.exports", "p.a.alpha");
        recorded.select(io.github.rejeb.dataform.language.lineage.dataplex.EntityNames.nodeIdOf("gcs:bucket.exports"));
        settle();

        assertEquals("gcs:bucket.exports", panel.valueOf("Name"));
        assertNotNull(panel.valueOf("Kind"));
        assertEquals(List.of("Focus", "Copy name"), texts(panel));
    }

    public void testTheDependencyCountsTellHowManyAreOutsideTheRepository() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
        recorded.select(LineageViewFixtures.ALPHA.id());
        settle();

        assertTrue(visibleLabels(panel).contains("1 · 1 outside this repository"));
        assertTrue(visibleLabels(panel).contains("1"));
    }

    public void testRecordedProcessesListWhoWroteAndReadTheTable() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
        service.processUses = List.of(
                new io.github.rejeb.dataform.language.lineage.dataplex.ProcessUse("Scheduled query", true, NOW.minusSeconds(3_600)),
                new io.github.rejeb.dataform.language.lineage.dataplex.ProcessUse("BigQuery job", false, NOW.minusSeconds(86_400)));
        recorded.select(LineageViewFixtures.ALPHA.id());
        settle();

        assertEquals(List.of("Scheduled query · wrote it today 09:00", "BigQuery job · read it yesterday 10:00"),
                panel.recordedProcesses());
    }

    public void testATableWithNoRecordedProcessSaysNoneInTheRange() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha");
        recorded.select(LineageViewFixtures.ALPHA.id());
        settle();

        assertTrue(panel.recordedProcesses().isEmpty());
        assertTrue(visibleLabels(panel).contains("None in the last 30 days"));
    }

    private DetailsPanel linkDetails(String from, String to, String... ends) {
        DetailsPanel panel = recordedDetails(ends);
        service.linkDetails = new io.github.rejeb.dataform.language.lineage.dataplex.LinkDetails(
                NOW.minusSeconds(9 * 86_400), NOW.minusSeconds(3_600),
                List.of(new io.github.rejeb.dataform.language.lineage.dataplex.RecordedProcess("processes/1", "Scheduled query", "BIGQUERY", "job_1"),
                        new io.github.rejeb.dataform.language.lineage.dataplex.RecordedProcess("processes/2", "BigQuery job", "BIGQUERY", null)),
                List.of(new io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun("r/1",
                                io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun.State.COMPLETED, NOW.minusSeconds(3_600), NOW),
                        new io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun("r/2",
                                io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun.State.COMPLETED, NOW.minusSeconds(90_000), NOW)));
        recorded.selectLink(new io.github.rejeb.dataform.language.lineage.model.LinkRef(from, to));
        settle();
        return panel;
    }

    public void testALinkShowsFirstAndLastRecordedRunsAndWhoRecordedIt() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");

        assertFalse(panel.isEmptyStateShown());
        assertEquals("raw → alpha", panel.headerTitle());
        assertEquals("Link recorded by Knowledge Catalog", panel.headerSubtitle());
        assertEquals("3 Nov 10:00", panel.valueOf("First recorded"));
        assertEquals("Today 09:00 · 1 hour ago", panel.valueOf("Last recorded"));
        assertEquals("2 in the last 30 days", panel.valueOf("Runs"));
        assertEquals(List.of("Scheduled query", "BigQuery job"), panel.recordedBy());
        assertEquals(List.of(RAW + ">" + LineageViewFixtures.ALPHA.id()), service.linkDetailsAsked);
    }

    public void testNarrowRowValuesShowTheirStartWithAnEllipsis() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
        service.freshness = new io.github.rejeb.dataform.language.lineage.dataplex.Freshness(
                NOW.minusSeconds(7_200), "Scheduled query", false);
        recorded.select(LineageViewFixtures.ALPHA.id());
        settle();

        for (String text : List.of("Today 08:00 · 2 hours ago", "p.a.alpha")) {
            TrimmedTextField value = LineageViewFixtures.all(panel, TrimmedTextField.class).stream()
                    .filter(field -> field.getText().equals(text)).findFirst().orElseThrow();
            value.setSize(value.getPreferredSize().width / 2, value.getPreferredSize().height);

            assertTrue(value.shownText(), value.shownText().startsWith(text.substring(0, 3)));
            assertTrue(value.shownText(), value.shownText().endsWith("…"));
        }
    }

    public void testANarrowLinkTitleShowsItsStartWithAnEllipsisAndTheFullNamesOnHover() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");
        TrimmedTextField title = LineageViewFixtures.all(panel, TrimmedTextField.class).stream()
                .filter(field -> field.getText().equals("raw → alpha")).findFirst().orElseThrow();

        title.setSize(title.getPreferredSize().width / 2, title.getPreferredSize().height);

        assertTrue(title.shownText(), title.shownText().startsWith("raw"));
        assertTrue(title.shownText(), title.shownText().endsWith("…"));
        assertEquals("other.landing.raw → p.a.alpha",
                title.getToolTipText(new MouseEvent(title, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false)));
    }

    public void testOpenLatestJobOpensTheJobOfTheFirstProcessThatHasOne() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");

        assertEquals(List.of("Open latest job"), texts(panel));
        perform(panel, "Open latest job");

        assertEquals(List.of(io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.RunConfigUiUtils
                .bigQueryJobUrl("p", "EU", "job_1")), opened);
    }

    public void testADeclaredLinkNamesItsFileAndRef() {
        DetailsPanel panel = linkDetails(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id(), "p.a.alpha", "p.b.beta");

        assertEquals("definitions/beta.sqlx", panel.valueOf("Declared in"));
        assertEquals("ref(\"alpha\")", panel.valueOf("Through"));
    }

    public void testALinkToAnOutsideTableSaysDataformDeclaresNothing() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");

        assertNull(panel.valueOf("Declared in"));
        assertTrue(panel.notes().contains("One of the two tables is outside this repository, so Dataform declares nothing here."));
    }

    public void testALinkBetweenRepositoryTablesThatNoRefDeclaresSaysSo() {
        DetailsPanel panel = linkDetails(LineageViewFixtures.BETA.id(), LineageViewFixtures.GAMMA.id(),
                "p.a.alpha", "p.b.beta", "p.b.beta", "p.a.gamma");

        assertTrue(panel.notes().contains("Both tables are in this repository, but no ref() declares this link."));
    }

    public void testTheLinkDetailsEndWithTheThirtyDayNote() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");

        assertEquals("Knowledge Catalog keeps lineage for 30 days, so no link can show a date older than that.",
                panel.notes().getLast());
    }

    public void testSwitchingTheSourceClearsTheSelectedLink() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");

        recorded.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAFORM);
        settle();

        assertNull(recorded.selectedLink());
        assertTrue(panel.isEmptyStateShown());
    }

    public void testWithKnowledgeCatalogTheEmptyStateNamesNodesAndLinks() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha");
        settle();

        assertEquals("Select a node or a link to see its details", panel.getEmptyText().getText());
    }

    public void testALinkOffersShowRunsWhenAToolWindowShowsThem() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha");
        java.util.List<io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab> shown = new java.util.ArrayList<>();
        panel.setTabOpener(shown::add);
        recorded.selectLink(new io.github.rejeb.dataform.language.lineage.model.LinkRef(RAW, LineageViewFixtures.ALPHA.id()));
        settle();

        assertEquals(List.of("Show runs", "Open latest job"), texts(panel));
        perform(panel, "Show runs");

        assertEquals(List.of(io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab.RUNS), shown);
    }

    public void testALinkHasNoShowRunsWithoutAToolWindow() {
        DetailsPanel panel = linkDetails(RAW, LineageViewFixtures.ALPHA.id(), "other.landing.raw", "p.a.alpha");

        assertEquals(List.of("Open latest job"), texts(panel));
    }

    public void testATableOffersTraceWhenAToolWindowShowsIt() {
        DetailsPanel panel = recordedDetails("other.landing.raw", "p.a.alpha");
        java.util.List<io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab> shown = new java.util.ArrayList<>();
        panel.setTabOpener(shown::add);

        recorded.select(LineageViewFixtures.ALPHA.id());
        settle();
        assertEquals(List.of("Open SQLX", "Run", "Focus", "Open in Knowledge Catalog", "Trace"), texts(panel));
        perform(panel, "Trace");
        recorded.select(RAW);
        settle();

        assertEquals(List.of("Open in BigQuery", "Focus", "Copy name", "Trace"), texts(panel));
        assertEquals(List.of(io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab.TRACE), shown);
    }

    public void testATableHasNoTraceWithTheDataformSource() {
        DetailsPanel panel = showDetailsOf(LineageViewFixtures.model(), LineageViewFixtures.ALPHA.id());
        panel.setTabOpener(tab -> {
        });

        assertEquals(List.of("Open SQLX", "Run", "Focus"), texts(panel));
    }
}
