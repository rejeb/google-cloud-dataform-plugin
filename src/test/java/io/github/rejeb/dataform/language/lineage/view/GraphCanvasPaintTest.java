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

import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Cursor;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.analysis.Confidence;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

public class GraphCanvasPaintTest extends BasePlatformTestCase {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    public void testBackgroundIsTheEditorBackground() {
        BufferedImage image = paint(canvas(new LineageModel(null)));
        int editor = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground().getRGB();
        assertEquals(editor, image.getRGB(0, 0));
    }

    public void testBackgroundIsDotted() {
        BufferedImage image = paint(canvas(new LineageModel(null)));
        assertEquals(LineageTheme.graphDot().getRGB(), image.getRGB(9, 9));
    }

    public void testMinimapCanBeLeftOut() {
        LineageModel model = LineageViewFixtures.model();
        GraphCanvas canvas = canvas(model);
        canvas.setMinimapShown(false);
        BufferedImage image = paint(canvas);
        int editor = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground().getRGB();
        assertEquals(editor, image.getRGB(WIDTH - 20, HEIGHT - 20));
    }

    public void testFitDownShrinksAGraphWiderThanTheView() {
        LineageModel model = new LineageModel(null);
        LineageGraph.Builder builder = LineageGraph.builder();
        String previous = null;
        for (int i = 0; i < 8; i++) {
            var node = LineageViewFixtures.node("p.s.n" + i, "s", "table", java.util.List.of(), null);
            builder.addNode(node);
            if (previous != null) builder.addEdge(previous, node.id());
            previous = node.id();
        }
        model.setGraph(builder.build());
        GraphCanvas canvas = new GraphCanvas(getProject(), model);
        canvas.setFitDown(true);
        AtomicInteger zoom = new AtomicInteger(-1);
        canvas.setZoomListener(zoom::set);
        canvas.setBounds(0, 0, WIDTH, HEIGHT);
        assertTrue("zoom was " + zoom.get(), zoom.get() > 0 && zoom.get() < 100);
    }

    public void testFitDownNeverEnlargesASmallGraph() {
        GraphCanvas canvas = new GraphCanvas(getProject(), LineageViewFixtures.model());
        canvas.setFitDown(true);
        AtomicInteger zoom = new AtomicInteger(-1);
        canvas.setZoomListener(zoom::set);
        canvas.setBounds(0, 0, 2000, 1500);
        assertEquals(100, zoom.get());
    }

    public void testTheMinimapIsAPanelBoxOfGreyNodes() {
        GraphCanvas canvas = canvas(LineageViewFixtures.model());
        BufferedImage image = paint(canvas);
        int left = WIDTH - 14 - 170;
        int top = HEIGHT - 14 - 104;
        assertEquals(UIUtil.getPanelBackground().getRGB(), image.getRGB(left + 3, top + 52));
        assertEquals(JBUI.CurrentTheme.Separator.color().getRGB(), image.getRGB(left + 85, top));
    }

    public void testTheMinimapOutlinesTheVisibleAreaInsideItsBorder() {
        GraphCanvas canvas = canvas(LineageViewFixtures.model());
        BufferedImage image = paint(canvas);
        int left = WIDTH - 14 - 170;
        int top = HEIGHT - 14 - 104;
        assertEquals(LineageTheme.accentColor().getRGB(), image.getRGB(left + 1, top + 52));
        assertEquals(LineageTheme.accentColor().getRGB(), image.getRGB(left + 85, top + 1));
    }

    public void testZoomingInShrinksTheVisibleAreaOfTheMinimap() {
        GraphCanvas canvas = canvas(LineageViewFixtures.model());
        canvas.zoomBy(4);
        BufferedImage image = paint(canvas);
        int left = WIDTH - 14 - 170;
        int top = HEIGHT - 14 - 104;
        assertFalse(LineageTheme.accentColor().getRGB() == image.getRGB(left + 1, top + 52));
        long blue = java.util.stream.IntStream.range(left + 2, left + 168)
                .filter(x -> image.getRGB(x, top + 52) == LineageTheme.accentColor().getRGB()).count();
        assertTrue("blue pixels on the middle row: " + blue, blue >= 2);
    }

    public void testTheProjectCanvasFitsTheGraphUpToThePrototypeZoom() {
        GraphCanvas canvas = new GraphCanvas(getProject(), LineageViewFixtures.model());
        AtomicInteger zoom = new AtomicInteger(-1);
        canvas.setZoomListener(zoom::set);
        canvas.setBounds(0, 0, 2000, 1500);
        assertEquals(115, zoom.get());
    }

    public void testFilteringEverythingOutOffersToResetTheFilters() {
        LineageModel model = LineageViewFixtures.model();
        GraphCanvas canvas = canvas(model);
        assertFalse(canvas.isEmptyStateShown());

        model.toggleSchema("a");
        model.toggleSchema("b");

        assertTrue(canvas.isEmptyStateShown());
        assertEquals("No action matches the filters\nReset all filters", canvas.getEmptyText().toString());
    }

    public void testTheContextMenuFollowsThePrototype() {
        LineageModel model = LineageViewFixtures.model();
        GraphCanvas canvas = canvas(model);
        com.intellij.openapi.actionSystem.AnAction[] items = canvas.contextMenuFor(LineageViewFixtures.BETA.id())
                .getChildren(null);
        java.util.List<String> texts = java.util.Arrays.stream(items)
                .map(a -> a instanceof com.intellij.openapi.actionSystem.Separator ? "-" : a.getTemplatePresentation().getText())
                .toList();
        assertEquals(java.util.List.of("Open SQLX", "Run", "-", "Focus on lineage", "Copy name"), texts);
        assertEquals(LineageViewFixtures.BETA.id(), model.selectedId());
    }

    public void testShrinkingAFitDownCanvasKeepsTheGraphInView() {
        GraphCanvas canvas = new GraphCanvas(getProject(), LineageViewFixtures.model());
        canvas.setMinimapShown(false);
        canvas.setFitDown(true);
        canvas.setBounds(0, 0, 800, 1200);
        canvas.setBounds(0, 0, 800, 120);
        assertTrue("the graph left the view", showsSomething(canvas, 800, 120));
    }

    public void testShrinkingAProjectCanvasKeepsTheGraphInView() {
        GraphCanvas canvas = new GraphCanvas(getProject(), LineageViewFixtures.model());
        canvas.setMinimapShown(false);
        canvas.setBounds(0, 0, 800, 1200);
        canvas.setBounds(0, 0, 800, 120);
        assertTrue("the graph left the view", showsSomething(canvas, 800, 120));
    }

    private static boolean showsSomething(GraphCanvas canvas, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        int background = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground().getRGB();
        int dot = LineageTheme.graphDot().getRGB();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                if (rgb != background && rgb != dot) return true;
            }
        }
        return false;
    }

    private GraphCanvas canvas(LineageModel model) {
        GraphCanvas canvas = new GraphCanvas(getProject(), model);
        canvas.setBounds(0, 0, WIDTH, HEIGHT);
        return canvas;
    }

    private static BufferedImage paint(GraphCanvas canvas) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        canvas.paint(g2);
        g2.dispose();
        return image;
    }

    private static final LineageNode SRC = LineageViewFixtures.node("p.d.src", "d", "table", java.util.List.of(), null);
    private static final LineageNode SIDE = LineageViewFixtures.node("p.d.side", "d", "table", java.util.List.of(), null);
    private static final LineageNode OUT = LineageViewFixtures.node("p.d.out", "d", "table", java.util.List.of(), null);

    private LineageModel cardModel() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(SRC).addNode(SIDE).addNode(OUT)
                .addEdge(SRC.id(), OUT.id()).addEdge(SIDE.id(), OUT.id()).build());
        ColumnLineageGraph.Builder columns = ColumnLineageGraph.builder();
        for (int i = 0; i < 6; i++) columns.addColumn(new ColumnRef("p.d.src", "c" + i));
        columns.addColumn(new ColumnRef("p.d.out", "total"));
        columns.addEdge(new ColumnRef("p.d.src", "c5").id(), new ColumnRef("p.d.out", "total").id(), Confidence.DIRECT);
        model.setColumnGraph(columns.build());
        return model;
    }

    private static java.awt.Rectangle extentOf(GraphCanvas canvas, LineageModel model, String id) {
        java.awt.Rectangle extent = null;
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x += 2) {
                canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
                if (!id.equals(model.hoverId())) continue;
                if (extent == null) extent = new java.awt.Rectangle(x, y, 0, 0);
                else extent.add(x, y);
            }
        }
        assertNotNull("No point over " + id, extent);
        return extent;
    }

    private static Point pointOnBand(GraphCanvas canvas, LineageModel model, String id) {
        java.awt.Rectangle extent = extentOf(canvas, model, id);
        int x = extent.x + 4;
        for (int y = extent.y; y <= extent.y + extent.height; y++) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
            if (canvas.getCursor().getType() == Cursor.HAND_CURSOR) return new Point(x, y + 1);
        }
        throw new AssertionError("No toggle band on " + id);
    }

    private static void click(GraphCanvas canvas, Point point) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK,
                point.x, point.y, 1, false, MouseEvent.BUTTON1));
    }

    private GraphCanvas bigCanvas(LineageModel model) {
        GraphCanvas canvas = new GraphCanvas(getProject(), model);
        canvas.setBounds(0, 0, 900, 700);
        return canvas;
    }

    public void testClickingTheBandTogglesTheColumnsAndKeepsTheSelection() {
        LineageModel model = cardModel();
        GraphCanvas canvas = bigCanvas(model);
        model.select(OUT.id());

        click(canvas, pointOnBand(canvas, model, SRC.id()));
        assertTrue(model.isExpanded(SRC.id()));
        assertEquals(OUT.id(), model.selectedId());

        click(canvas, pointOnBand(canvas, model, SRC.id()));
        assertFalse(model.isExpanded(SRC.id()));
    }

    public void testClickingTheHeaderAboveTheBandSelectsTheNodeAndTogglesNothing() {
        LineageModel model = cardModel();
        GraphCanvas canvas = bigCanvas(model);
        java.awt.Rectangle extent = extentOf(canvas, model, SRC.id());

        click(canvas, new Point(extent.x + 4, extent.y + 3));

        assertEquals(SRC.id(), model.selectedId());
        assertFalse(model.isExpanded(SRC.id()));
    }

    public void testAnExpandedCardPushesTheNodeBelowIt() {
        LineageModel model = cardModel();
        GraphCanvas canvas = bigCanvas(model);
        model.toggleExpanded(SRC.id());
        java.awt.Rectangle src = extentOf(canvas, model, SRC.id());
        java.awt.Rectangle side = extentOf(canvas, model, SIDE.id());
        assertTrue("the expanded card is not taller than a plain node", src.height > 2 * side.height);
        assertTrue("the next node overlaps the expanded card",
                side.y > src.y + src.height || src.y > side.y + side.height);
    }

    public void testClickingAColumnRowSelectsThatColumn() {
        LineageModel model = cardModel();
        GraphCanvas canvas = bigCanvas(model);
        model.toggleExpanded(SRC.id());
        java.awt.Rectangle extent = extentOf(canvas, model, SRC.id());

        click(canvas, new Point(extent.x + extent.width / 2, extent.y + extent.height - 3));

        assertEquals(new ColumnRef("p.d.src", "c5").id(), model.selectedColumnId());
    }

    public void testClickingTheOnlySelectedColumnAgainClearsTheSelection() {
        LineageModel model = cardModel();
        GraphCanvas canvas = bigCanvas(model);
        model.toggleExpanded(SRC.id());
        java.awt.Rectangle extent = extentOf(canvas, model, SRC.id());
        Point lastRow = new Point(extent.x + extent.width / 2, extent.y + extent.height - 3);

        click(canvas, lastRow);
        click(canvas, lastRow);

        assertNull(model.selectedColumnId());
    }

    public void testNothingOpensOnHover() {
        LineageModel model = cardModel();
        GraphCanvas canvas = bigCanvas(model);
        java.awt.Rectangle before = extentOf(canvas, model, SRC.id());
        java.awt.Rectangle after = extentOf(canvas, model, SRC.id());
        assertEquals(before, after);
        assertFalse(model.isExpanded(SRC.id()));
    }

    public void testNoEdgeIsDrawnBetweenColumnRows() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(SRC).addNode(OUT).build());
        ColumnRef from = new ColumnRef("p.d.src", "c0");
        ColumnRef to = new ColumnRef("p.d.out", "total");
        model.setColumnGraph(ColumnLineageGraph.builder().addColumn(from).addColumn(to)
                .addEdge(from.id(), to.id(), Confidence.DIRECT).build());
        GraphCanvas canvas = bigCanvas(model);
        canvas.setMinimapShown(false);
        model.selectColumn(from.id());
        java.awt.Rectangle src = extentOf(canvas, model, SRC.id());
        java.awt.Rectangle out = extentOf(canvas, model, OUT.id());
        src.grow(3, 3);
        out.grow(3, 3);

        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        canvas.paint(g2);
        g2.dispose();

        int accent = LineageTheme.selectionColor(0).getRGB();
        boolean rootRowPainted = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != accent) continue;
                if (src.contains(x, y) || out.contains(x, y)) {
                    rootRowPainted = true;
                } else {
                    fail("lineage colour painted outside the cards at " + x + "," + y);
                }
            }
        }
        assertTrue("the selected column is not outlined in its lineage colour", rootRowPainted);
    }

    private static LineageGraph chain(int length) {
        LineageGraph.Builder builder = LineageGraph.builder();
        String previous = null;
        for (int i = 0; i < length; i++) {
            var node = LineageViewFixtures.node("p.s.n" + i, "s", "table", java.util.List.of(), null);
            builder.addNode(node);
            if (previous != null) builder.addEdge(previous, node.id());
            previous = node.id();
        }
        return builder.build();
    }

    private static String chainId(int index) {
        return LineageNode.idOf("p.s.n" + index);
    }

    public void testARestoredViewportIsAppliedToTheNextGraph() {
        LineageModel model = new LineageModel(null);
        LineageGraph graph = chain(5);
        model.setGraph(graph);
        GraphCanvas canvas = canvas(model);
        canvas.zoomBy(1.5);
        GraphCanvas.ViewportState state = canvas.viewportState();
        model.setGraph(null);

        canvas.restoreViewport(state);
        model.setGraph(graph);

        assertEquals(state, canvas.viewportState());
    }

    public void testWithoutARestoredViewportANewGraphIsRecentred() {
        LineageModel model = new LineageModel(null);
        LineageGraph graph = chain(5);
        model.setGraph(graph);
        GraphCanvas canvas = canvas(model);
        canvas.zoomBy(1.5);
        GraphCanvas.ViewportState state = canvas.viewportState();
        model.setGraph(null);

        model.setGraph(graph);

        assertFalse(state.equals(canvas.viewportState()));
    }

    public void testKeepViewHoldsTheSelectedNodeWhereItWas() {
        LineageModel model = new LineageModel(null);
        model.setGraph(chain(5));
        GraphCanvas canvas = canvas(model);
        model.select(chainId(2));
        canvas.zoomBy(1.3);
        java.awt.geom.Point2D before = canvas.screenCentreOf(chainId(2));

        canvas.keepView();
        model.setGraph(chain(6));

        java.awt.geom.Point2D after = canvas.screenCentreOf(chainId(2));
        assertEquals(before.getX(), after.getX(), 0.5);
        assertEquals(before.getY(), after.getY(), 0.5);
    }

    public void testFitToZoomsOnTheGivenNodes() {
        LineageModel model = new LineageModel(null);
        model.setGraph(chain(20));
        GraphCanvas canvas = canvas(model);
        canvas.fitToView();
        double whole = canvas.viewportState().zoom();

        canvas.fitTo(java.util.Set.of(chainId(0)));

        assertTrue("zoom " + canvas.viewportState().zoom() + " vs " + whole, canvas.viewportState().zoom() > whole);
        java.awt.geom.Point2D centre = canvas.screenCentreOf(chainId(0));
        assertEquals(WIDTH / 2.0, centre.getX(), 1.0);
    }

    public void testCenterOnPutsTheNodeInTheMiddleAtTheSameZoom() {
        LineageModel model = new LineageModel(null);
        model.setGraph(chain(20));
        GraphCanvas canvas = canvas(model);
        double zoom = canvas.viewportState().zoom();

        canvas.centerOn(chainId(15));

        java.awt.geom.Point2D centre = canvas.screenCentreOf(chainId(15));
        assertEquals(WIDTH / 2.0, centre.getX(), 1.0);
        assertEquals(HEIGHT / 2.0, centre.getY(), 1.0);
        assertEquals(zoom, canvas.viewportState().zoom(), 0.0001);
    }
}
