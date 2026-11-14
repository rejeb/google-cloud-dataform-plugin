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
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.ui.PreviewHeader;

import javax.swing.JComponent;
import java.util.List;

public class LineageFilePanelTest extends BasePlatformTestCase {

    private static final String FILE = "definitions/analytics/orders_enriched.sqlx";
    private static final LineageNode MAIN = LineageViewFixtures.node("p.analytics.orders_enriched", "analytics", "table", List.of(), FILE);
    private static final LineageNode SOURCE = LineageViewFixtures.node("p.staging.stg_orders", "staging", "view", List.of(), "definitions/staging/stg_orders.sqlx");
    private static final LineageNode TARGET = LineageViewFixtures.node("p.analytics.daily_revenue", "analytics", "incremental", List.of(), "definitions/analytics/daily_revenue.sqlx");
    private static final LineageNode FAR = LineageViewFixtures.node("p.reporting.kpis", "reporting", "view", List.of(), "definitions/reporting/kpis.sqlx");

    private LineageFilePanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LightVirtualFile file = new LightVirtualFile("orders_enriched.sqlx") {
            @Override
            public String getPath() {
                return "/project/" + FILE;
            }
        };
        panel = new LineageFilePanel(getProject(), file);
        panel.setLineage(LineageGraph.builder()
                .addNode(SOURCE).addNode(MAIN).addNode(TARGET).addNode(FAR)
                .addEdge(SOURCE.id(), MAIN.id())
                .addEdge(MAIN.id(), TARGET.id())
                .addEdge(TARGET.id(), FAR.id())
                .build());
    }

    public void testHeaderSaysWhoseDependenciesAreShown() {
        List<PreviewHeader> headers = LineageViewFixtures.all(panel, PreviewHeader.class);
        assertEquals(1, headers.size());
        assertEquals("Direct dependencies of orders_enriched.sqlx", headers.get(0).summaryText());
    }

    public void testHeaderOffersTheLayoutDirectionAndTheFullLineage() {
        PreviewHeader header = LineageViewFixtures.all(panel, PreviewHeader.class).get(0);
        List<AnAction> actions = LineageViewFixtures.all(header, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance)
                .map(ActionToolbar.class::cast)
                .flatMap(toolbar -> List.of(toolbar.getActionGroup().getChildren(null)).stream())
                .toList();
        assertEquals(List.of("Layout direction", "Open full lineage"),
                actions.stream().map(a -> a.getTemplatePresentation().getText()).toList());
        assertEquals(Boolean.TRUE, actions.get(1).getTemplatePresentation()
                .getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
    }

    public void testOpenFullLineagePassesTheSourceAndEveryTableOfTheFile() {
        LineageNode archive = LineageViewFixtures.node("p.analytics.orders_archive", "analytics", "incremental", List.of(), FILE);
        LineageNode check = LineageViewFixtures.node("p.dataform_assertions.orders_enriched_rows", "dataform_assertions",
                "assertion", List.of(), FILE);
        panel.setLineage(LineageGraph.builder()
                .addNode(SOURCE).addNode(MAIN).addNode(archive).addNode(check)
                .addEdge(SOURCE.id(), MAIN.id())
                .addEdge(MAIN.id(), check.id())
                .build());
        List<String> opened = new java.util.ArrayList<>();
        panel.setProjectLineageOpener((source, ids) -> opened.add(source + " " + ids));
        PreviewHeader header = LineageViewFixtures.all(panel, PreviewHeader.class).get(0);
        AnAction open = LineageViewFixtures.all(header, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance)
                .map(ActionToolbar.class::cast)
                .flatMap(toolbar -> List.of(toolbar.getActionGroup().getChildren(null)).stream())
                .filter(a -> "Open full lineage".equals(a.getTemplatePresentation().getText()))
                .findFirst().orElseThrow();

        open.actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent(open));

        assertEquals(List.of("DATAFORM " + List.of(MAIN.id(), archive.id())), opened);
    }

    public void testTheActionOfTheFileIsSelected() {
        assertEquals(MAIN.id(), panel.model().selectedId());
    }

    public void testStatusCountsTheShownNodes() {
        List<String> texts = LineageViewFixtures.all(panel, JBLabel.class).stream().map(JBLabel::getText).toList();
        assertTrue(texts.toString(), texts.contains("3 nodes · double-click a node to open its file"));
    }

    public void testMinimapIsLeftOut() {
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).get(0);
        assertFalse(canvas.isMinimapShown());
    }

    private static final String COMPILED = "{\"tables\":[{\"target\":{\"database\":\"p\",\"schema\":\"analytics\",\"name\":\"orders_enriched\"},"
            + "\"enumType\":\"table\",\"fileName\":\"" + FILE + "\"}],"
            + "\"projectConfig\":{\"defaultDatabase\":\"p\",\"defaultLocation\":\"EU\"}}";
    private static final String RAW = LineageNode.idOf("landing.raw.orders");

    private DataplexViewFixtures.ScriptedService service;
    private DataplexViewFixtures.ManualExecutor executor;
    private LineageGraph declared;
    private final List<String> toolWindowCalls = new java.util.ArrayList<>();

    private void recordedPanel() {
        recordedPanel(true);
    }

    private long logged(String prefix) {
        return service.log.stream().filter(line -> line.startsWith(prefix)).count();
    }

    private static final io.github.rejeb.dataform.language.lineage.dataplex.TimeRange DAYS_30 =
            io.github.rejeb.dataform.language.lineage.dataplex.TimeRange.LAST_30_DAYS;

    private void recordedPanel(boolean shown) {
        com.intellij.testFramework.ServiceContainerUtil.replaceService(getProject(),
                io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindow.class,
                new io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindow() {
                    @Override
                    public void attach(@org.jetbrains.annotations.NotNull io.github.rejeb.dataform.language.lineage.model.LineageModel model) {
                        toolWindowCalls.add("attach");
                    }

                    @Override
                    public void detach(@org.jetbrains.annotations.NotNull io.github.rejeb.dataform.language.lineage.model.LineageModel model) {
                        toolWindowCalls.add("detach");
                    }

                    @Override
                    public void show(@org.jetbrains.annotations.Nullable io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowTab tab) {
                        toolWindowCalls.add("show");
                    }

                    @Override
                    public void hide() {
                        toolWindowCalls.add("hide");
                    }
                }, getTestRootDisposable());
        service = new DataplexViewFixtures.ScriptedService();
        executor = new DataplexViewFixtures.ManualExecutor();
        LightVirtualFile file = new LightVirtualFile("orders_enriched.sqlx") {
            @Override
            public String getPath() {
                return "/project/" + FILE;
            }
        };
        io.github.rejeb.dataform.language.compilation.model.CompiledGraph compiled = new com.google.gson.Gson().fromJson(COMPILED,
                io.github.rejeb.dataform.language.compilation.model.CompiledGraph.class);
        panel = new LineageFilePanel(getProject(), file, service, executor, () -> compiled);
        declared = LineageGraph.builder()
                .addNode(SOURCE).addNode(MAIN).addNode(TARGET).addNode(FAR)
                .addEdge(SOURCE.id(), MAIN.id())
                .addEdge(MAIN.id(), TARGET.id())
                .addEdge(TARGET.id(), FAR.id())
                .build();
        panel.setLineage(declared);
        if (shown) panel.setShown(true);
    }

    private io.github.rejeb.dataform.language.lineage.dataplex.DataplexView recorded(String... ends) {
        return DataplexViewFixtures.view(declared, MAIN.fullName(), ends);
    }

    private void settle() {
        executor.runAll();
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private GraphCanvas canvas() {
        return LineageViewFixtures.all(panel, GraphCanvas.class).getFirst();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            com.intellij.ide.util.PropertiesComponent.getInstance(getProject()).unsetValue("dataform.lineage.source");
        } finally {
            super.tearDown();
        }
    }

    public void testTheFilePanelHasTheSourceSwitchInItsHeader() {
        PreviewHeader header = LineageViewFixtures.all(panel, PreviewHeader.class).getFirst();

        List<io.github.rejeb.dataform.language.ui.SegmentedSwitch> switches =
                LineageViewFixtures.all(header, io.github.rejeb.dataform.language.ui.SegmentedSwitch.class);

        assertEquals(1, switches.size());
        assertSame(panel.sourceSwitch(), switches.getFirst());
        assertEquals(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAFORM, panel.sourceSwitch().selected());
        assertEquals(List.of("Dataform", "Knowledge Catalog"),
                LineageViewFixtures.all(panel.sourceSwitch(), javax.swing.JToggleButton.class).stream()
                        .map(javax.swing.JToggleButton::getText).toList());
    }

    public void testNoCacheMakesTheFullLoad() {
        recordedPanel();
        service.answer(recorded("landing.raw.orders", MAIN.fullName(), MAIN.fullName(), TARGET.fullName()));

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertFalse(panel.isLoading());
        assertEquals(List.of("RETAIN " + DAYS_30, "CACHED " + DAYS_30, "RELOAD " + DAYS_30), service.log);
        assertEquals(java.util.Set.of(RAW, MAIN.id(), TARGET.id()), panel.model().visibleIds());
        assertEquals("outside", panel.model().graph().node(RAW).dataformType());
        assertEquals(MAIN.id(), panel.model().selectedId());
        assertEquals("Direct recorded neighbours of orders_enriched.sqlx",
                LineageViewFixtures.all(panel, PreviewHeader.class).getFirst().summaryText());
    }

    public void testEveryActionOfTheFileAndItsDirectNeighboursAreShownFromTheFullLineage() {
        recordedPanel();
        LineageNode archive = LineageViewFixtures.node("p.analytics.orders_archive", "analytics", "incremental", List.of(), FILE);
        LineageGraph withArchive = LineageGraph.builder()
                .addNode(SOURCE).addNode(MAIN).addNode(TARGET).addNode(FAR).addNode(archive)
                .addEdge(SOURCE.id(), MAIN.id())
                .addEdge(MAIN.id(), TARGET.id())
                .addEdge(TARGET.id(), FAR.id())
                .build();
        panel.setLineage(withArchive);
        service.cached = DataplexViewFixtures.view(withArchive, MAIN.fullName(),
                "landing.raw.orders", MAIN.fullName(), MAIN.fullName(), TARGET.fullName(), TARGET.fullName(), FAR.fullName(),
                "landing.feed.archive", archive.fullName());

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertEquals(java.util.Set.of(RAW, MAIN.id(), TARGET.id(), LineageNode.idOf("landing.feed.archive"), archive.id()),
                panel.model().visibleIds());
        assertEquals(List.of("RETAIN " + DAYS_30, "CACHED " + DAYS_30, "CHECK " + DAYS_30), service.log);
        assertEquals(MAIN.id(), panel.model().selectedId());
    }

    public void testShowingTheTabChecksAndHidingItClearsAndReleases() {
        recordedPanel();
        service.cached = recorded("landing.raw.orders", MAIN.fullName());
        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();
        assertEquals(1, logged("CHECK"));

        panel.setShown(false);
        settle();

        assertTrue(panel.model().graph().isEmpty());
        assertNull(panel.model().dataplexView());
        assertEquals("RELEASE " + DAYS_30, service.log.getLast());

        panel.setShown(true);
        settle();

        assertEquals(2, logged("CHECK"));
        assertNotNull(panel.model().graph().node(RAW));
        assertEquals(0, logged("RELOAD"));
    }

    public void testATabNeverShownReadsNothing() {
        new io.github.rejeb.dataform.language.lineage.model.LineageModel(getProject())
                .setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);

        recordedPanel(false);
        settle();

        assertTrue(service.log.isEmpty());
    }

    public void testAFailedUpdateKeepsTheFileLineageAndShowsTheWarning() {
        recordedPanel();
        service.cached = recorded("landing.raw.orders", MAIN.fullName());
        service.stale = true;
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.OTHER);

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertNotNull(panel.model().graph().node(RAW));
        assertNotNull(panel.stateBanner());
        assertEquals("Could not update the Knowledge Catalog lineage: failed: OTHER", panel.stateBanner().title());
    }

    public void testOnlyTheDirectNeighboursAreShownWhateverWasLoaded() {
        recordedPanel();
        service.answer(recorded("landing.raw.orders", MAIN.fullName(), "landing.ingest.files", "landing.raw.orders"));

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertEquals(java.util.Set.of(RAW, MAIN.id()), panel.model().visibleIds());
    }

    public void testAFileWithNoRecordedLineageShowsTheEmptyStateWithShowDataformLineage() {
        recordedPanel();
        service.answer(recorded());

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertTrue(canvas().isEmptyStateShown());
        assertEquals("No lineage recorded for orders_enriched in the last 30 days", canvas().getEmptyText().getText());
        assertEquals("Show Dataform lineage",
                canvas().getEmptyText().getSecondaryComponent().getCharSequence(false).toString());
        assertNull(panel.stateBanner());
    }

    public void testAFileWithoutACompiledTableSaysToCompile() {
        recordedPanel();
        panel.setLineage(LineageGraph.builder().addNode(SOURCE).build());

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertTrue(service.log.isEmpty());
        assertEquals("No compiled table of this file to look up in Knowledge Catalog", canvas().getEmptyText().getText());
    }

    public void testAFailureShowsTheBannerOfTheProjectView() {
        recordedPanel();
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.PERMISSION_DENIED);

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        assertNotNull(panel.stateBanner());
        assertEquals("Your account cannot read lineage in p", panel.stateBanner().title());
        assertTrue(panel.model().graph().isEmpty());
    }

    public void testSwitchingBackShowsTheDeclaredDependenciesAgain() {
        recordedPanel();
        service.fail(io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind.API_DISABLED);
        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAFORM);

        assertNull(panel.stateBanner());
        assertEquals(java.util.Set.of(SOURCE.id(), MAIN.id(), TARGET.id()), panel.model().visibleIds());
        assertEquals(MAIN.id(), panel.model().selectedId());
        assertEquals("Direct dependencies of orders_enriched.sqlx",
                LineageViewFixtures.all(panel, PreviewHeader.class).getFirst().summaryText());
    }

    public void testAnAnswerThatComesAfterSwitchingBackIsDiscarded() {
        recordedPanel();
        service.answer(recorded("landing.raw.orders", MAIN.fullName()));
        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);

        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAFORM);
        settle();

        assertNull(panel.model().graph().node(RAW));
        assertFalse(panel.isLoading());
    }

    public void testANewCompilationRescopesTheShownLineageWithoutAsking() {
        recordedPanel();
        service.answer(recorded("landing.raw.orders", MAIN.fullName()));
        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();
        int asked = service.log.size();

        panel.setLineage(declared);
        settle();

        assertEquals(asked, service.log.size());
        assertNotNull(panel.model().graph().node(RAW));
        assertEquals(MAIN.id(), panel.model().selectedId());
    }

    public void testANewEntryReadByAnotherViewUpdatesTheFileLineage() {
        recordedPanel();
        service.answer(recorded("landing.raw.orders", MAIN.fullName()));
        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();
        int asked = service.log.size();

        service.announce(recorded("landing.raw.orders", MAIN.fullName(), "landing.raw.items", MAIN.fullName()));
        settle();

        assertEquals(asked, service.log.size());
        assertNotNull(panel.model().graph().node(io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("landing.raw.items")));
        assertEquals(MAIN.id(), panel.model().selectedId());
    }

    public void testTheFilePanelHasNoExpanderAndNeverShowsTheToolWindow() {
        recordedPanel();
        service.answer(DataplexViewFixtures.view(declared, false, MAIN.fullName(),
                List.of("landing.raw.orders", MAIN.fullName()), List.of("landing.ingest.files", "landing.raw.orders"),
                java.util.Set.of()));
        panel.sourceSwitch().select(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        settle();
        GraphCanvas canvas = canvas();
        canvas.setSize(900, 400);
        canvas.recenter();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(900, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        canvas.paint(g2);
        g2.dispose();

        for (int y = 0; y < canvas.getHeight(); y += 3) {
            for (int x = 0; x < canvas.getWidth(); x += 3) {
                canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_CLICKED, 0,
                        java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            }
        }
        panel.model().select(RAW);
        settle();

        assertTrue(service.expands.isEmpty());
        assertNull(panel.model().selectedLink());
        assertEquals(List.of(), toolWindowCalls);
    }

    public void testBothViewsShareTheRememberedSource() {
        io.github.rejeb.dataform.language.lineage.model.LineageModel project =
                new io.github.rejeb.dataform.language.lineage.model.LineageModel(getProject());
        project.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);

        recordedPanel();

        assertEquals(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX, panel.model().source());
        assertEquals(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX, panel.sourceSwitch().selected());
    }

    public void testARememberedKnowledgeCatalogSourceLoadsAsSoonAsTheLineageComes() {
        new io.github.rejeb.dataform.language.lineage.model.LineageModel(getProject())
                .setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        service = null;

        recordedPanel();
        service.answer(recorded("landing.raw.orders", MAIN.fullName()));
        settle();

        assertEquals(1, logged("RELOAD"));
        assertNotNull(panel.model().graph().node(RAW));
    }
}
