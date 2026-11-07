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
}
