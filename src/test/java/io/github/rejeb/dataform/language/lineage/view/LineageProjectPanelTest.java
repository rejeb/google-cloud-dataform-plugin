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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.SearchScope;
import io.github.rejeb.dataform.language.ui.SegmentedSwitch;

import javax.swing.JComponent;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public class LineageProjectPanelTest extends BasePlatformTestCase {

    private LineageModel model;
    private LineageProjectPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model = LineageViewFixtures.model();
        panel = new LineageProjectPanel(getProject(), model);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private ActionToolbar toolbar() {
        return LineageViewFixtures.all(panel, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance).map(ActionToolbar.class::cast)
                .filter(t -> "DataformLineageToolbar".equals(t.getPlace()))
                .findFirst().orElseThrow();
    }

    private List<AnAction> toolbarActions() {
        return Arrays.asList(toolbar().getActionGroup().getChildren(null));
    }

    private static String describe(AnAction action) {
        if (action instanceof Separator) return "|";
        if (action instanceof CustomComponentAction) return "[component]";
        return action.getTemplatePresentation().getText();
    }

    public void testTheToolbarFollowsThePrototypeOrder() {
        assertEquals(List.of("[component]", "Show filters", "[component]", "[component]", "|",
                        "Layout direction", "Compact density", "Show minimap", "Show details", "|",
                        "Fit to view", "Recompile and refresh", "Legend"),
                toolbarActions().stream().map(LineageProjectPanelTest::describe).toList());
    }

    public void testTheToolbarHasNoTitle() {
        List<String> texts = LineageViewFixtures.all(panel, JBLabel.class).stream().map(JBLabel::getText).toList();
        assertFalse(texts.toString(), texts.contains("Lineage"));
    }

    public void testTheSearchFieldIs340PixelsWide() {
        assertEquals(com.intellij.ui.scale.JBUIScale.scale(340), panel.searchBox().getPreferredSize().width);
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(panel.searchField(), panel.searchBox()));
    }

    @SuppressWarnings("unchecked")
    private SegmentedSwitch<SearchScope> scopeSwitch() {
        return LineageViewFixtures.all(panel.searchBox(), SegmentedSwitch.class).getFirst();
    }

    public void testTheSearchFieldHoldsATablesColumnsSwitchSetToTables() {
        SegmentedSwitch<SearchScope> scope = scopeSwitch();

        assertEquals(SearchScope.TABLES, scope.selected());
        assertEquals(List.of("Tables", "Columns"), LineageViewFixtures.all(scope, javax.swing.JToggleButton.class)
                .stream().map(javax.swing.JToggleButton::getText).toList());
        assertTrue(panel.searchField().getTextEditor().getEmptyText().getText().startsWith("Search tables, tags…"));
    }

    public void testChoosingColumnsChangesThePlaceholderToSearchColumns() {
        scopeSwitch().select(SearchScope.COLUMNS);

        assertEquals(SearchScope.COLUMNS, model.searchScope());
        assertTrue(panel.searchField().getTextEditor().getEmptyText().getText().startsWith("Search columns…"));
    }

    public void testTheSwitchFollowsTheScopeOfTheModel() {
        model.setSearchScope(SearchScope.COLUMNS);

        assertEquals(SearchScope.COLUMNS, scopeSwitch().selected());
    }

    public void testTheCounterFollowsTheScope() {
        model.setColumnGraph(io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder()
                .addColumn(new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.a.alpha", "gamma_rate"))
                .addColumn(new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.a.alpha", "gamut"))
                .build());
        panel.searchField().setText("gam");
        assertEquals("1 found", panel.foundLabel().getText());

        scopeSwitch().select(SearchScope.COLUMNS);

        assertEquals("2 columns", panel.foundLabel().getText());
    }

    public void testTheSearchCountsTheMatches() {
        JBLabel found = panel.foundLabel();
        assertFalse(found.isVisible());

        panel.searchField().setText("gam");

        assertTrue(found.isVisible());
        assertEquals("1 found", found.getText());
    }

    public void testResettingTheFiltersClearsTheSearchField() {
        SearchTextField search = panel.searchField();
        search.setText("gam");

        model.resetFilters();

        assertEquals("", search.getText());
    }

    public void testTheDetailsAreShownBeforeAnythingIsSelected() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        assertTrue(details.isVisible());
        assertTrue(details.isEmptyStateShown());
    }

    public void testTheDetailsToggleHidesAndShowsTheDetails() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        ToggleAction toggle = (ToggleAction) toolbarActions().stream()
                .filter(a -> "Show details".equals(a.getTemplatePresentation().getText())).findFirst().orElseThrow();

        toggle.setSelected(TestActionEvent.createTestEvent(toggle), false);
        assertFalse(details.isVisible());

        toggle.setSelected(TestActionEvent.createTestEvent(toggle), true);
        assertTrue(details.isVisible());
    }

    public void testTheDetailsMinusButtonHidesTheDetailsWithoutASelection() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();

        LineageViewFixtures.all(details, com.intellij.ui.InplaceButton.class).getFirst().doClick();

        assertFalse(details.isVisible());
    }

    public void testTheDetailsMinusButtonHidesTheDetails() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        model.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        LineageViewFixtures.all(details, com.intellij.ui.InplaceButton.class).getFirst().doClick();

        assertFalse(details.isVisible());
    }

    public void testClickingANodeReopensTheHiddenDetails() {
        DetailsPanel details = hiddenDetails();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, pointOver(canvas, LineageViewFixtures.ALPHA.id()));

        assertTrue(details.isVisible());
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testClickingTheSelectedNodeReopensTheHiddenDetails() {
        model.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        DetailsPanel details = hiddenDetails();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, pointOver(canvas, LineageViewFixtures.ALPHA.id()));

        assertTrue(details.isVisible());
    }

    public void testClickingTheBackgroundKeepsTheDetailsHidden() {
        DetailsPanel details = hiddenDetails();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, new Point(2, 2));

        assertFalse(details.isVisible());
    }

    private DetailsPanel hiddenDetails() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        LineageViewFixtures.all(details, com.intellij.ui.InplaceButton.class).getFirst().doClick();
        assertFalse(details.isVisible());
        return details;
    }

    private GraphCanvas sizedCanvas() {
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
        canvas.setBounds(0, 0, 900, 600);
        return canvas;
    }

    private Point pointOver(GraphCanvas canvas, String id) {
        for (int y = 0; y < canvas.getHeight(); y += 5) {
            for (int x = 0; x < canvas.getWidth(); x += 5) {
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
                if (id.equals(model.hoverId())) return new Point(x, y);
            }
        }
        throw new AssertionError("No point over " + id);
    }

    private static void click(GraphCanvas canvas, Point point) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK,
                point.x, point.y, 1, false, MouseEvent.BUTTON1));
    }

    public void testTheDetailsAreTheResizableSideOfASplitter() {
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
        ThreeComponentsSplitter splitter = LineageViewFixtures.all(panel, ThreeComponentsSplitter.class).getFirst();
        model.select(LineageViewFixtures.ALPHA.id());
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        panel.setSize(1400, 700);
        layout(panel);

        assertSame(details, splitter.getLastComponent());
        assertEquals(com.intellij.ui.scale.JBUIScale.scale(300), details.getWidth());
        assertTrue(canvas.getWidth() > 700);

        splitter.setLastSize(420);
        layout(panel);
        assertEquals(420, details.getWidth());
    }

    public void testHidingTheDetailsGivesTheirSpaceToTheCanvas() {
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
        ThreeComponentsSplitter splitter = LineageViewFixtures.all(panel, ThreeComponentsSplitter.class).getFirst();
        panel.setSize(1400, 700);
        layout(panel);
        int withDetails = canvas.getWidth();

        LineageViewFixtures.all(panel, DetailsPanel.class).getFirst().setVisible(false);
        layout(panel);

        assertEquals(splitter.getWidth(), canvas.getWidth());
        assertTrue(canvas.getWidth() > withDetails);
    }

    public void testTheCanvasHasZoomControls() {
        List<String> zoom = LineageViewFixtures.all(panel, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance).map(ActionToolbar.class::cast)
                .flatMap(t -> Arrays.stream(t.getActionGroup().getChildren(null)))
                .map(a -> a.getTemplatePresentation().getText())
                .filter(t -> t != null && t.startsWith("Zoom"))
                .toList();
        assertEquals(List.of("Zoom out", "Zoom in"), zoom);
    }

    private static void layout(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component child : k.getComponents()) layout(child);
        }
    }

    private static final String COMPILED = "{\"tables\":["
            + "{\"target\":{\"database\":\"p\",\"schema\":\"a\",\"name\":\"alpha\"},\"enumType\":\"table\"},"
            + "{\"target\":{\"database\":\"p\",\"schema\":\"b\",\"name\":\"beta\"},\"enumType\":\"view\"}],"
            + "\"projectConfig\":{\"defaultDatabase\":\"p\",\"defaultLocation\":\"EU\"}}";

    private DataplexViewFixtures.ScriptedService service;
    private DataplexViewFixtures.ManualExecutor executor;
    private RecordingToolWindow toolWindow;
    private ScriptedOutsideColumns outside;

    private void dataplexPanel() {
        service = new DataplexViewFixtures.ScriptedService();
        executor = new DataplexViewFixtures.ManualExecutor();
        model = LineageViewFixtures.model();
        toolWindow = new RecordingToolWindow();
        outside = new ScriptedOutsideColumns();
        panel = new LineageProjectPanel(getProject(), model, service, executor, toolWindow, outside);
        panel.setShown(true);
        panel.showCompiled(new com.google.gson.Gson().fromJson(COMPILED,
                        io.github.rejeb.dataform.language.compilation.model.CompiledGraph.class),
                new io.github.rejeb.dataform.language.lineage.service.LineageGraphService.Graphs(model.graph(), null));
    }

    private io.github.rejeb.dataform.language.lineage.dataplex.DataplexView recorded(String... ends) {
        return DataplexViewFixtures.view(LineageViewFixtures.model().graph(), "p.a.alpha", ends);
    }

    private void settle() {
        executor.runAll();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private long logged(String prefix) {
        return service.log.stream().filter(line -> line.startsWith(prefix)).count();
    }

    private static final io.github.rejeb.dataform.language.lineage.dataplex.TimeRange DAYS_30 =
            io.github.rejeb.dataform.language.lineage.dataplex.TimeRange.LAST_30_DAYS;
    private static final io.github.rejeb.dataform.language.lineage.dataplex.TimeRange DAYS_7 =
            io.github.rejeb.dataform.language.lineage.dataplex.TimeRange.LAST_7_DAYS;

    private List<String> toolbarTexts() {
        return toolbarActions().stream().map(LineageProjectPanelTest::describe).toList();
    }

    public void testTheSourceSwitchIsTheFirstToolbarItemAndStartsOnDataform() {
        assertEquals("[component]", toolbarTexts().getFirst());
        assertEquals(LineageSource.DATAFORM, panel.sourceSwitch().selected());
        assertEquals(List.of("Dataform", "Knowledge Catalog"),
                LineageViewFixtures.all(panel.sourceSwitch(), javax.swing.JToggleButton.class).stream()
                        .map(javax.swing.JToggleButton::getText).toList());
    }

    public void testDataplexAddsReloadRangeAndDifferencesAndDropsRecompile() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertEquals(List.of("[component]", "Show filters", "[component]", "[component]", "|",
                        "Layout direction", "Compact density", "Show minimap", "Show details", "|",
                        "Fit to view", "Reload from Knowledge Catalog", "[component]", "Time range", "Differences", "Legend"),
                toolbarTexts());
    }

    public void testSwitchingToDataplexKeepsTheSelectedTableAndClearsFiltersAndColumnSelection() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        io.github.rejeb.dataform.language.columns.model.ColumnRef column =
                new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.a.alpha", "id");
        model.setColumnGraph(io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder()
                .addColumn(column).build());
        model.select(LineageViewFixtures.ALPHA.id());
        model.selectColumn(column.id());
        model.toggleTag("x");
        model.setSearchQuery("alp");

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertEquals(LineageSource.DATAPLEX, model.source());
        assertEquals(1, logged("RELOAD"));
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
        assertEquals("", model.searchQuery());
        assertTrue(model.enabledTags().isEmpty());
        assertTrue(model.selectedColumnIds().isEmpty());
        assertNotNull(model.dataplexView());
        assertEquals("outside", model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode
                .idOf("other.landing.raw")).dataformType());
    }

    public void testKnowledgeCatalogShowsTheCachedFullLineageWithoutASelection() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertEquals(List.of("RETAIN " + DAYS_30, "CACHED " + DAYS_30, "CHECK " + DAYS_30), service.log);
        assertSame(service.cached, model.dataplexView());
        assertNull(model.selectedId());
        assertNotNull(model.graph().node(RAW_ID));
        assertFalse(panel.canvas().isEmptyStateShown());
        assertNull(panel.stateBanner());
        assertFalse(panel.isLoading());
    }

    public void testAFirstLoadClearsTheGraphAndSaysItIsLoading() {
        dataplexPanel();
        List<String> seen = new java.util.ArrayList<>();
        service.answerWith(() -> {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            seen.add(panel.isLoading() + " " + model.graph().isEmpty() + " " + panel.canvas().getEmptyText().getText());
            return recorded("other.landing.raw", "p.a.alpha");
        });

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertEquals(List.of("true true Loading the Knowledge Catalog lineage…"), seen);
        assertFalse(panel.isLoading());
        assertNotNull(model.graph().node(RAW_ID));
        assertFalse(panel.canvas().isEmptyStateShown());
    }

    public void testTheCheckRunsEachTimeTheViewIsShown() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        panel.setShown(false);
        panel.setShown(true);
        settle();

        assertEquals(2, logged("CHECK"));
        assertEquals(0, logged("RELOAD"));
    }

    public void testHidingClearsTheDrawingAndReleasesTheCache() {
        recordedAroundAlpha();

        panel.setShown(false);
        settle();

        assertTrue(model.graph().isEmpty());
        assertNull(model.dataplexView());
        assertEquals("RELEASE " + DAYS_30, service.log.getLast());
        assertFalse(panel.isLoading());
    }

    public void testHidingDoesNotClearTheToolWindow() {
        recordedAroundAlpha();
        toolWindow.calls.clear();

        panel.setShown(false);
        settle();
        panel.setShown(true);
        settle();

        assertEquals(List.of(), toolWindow.calls);
    }

    public void testShowingAgainRedrawsWithTheSelectionAndViewportItHad() {
        recordedAroundAlpha();
        GraphCanvas canvas = sizedCanvas();
        model.select(RAW_ID);
        canvas.zoomBy(1.5);
        GraphCanvas.ViewportState before = canvas.viewportState();

        panel.setShown(false);
        settle();
        assertNull(model.selectedId());
        panel.setShown(true);
        settle();

        assertEquals(RAW_ID, model.selectedId());
        assertEquals(before, canvas.viewportState());
        assertNotNull(model.graph().node(RAW_ID));
    }

    public void testAnUpdateKeepsTheSelectionAndViewport() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        GraphCanvas canvas = sizedCanvas();
        canvas.recenter();
        model.select(RAW_ID);
        java.awt.geom.Point2D.Double before = canvas.screenCentreOf(RAW_ID);
        assertNotNull(before);
        service.answer(recorded("p.x.newer", "other.landing.raw", "other.landing.raw", "p.a.alpha"));

        panel.reloadDataplex();
        settle();

        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p.x.newer")));
        assertEquals(RAW_ID, model.selectedId());
        assertEquals(before, canvas.screenCentreOf(RAW_ID));
    }

    public void testANewEntryReadByAnotherViewUpdatesThisOneInPlace() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        GraphCanvas canvas = sizedCanvas();
        canvas.recenter();
        model.select(RAW_ID);
        java.awt.geom.Point2D.Double before = canvas.screenCentreOf(RAW_ID);
        long asked = logged("RELOAD");

        service.announce(recorded("p.x.newer", "other.landing.raw", "other.landing.raw", "p.a.alpha"));
        settle();

        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p.x.newer")));
        assertEquals(RAW_ID, model.selectedId());
        assertEquals(before, canvas.screenCentreOf(RAW_ID));
        assertEquals(asked, logged("RELOAD"));
    }

    public void testAReloadFinishingWhileHiddenDrawsNothingAndTheNextShowDrawsIt() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        executor.runAll();

        panel.setShown(false);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertTrue(model.graph().isEmpty());
        assertNull(model.dataplexView());
        assertNotNull(service.cached);

        panel.setShown(true);
        settle();

        assertSame(service.cached, model.dataplexView());
        assertEquals(1, logged("RELOAD"));
    }

    public void testAFailedUpdateKeepsTheGraphAndShowsTheWarning() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        service.stale = true;
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.OTHER)
                .answer(recorded("p.x.newer", "p.a.alpha"));

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertNotNull(model.dataplexView());
        assertNotNull(model.graph().node(RAW_ID));
        assertNotNull(panel.stateBanner());
        assertEquals("Could not update the Knowledge Catalog lineage: failed: OTHER", panel.stateBanner().title());
        assertEquals(com.intellij.ui.EditorNotificationPanel.Status.Warning, panel.stateBanner().status());

        panel.stateBanner().run("Retry");
        settle();

        assertNull(panel.stateBanner());
        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p.x.newer")));
    }

    public void testARecompilationWithTheSameTablesDoesNotCheckAgain() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        panel.showCompiled(new com.google.gson.Gson().fromJson(COMPILED,
                        io.github.rejeb.dataform.language.compilation.model.CompiledGraph.class),
                new io.github.rejeb.dataform.language.lineage.service.LineageGraphService.Graphs(
                        LineageViewFixtures.model().graph(), null));
        settle();

        assertEquals(1, logged("CHECK"));
    }

    public void testARecompilationThatChangesTheTablesChecksAgain() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        panel.showCompiled(new com.google.gson.Gson().fromJson(COMPILED,
                        io.github.rejeb.dataform.language.compilation.model.CompiledGraph.class),
                new io.github.rejeb.dataform.language.lineage.service.LineageGraphService.Graphs(
                        io.github.rejeb.dataform.language.lineage.graph.LineageGraph.builder()
                                .addNode(LineageViewFixtures.ALPHA).build(), null));
        settle();

        assertEquals(2, logged("CHECK"));
    }

    public void testAResultOfAnOlderRequestNeverReplacesANewerOne() {
        dataplexPanel();
        service.answer(recorded("p.x.newer", "p.a.alpha")).answer(recorded("p.x.older", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        panel.reloadDataplex();
        assertEquals(2, executor.tasks.size());

        executor.run(1);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        executor.run(0);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p.x.newer")));
        assertNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p.x.older")));
    }

    public void testAFailureWithNothingCachedShowsItsBannerAndAnEmptyGraph() {
        dataplexPanel();
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.PERMISSION_DENIED);
        model.select(LineageViewFixtures.ALPHA.id());

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertTrue(model.graph().isEmpty());
        assertNotNull(panel.stateBanner());
        assertEquals("Your account cannot read lineage in p", panel.stateBanner().title());
    }

    public void testRetryOnTheBannerLoadsAgainAndSelectsTheTableAgain() {
        dataplexPanel();
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.OTHER)
                .answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        panel.stateBanner().run("Retry");
        settle();

        assertEquals(2, logged("RELOAD"));
        assertNull(panel.stateBanner());
        assertNotNull(model.dataplexView());
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testNothingRecordedKeepsTheTablesAndShowsTheBanner() {
        dataplexPanel();
        service.answer(recorded());

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertNotNull(panel.stateBanner());
        assertEquals("No lineage recorded in the last 30 days", panel.stateBanner().title());
        assertNotNull(model.dataplexView());
        assertNotNull(model.graph().node(LineageViewFixtures.ALPHA.id()));
    }

    public void testTheSourceIsRememberedForTheProject() {
        com.intellij.ide.util.PropertiesComponent properties = com.intellij.ide.util.PropertiesComponent.getInstance(getProject());
        try {
            new LineageModel(getProject()).setSource(LineageSource.DATAPLEX);

            assertEquals("DATAPLEX", properties.getValue("dataform.lineage.source"));
            assertEquals(LineageSource.DATAPLEX, new LineageModel(getProject()).source());
        } finally {
            properties.unsetValue("dataform.lineage.source");
        }
    }

    public void testThePlaceholderInDataplexTablesScopeIsSearchTablesProjects() {
        dataplexPanel();

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertTrue(panel.searchField().getTextEditor().getEmptyText().getText().startsWith("Search tables, projects…"));
    }

    public void testATableSearchInDataplexAlsoMatchesTheProject() {
        dataplexPanel();
        service.answer(recorded("other-proj.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        model.setSearchQuery("other-proj");

        assertEquals(java.util.Set.of(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other-proj.landing.raw")),
                model.searchMatches());
    }

    public void testSwitchingBackToDataformRestoresTheRepositoryGraphAndTheSelection() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        panel.sourceSwitch().select(LineageSource.DATAFORM);
        settle();

        assertEquals(3, model.graph().nodes().size());
        assertNotNull(model.graph().node(LineageViewFixtures.GAMMA.id()));
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
        assertNull(model.dataplexView());
        assertNull(panel.stateBanner());
    }

    public void testSwitchingBackAfterAFailureSelectsTheRootAgain() {
        dataplexPanel();
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.API_DISABLED);
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        panel.stateBanner().run("Show Dataform lineage");
        settle();

        assertEquals(LineageSource.DATAFORM, model.source());
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testChangingTheRangeShowsThatRange() {
        recordedAroundAlpha();
        service.cached = null;
        service.log.clear();

        model.setTimeRange(DAYS_7);
        settle();

        assertEquals(List.of("RELEASE " + DAYS_30, "RETAIN " + DAYS_7, "CACHED " + DAYS_7, "RELOAD " + DAYS_7), service.log);
        assertEquals(DAYS_7, service.ranges.getLast());
    }

    public void testASelectionChangeInDataplexDoesNotLoadAgain() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        int asked = service.log.size();

        model.select(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.landing.raw"));
        settle();

        assertEquals(asked, service.log.size());
    }

    public void testReloadSkipsTheCheck() {
        dataplexPanel();
        service.cached = recorded("other.landing.raw", "p.a.alpha");
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        model.select(RAW_ID);
        service.log.clear();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));

        panel.reloadDataplex();
        settle();

        assertEquals(List.of("RELOAD " + DAYS_30), service.log);
        assertEquals(RAW_ID, model.selectedId());
    }

    private Point expanderPoint(GraphCanvas canvas) {
        for (int y = 0; y < canvas.getHeight(); y += 3) {
            for (int x = 0; x < canvas.getWidth(); x += 3) {
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
                if (model.hoverId() == null && canvas.getCursor().getType() == java.awt.Cursor.HAND_CURSOR) return new Point(x, y);
            }
        }
        throw new AssertionError("No expander");
    }

    private io.github.rejeb.dataform.language.lineage.dataplex.DataplexView withHiddenUpstream() {
        return DataplexViewFixtures.view(LineageViewFixtures.model().graph(), false, "p.a.alpha",
                List.of("other.landing.raw", "p.a.alpha"), List.of("other.ingest.files", "other.landing.raw"), java.util.Set.of());
    }

    public void testAClickOnAnExpanderLoadsOneMoreLevelOnThatSideAndKeepsTheSelection() {
        dataplexPanel();
        service.answer(withHiddenUpstream());
        service.expanded = recorded("other.landing.raw", "p.a.alpha", "other.ingest.files", "other.landing.raw");
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, expanderPoint(canvas));
        settle();

        assertEquals(List.of("UPSTREAM " + io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.landing.raw")),
                service.expands);
        assertSame(service.expanded, model.dataplexView());
        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.ingest.files")));
        assertEquals(LineageViewFixtures.ALPHA.id(), model.selectedId());
    }

    public void testAnExpansionAnsweredAfterAReloadIsDropped() {
        dataplexPanel();
        service.answer(withHiddenUpstream()).answer(recorded("p.x.newer", "p.a.alpha"));
        service.expanded = recorded("other.landing.raw", "p.a.alpha", "other.ingest.files", "other.landing.raw");
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        GraphCanvas canvas = sizedCanvas();
        click(canvas, expanderPoint(canvas));

        panel.reloadDataplex();
        settle();

        assertNotSame(service.expanded, model.dataplexView());
        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p.x.newer")));
    }

    public void testTheDataformSourceHasNoExpander() {
        GraphCanvas canvas = sizedCanvas();
        try {
            expanderPoint(canvas);
            fail("an expander was found");
        } catch (AssertionError expected) {
            assertEquals("No expander", expected.getMessage());
        }
    }

    public void testDoubleClickingAnOutsideTableOpensItInBigQuery() {
        dataplexPanel();
        service.answer(recorded("p2.sales.orders", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        List<String> opened = new java.util.ArrayList<>();
        GraphCanvas canvas = sizedCanvas();
        canvas.setUrlOpener(opened::add);
        Point point = pointOver(canvas, io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("p2.sales.orders"));

        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK,
                point.x, point.y, 2, false, MouseEvent.BUTTON1));

        assertEquals(List.of(io.github.rejeb.dataform.language.gcp.common.BigQueryConsoleUrls.tableUrl("p2.sales.orders")), opened);
    }

    private Point pointOnAnEdge(GraphCanvas canvas) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(canvas.getWidth(), canvas.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        for (int y = 0; y < canvas.getHeight(); y += 3) {
            for (int x = 0; x < canvas.getWidth(); x += 3) {
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
                if (model.hoverId() == null && canvas.getCursor().getType() == java.awt.Cursor.HAND_CURSOR) return new Point(x, y);
            }
        }
        throw new AssertionError("No edge");
    }

    public void testAClickOnAnEdgeSelectsTheLinkWithKnowledgeCatalog() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, pointOnAnEdge(canvas));

        assertEquals(new io.github.rejeb.dataform.language.lineage.model.LinkRef(
                io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.landing.raw"),
                LineageViewFixtures.ALPHA.id()), model.selectedLink());
        assertNull(model.selectedId());
    }

    public void testAnEdgeCannotBeSelectedWithTheDataformSource() {
        GraphCanvas canvas = sizedCanvas();
        try {
            pointOnAnEdge(canvas);
            fail("an edge was found");
        } catch (AssertionError expected) {
            assertEquals("No edge", expected.getMessage());
        }
    }

    private static final String RAW_ID = io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.landing.raw");

    private void recordedAroundAlpha() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
    }

    public void testThePanelAttachesItsModelToTheToolWindowAndDetachesItWhenDisposed() {
        dataplexPanel();
        assertEquals(List.of("attach"), toolWindow.calls);
        assertSame(model, toolWindow.attached);

        com.intellij.openapi.util.Disposer.dispose(panel);

        assertEquals(List.of("attach", "detach"), toolWindow.calls);
    }

    public void testSelectingATableInDataplexModeShowsTheToolWindowOnItsCurrentTab() {
        recordedAroundAlpha();
        toolWindow.calls.clear();

        model.select(RAW_ID);

        assertEquals(List.of("show null"), toolWindow.calls);
    }

    public void testSelectingATableInDataformModeNeverShowsIt() {
        dataplexPanel();

        model.select(LineageViewFixtures.ALPHA.id());
        model.select(LineageViewFixtures.BETA.id());

        assertEquals(List.of("attach"), toolWindow.calls);
    }

    public void testSwitchingToDataplexWithASelectedTableShowsIt() {
        dataplexPanel();
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());

        panel.sourceSwitch().select(LineageSource.DATAPLEX);

        assertEquals(List.of("attach", "show null"), toolWindow.calls);
        settle();
        assertEquals(List.of("attach", "show null"), toolWindow.calls);
    }

    public void testSwitchingToDataplexWithoutASelectionDoesNotShowIt() {
        dataplexPanel();

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertEquals(List.of("attach"), toolWindow.calls);
    }

    public void testSwitchingBackToDataformHidesIt() {
        recordedAroundAlpha();
        toolWindow.calls.clear();

        panel.sourceSwitch().select(LineageSource.DATAFORM);

        assertEquals(List.of("hide"), toolWindow.calls);
    }

    public void testASelectionAfterTheUserHidItShowsItAgain() {
        recordedAroundAlpha();
        toolWindow.calls.clear();

        model.select(RAW_ID);
        model.select(null);
        model.select(RAW_ID);

        assertEquals(List.of("show null", "show null"), toolWindow.calls);
    }

    public void testAChangeThatKeepsTheSelectionDoesNotShowItAgain() {
        recordedAroundAlpha();
        toolWindow.calls.clear();

        model.setHover(RAW_ID);
        model.toggleDensity();

        assertEquals(List.of(), toolWindow.calls);
    }

    public void testShowRunsOnALinkShowsTheRunsTab() {
        recordedAroundAlpha();
        model.selectLink(new io.github.rejeb.dataform.language.lineage.model.LinkRef(RAW_ID, LineageViewFixtures.ALPHA.id()));
        settle();
        toolWindow.calls.clear();
        DetailsPanel details = LineageViewFixtures.all(panel, DetailsPanel.class).getFirst();
        com.intellij.openapi.actionSystem.DefaultActionGroup group =
                (com.intellij.openapi.actionSystem.DefaultActionGroup) details.toolbar().getActionGroup();
        AnAction showRuns = Arrays.stream(group.getChildActionsOrStubs())
                .filter(a -> "Show runs".equals(a.getTemplatePresentation().getText())).findFirst().orElseThrow();

        showRuns.actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent(showRuns));

        assertEquals(List.of("show RUNS"), toolWindow.calls);
    }

    public void testRevealOfATableNotOnTheGraphIncludesIt() {
        recordedAroundAlpha();
        String files = io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.ingest.files");
        service.included = recorded("other.ingest.files", "other.landing.raw", "other.landing.raw", "p.a.alpha");
        sizedCanvas();

        model.requestNode(files);
        assertTrue(panel.isLoading());
        settle();

        assertEquals("INCLUDE " + files, service.log.getLast());
        assertSame(service.included, model.dataplexView());
        assertEquals(files, model.selectedId());
        assertFalse(panel.isLoading());
    }

    public void testRevealOfATableOnTheGraphSelectsAndCentresIt() {
        recordedAroundAlpha();
        GraphCanvas canvas = sizedCanvas();
        canvas.recenter();
        int asked = service.log.size();

        model.requestNode(RAW_ID);

        assertEquals(RAW_ID, model.selectedId());
        java.awt.geom.Point2D.Double centre = canvas.screenCentreOf(RAW_ID);
        assertEquals(450, centre.x, 0.5);
        assertEquals(300, centre.y, 0.5);
        assertEquals(asked, service.log.size());
    }

    private io.github.rejeb.dataform.language.lineage.dataplex.DataplexView recordedAlphaAndBeta() {
        return recorded("other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
    }

    public void testShowNodesWithOneTableSelectsIt() {
        dataplexPanel();
        service.cached = recordedAlphaAndBeta();
        GraphCanvas canvas = sizedCanvas();

        panel.showNodes(LineageSource.DATAPLEX, List.of(LineageViewFixtures.BETA.id()));
        settle();

        assertEquals(LineageSource.DATAPLEX, model.source());
        assertEquals(LineageViewFixtures.BETA.id(), model.selectedId());
        assertTrue(model.markedIds().isEmpty());
        java.awt.geom.Point2D.Double centre = canvas.screenCentreOf(LineageViewFixtures.BETA.id());
        assertEquals(450, centre.x, 0.5);
        assertEquals(300, centre.y, 0.5);
    }

    public void testShowNodesWithSeveralTablesMarksThemWithoutSelecting() {
        dataplexPanel();
        service.cached = recordedAlphaAndBeta();
        model.select(LineageViewFixtures.ALPHA.id());

        panel.showNodes(LineageSource.DATAPLEX, List.of(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id()));
        settle();

        assertNull(model.selectedId());
        assertEquals(java.util.Set.of(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id()), model.markedIds());
        assertEquals(java.util.Set.of(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id()), model.highlightedIds());
        assertTrue(model.dimmedIds().isEmpty());
    }

    public void testShowNodesBeforeTheGraphArrivesAppliesWhenItDoes() {
        dataplexPanel();
        service.answer(recordedAlphaAndBeta());

        panel.showNodes(LineageSource.DATAPLEX, List.of(LineageViewFixtures.ALPHA.id()));
        panel.showNodes(null, List.of(LineageViewFixtures.BETA.id()));
        assertNull(model.selectedId());
        settle();

        assertEquals(LineageViewFixtures.BETA.id(), model.selectedId());
        assertNotNull(model.dataplexView());
    }

    public void testShowNodesInDataformUsesTheSameRule() {
        dataplexPanel();

        panel.showNodes(LineageSource.DATAFORM, List.of(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id()));
        assertNull(model.selectedId());
        assertEquals(java.util.Set.of(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id()), model.markedIds());

        panel.showNodes(null, List.of(LineageViewFixtures.GAMMA.id()));
        assertEquals(LineageViewFixtures.GAMMA.id(), model.selectedId());
        assertTrue(model.markedIds().isEmpty());

        model.select(null);
        panel.showNodes(null, List.of(LineageViewFixtures.ALPHA.id(), LineageViewFixtures.BETA.id()));
        panel.showNodes(null, List.of());
        assertTrue(model.markedIds().isEmpty());
        assertNull(model.selectedId());
        assertTrue(service.log.isEmpty());
    }

    public void testARequestedNodeIsIgnoredWithTheDataformSource() {
        dataplexPanel();

        model.requestNode(RAW_ID);
        settle();

        assertTrue(service.log.isEmpty());
        assertFalse(panel.isLoading());
    }

    private AnAction differencesToggle() {
        return toolbarActions().stream().filter(a -> a.getTemplatePresentation().getText() != null
                && a.getTemplatePresentation().getText().startsWith("Differences")).findFirst().orElseThrow();
    }

    private String toggleText() {
        AnAction toggle = differencesToggle();
        com.intellij.openapi.actionSystem.AnActionEvent event = com.intellij.testFramework.TestActionEvent.createTestEvent(toggle);
        toggle.update(event);
        return event.getPresentation().getText();
    }

    private void recordedWithAnUndeclaredSourceAndAMissingLink() {
        dataplexPanel();
        service.answer(DataplexViewFixtures.view(model.graph(), "p.b.beta", "other.landing.raw", "p.b.beta"));
        model.select(LineageViewFixtures.BETA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
    }

    public void testTheToggleCarriesTheCountAndShowsTheDifferencesTabOfTheToolWindow() {
        recordedWithAnUndeclaredSourceAndAMissingLink();
        assertEquals(2, model.differences().size());
        assertEquals("Differences (2)", toggleText());
        toolWindow.calls.clear();

        ((com.intellij.openapi.actionSystem.ToggleAction) differencesToggle()).setSelected(
                com.intellij.testFramework.TestActionEvent.createTestEvent(differencesToggle()), true);

        assertTrue(model.differencesShown());
        assertEquals(List.of("show DIFFERENCES"), toolWindow.calls);
    }

    public void testWithDifferencesOnTheDeclaredOnlyTableAndEdgeJoinTheGraph() {
        recordedWithAnUndeclaredSourceAndAMissingLink();
        assertNull(model.graph().node(LineageViewFixtures.ALPHA.id()));

        model.setDifferencesShown(true);

        assertNotNull(model.graph().node(LineageViewFixtures.ALPHA.id()));
        assertTrue(model.graph().predecessors(LineageViewFixtures.BETA.id()).contains(LineageViewFixtures.ALPHA.id()));
        assertEquals(LineageViewFixtures.BETA.id(), model.selectedId());

        model.setDifferencesShown(false);

        assertNull(model.graph().node(LineageViewFixtures.ALPHA.id()));
    }

    public void testAgreeingGraphsLeaveTheToggleWithoutCount() {
        dataplexPanel();
        service.answer(recorded("p.a.alpha", "p.b.beta"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();

        assertTrue(model.differences().isEmpty());
        assertEquals("Differences", toggleText());
    }

    public void testANewRecordedLineageKeepsTheDifferencesOn() {
        dataplexPanel();
        service.answer(DataplexViewFixtures.view(model.graph(), "p.b.beta", "other.landing.raw", "p.b.beta"))
                .answer(DataplexViewFixtures.view(LineageViewFixtures.model().graph(), "p.b.beta", "other.landing.files", "p.b.beta"));
        model.select(LineageViewFixtures.BETA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        model.setDifferencesShown(true);

        panel.reloadDataplex();
        settle();

        assertTrue(model.differencesShown());
        assertNotNull(model.graph().node(LineageViewFixtures.ALPHA.id()));
        assertNotNull(model.graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.landing.files")));
    }

    private static final class ScriptedOutsideColumns
            implements io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns {
        private final List<List<String>> asked = new java.util.ArrayList<>();
        private final java.util.Set<String> unreadable = new java.util.HashSet<>();

        @Override
        public @org.jetbrains.annotations.NotNull Result load(@org.jetbrains.annotations.NotNull String projectId,
                                                               @org.jetbrains.annotations.NotNull java.util.Collection<String> fullNames) {
            asked.add(fullNames.stream().sorted().toList());
            java.util.Map<String, List<io.github.rejeb.dataform.language.columns.model.ColumnRef>> columns = new java.util.LinkedHashMap<>();
            java.util.Set<String> denied = new java.util.LinkedHashSet<>();
            for (String name : fullNames) {
                if (unreadable.contains(name)) denied.add(name);
                else columns.put(name, List.of(new io.github.rejeb.dataform.language.columns.model.ColumnRef(name, "amount_eur")));
            }
            return new Result(columns, java.util.Map.of(), denied);
        }
    }

    private void recordedWithOutsideSources(String... names) {
        dataplexPanel();
        String[] ends = new String[names.length * 2];
        for (int i = 0; i < names.length; i++) {
            ends[i * 2] = names[i];
            ends[i * 2 + 1] = "p.a.alpha";
        }
        service.answer(recorded(ends));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
    }

    public void testTheColumnsOfOutsideTablesAreReadTheFirstTimeTheColumnsScopeIsChosen() {
        recordedWithOutsideSources("other.landing.raw", "other.landing.files");
        assertTrue(outside.asked.isEmpty());

        model.setSearchScope(SearchScope.COLUMNS);
        assertTrue(outside.asked.isEmpty());
        settle();

        assertEquals(List.of(List.of("other.landing.files", "other.landing.raw")), outside.asked);
        model.setSearchQuery("amount");
        assertEquals(2, model.foundColumnIds().size());

        model.setSearchScope(SearchScope.TABLES);
        model.setSearchScope(SearchScope.COLUMNS);
        settle();
        assertEquals(1, outside.asked.size());
    }

    public void testTheColumnsScopeReadsNothingWithTheDataformSource() {
        dataplexPanel();

        model.setSearchScope(SearchScope.COLUMNS);
        settle();

        assertTrue(outside.asked.isEmpty());
    }

    public void testChoosingKnowledgeCatalogWithTheColumnsScopeAlreadyOnReadsTheOutsideColumns() {
        dataplexPanel();
        model.setSearchScope(SearchScope.COLUMNS);
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.select(LineageViewFixtures.ALPHA.id());

        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        settle();

        assertEquals(List.of(List.of("other.landing.raw")), outside.asked);
    }

    public void testAnExpansionReadsTheColumnsOfTheNewOutsideTablesOnly() {
        dataplexPanel();
        service.answer(withHiddenUpstream());
        service.expanded = recorded("other.landing.raw", "p.a.alpha", "other.ingest.files", "other.landing.raw");
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
        model.setSearchScope(SearchScope.COLUMNS);
        settle();
        GraphCanvas canvas = sizedCanvas();

        click(canvas, expanderPoint(canvas));
        settle();
        settle();

        assertEquals(List.of(List.of("other.landing.raw"), List.of("other.ingest.files")), outside.asked);
        model.setSearchQuery("amount");
        assertEquals(2, model.foundColumnIds().size());
    }

    public void testAReloadReadsTheOutsideColumnsAgain() {
        recordedWithOutsideSources("other.landing.raw");
        service.answer(recorded("other.landing.raw", "p.a.alpha"));
        model.setSearchScope(SearchScope.COLUMNS);
        settle();

        panel.reloadDataplex();
        settle();
        settle();

        assertEquals(2, outside.asked.size());
    }

    public void testHidingDropsTheOutsideColumnsAndShowingReadsThemAgain() {
        recordedWithOutsideSources("other.landing.raw");
        model.setSearchScope(SearchScope.COLUMNS);
        settle();
        assertEquals(1, outside.asked.size());

        panel.setShown(false);
        settle();
        assertEquals(io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns.Result.NONE,
                model.outsideColumns());

        panel.setShown(true);
        settle();
        settle();
        assertEquals(2, outside.asked.size());
        model.setSearchQuery("amount");
        assertEquals(1, model.foundColumnIds().size());
    }

    public void testOutsideColumnsReadWhileTheViewIsHiddenAreDropped() {
        recordedWithOutsideSources("other.landing.raw");
        model.setSearchScope(SearchScope.COLUMNS);

        panel.setShown(false);
        settle();

        assertEquals(io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns.Result.NONE,
                model.outsideColumns());
    }

    public void testAnUnreadableOutsideTableIsCountedNextToTheSearch() {
        recordedWithOutsideSources("other.landing.raw");
        outside.unreadable.add("other.landing.raw");
        model.setSearchScope(SearchScope.COLUMNS);
        settle();

        model.setSearchQuery("amount");

        assertEquals("0 columns · 1 table not searched", SearchCounter.text(model));
    }

    private static final io.github.rejeb.dataform.language.columns.model.ColumnRef ALPHA_ID =
            new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.a.alpha", "id");
    private static final io.github.rejeb.dataform.language.columns.model.ColumnRef BETA_ID =
            new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.b.beta", "id");
    private static final io.github.rejeb.dataform.language.columns.model.ColumnRef BETA_TOTAL =
            new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.b.beta", "total");

    private io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph dataformColumns;

    private void recordedWithColumns() {
        dataplexPanel();
        dataformColumns = io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder()
                .addColumn(ALPHA_ID).addColumn(BETA_ID).addColumn(BETA_TOTAL)
                .addEdge(ALPHA_ID.id(), BETA_TOTAL.id(), io.github.rejeb.dataform.language.columns.analysis.Confidence.DERIVED)
                .build();
        panel.showCompiled(new com.google.gson.Gson().fromJson(COMPILED,
                        io.github.rejeb.dataform.language.compilation.model.CompiledGraph.class),
                new io.github.rejeb.dataform.language.lineage.service.LineageGraphService.Graphs(model.graph(), dataformColumns));
        service.answer(recorded("other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta"));
        model.select(LineageViewFixtures.ALPHA.id());
        panel.sourceSwitch().select(LineageSource.DATAPLEX);
        settle();
    }

    private void recordedColumnLink(io.github.rejeb.dataform.language.columns.model.ColumnRef from,
                                    io.github.rejeb.dataform.language.columns.model.ColumnRef to, String reason) {
        service.columnLineage = new io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage.Result(
                io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder()
                        .addColumn(from).addColumn(to)
                        .addEdge(from.id(), to.id(), io.github.rejeb.dataform.language.columns.analysis.Confidence.DIRECT)
                        .build(), reason);
    }

    public void testWithKnowledgeCatalogTheKnownColumnsLoseTheirDataformEdges() {
        recordedWithColumns();

        assertNotNull(model.columnGraph());
        assertEquals(3, model.columnGraph().columns().size());
        assertTrue(model.columnGraph().edges().isEmpty());
        assertTrue(service.columnRootsAsked.isEmpty());
    }

    public void testTheColumnLineageOfTheRootsIsReadFromKnowledgeCatalog() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);

        model.selectColumn(ALPHA_ID.id());
        assertTrue(model.columnGraph().edges().isEmpty());
        settle();

        assertEquals(List.of(List.of(ALPHA_ID.id())), service.columnRootsAsked);
        assertEquals(java.util.Set.of(ALPHA_ID.id(), BETA_ID.id()), model.highlightColumnLineage());
        assertEquals(List.of(ALPHA_ID.id()), List.copyOf(model.selectedColumnIds()));
        assertNull(panel.columnLineageReason());
    }

    public void testTheSameRootsAreNotAskedAgain() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);
        model.selectColumn(ALPHA_ID.id());
        settle();

        model.select(LineageViewFixtures.BETA.id());
        model.toggleExpanded(LineageViewFixtures.BETA.id());
        settle();

        assertEquals(1, service.columnRootsAsked.size());
    }

    public void testOtherRootsReplaceTheShownColumnLineage() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);
        model.selectColumn(ALPHA_ID.id());
        settle();
        service.columnLineage = new io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage.Result(
                io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder().build(), null);

        model.selectColumn(BETA_TOTAL.id());
        assertTrue(model.columnGraph().edges().isEmpty());
        settle();

        assertEquals(List.of(List.of(ALPHA_ID.id()), List.of(BETA_TOTAL.id())), service.columnRootsAsked);
        assertEquals(java.util.Set.of(BETA_TOTAL.id()), model.highlightColumnLineage());
    }

    public void testAnAnswerForRootsThatAreNoLongerShownIsDropped() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);
        model.selectColumn(ALPHA_ID.id());

        model.clearColumnSelection();
        settle();

        assertTrue(model.columnGraph().edges().isEmpty());
    }

    public void testAColumnSearchReadsTheLineageOfTheFoundColumns() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);

        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("total");
        settle();

        assertEquals(List.of(BETA_TOTAL.id()), service.columnRootsAsked.getLast());
    }

    public void testWhyTheColumnLineageCannotBeReadIsShownAndLeavesWithTheRoots() {
        recordedWithColumns();
        service.columnLineage = new io.github.rejeb.dataform.language.lineage.dataplex.DataplexColumnLineage.Result(
                io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder().build(),
                "Your account cannot read column lineage in p: it needs the permission datalineage.events.getFields");

        model.selectColumn(ALPHA_ID.id());
        settle();

        assertEquals("Your account cannot read column lineage in p: it needs the permission datalineage.events.getFields",
                panel.columnLineageReason());

        model.clearColumnSelection();
        assertNull(panel.columnLineageReason());
    }

    public void testAFailingColumnLineageReadIsShownAsTheReason() {
        recordedWithColumns();
        service.columnLineage = null;

        model.selectColumn(ALPHA_ID.id());
        settle();

        assertNotNull(panel.columnLineageReason());
        assertTrue(model.columnGraph().edges().isEmpty());
    }

    public void testGoingBackToDataformRestoresItsColumnLineage() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);
        model.selectColumn(ALPHA_ID.id());
        settle();

        panel.sourceSwitch().select(LineageSource.DATAFORM);

        assertSame(dataformColumns, model.columnGraph());
        assertNull(panel.columnLineageReason());
    }

    public void testTheColumnsOfOutsideTablesCanBeRootsOfTheRecordedColumnLineage() {
        recordedWithColumns();
        model.setSearchScope(SearchScope.COLUMNS);
        settle();
        io.github.rejeb.dataform.language.columns.model.ColumnRef rawAmount =
                new io.github.rejeb.dataform.language.columns.model.ColumnRef("other.landing.raw", "amount_eur");
        recordedColumnLink(rawAmount, ALPHA_ID, null);
        model.setSearchScope(SearchScope.TABLES);

        model.selectColumn(rawAmount.id());
        settle();

        assertEquals(List.of(rawAmount.id()), service.columnRootsAsked.getLast());
        assertEquals(java.util.Set.of(rawAmount.id(), ALPHA_ID.id()), model.highlightColumnLineage());
    }

    public void testARecompilationInKnowledgeCatalogKeepsTheRecordedColumnLineage() {
        recordedWithColumns();
        recordedColumnLink(ALPHA_ID, BETA_ID, null);
        model.selectColumn(ALPHA_ID.id());
        settle();

        panel.showCompiled(new com.google.gson.Gson().fromJson(COMPILED,
                        io.github.rejeb.dataform.language.compilation.model.CompiledGraph.class),
                new io.github.rejeb.dataform.language.lineage.service.LineageGraphService.Graphs(
                        LineageViewFixtures.model().graph(), dataformColumns));
        settle();

        assertEquals(1, model.columnGraph().edges().size());
        assertEquals(java.util.Set.of(ALPHA_ID.id(), BETA_ID.id()), model.highlightColumnLineage());
    }

    private static final class RecordingToolWindow implements io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindow {
        private final List<String> calls = new java.util.ArrayList<>();
        private LineageModel attached;

        @Override
        public void attach(@org.jetbrains.annotations.NotNull LineageModel model) {
            attached = model;
            calls.add("attach");
        }

        @Override
        public void detach(@org.jetbrains.annotations.NotNull LineageModel model) {
            calls.add("detach");
        }

        @Override
        public void show(@org.jetbrains.annotations.Nullable io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab tab) {
            calls.add("show " + tab);
        }

        @Override
        public void hide() {
            calls.add("hide");
        }
    }
}
