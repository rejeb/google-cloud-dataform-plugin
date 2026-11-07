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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class NodeRendererTest extends BasePlatformTestCase {

    private static final int X = 10;
    private static final int Y = 10;
    private static final int W = 170;
    private static final int H = 52;

    private static final LineageNode SHORT = LineageViewFixtures.node("p.s.a", "s", "table", List.of(), null);
    private static final LineageNode LONG = LineageViewFixtures.node(
            "p.dataform_assertions.orders_enriched_assertions_rowConditions", "dataform_assertions",
            "table", List.of(), null);

    private LineageModel model;
    private NodeRenderer renderer;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(SHORT).addNode(LONG).build());
        renderer = new NodeRenderer(new JPanel(), model);
    }

    public void testShortNodeIsAsWideAsThePrototypeNode() {
        assertEquals(170, renderer.measureWidth(SHORT.id()));
        model.toggleDensity();
        assertEquals(170, renderer.measureWidth(SHORT.id()));
    }

    public void testLongNameWidensTheNode() {
        assertTrue(renderer.measureWidth(LONG.id()) > 170);
    }

    public void testNodeHasNoTypeStripe() {
        BufferedImage image = paint(false);
        assertEquals(LineageTheme.nodeBackground().getRGB(), image.getRGB(X + 2, Y + H / 2));
    }

    public void testSelectedNodeIsFilledWithTheSelectionBackground() {
        BufferedImage image = paint(true);
        assertEquals(UIUtil.getListSelectionBackground(true).getRGB(), image.getRGB(X + W - 12, Y + H - 8));
    }

    public void testSelectedNodeHasAnAccentBorder() {
        BufferedImage image = paint(true);
        assertEquals(LineageTheme.accentColor().getRGB(), image.getRGB(X + W / 2, Y));
    }

    public void testSelectingANodeDimsTheNodesOutsideItsLineage() {
        model.select(SHORT.id());
        assertTrue(renderer.alphaOf(LONG.id()) < 1f);
        assertEquals(1f, renderer.alphaOf(SHORT.id()));
    }

    public void testHoveringANodeDimsNoOtherNode() {
        model.setHover(LONG.id());
        assertEquals(1f, renderer.alphaOf(SHORT.id()));
    }

    public void testAnUpstreamNodeOfTheSelectionIsOutlinedInTheSearchColour() {
        LineageNode other = LineageViewFixtures.node("p.s.other", "s", "table", List.of(), null);
        model.setGraph(LineageGraph.builder().addNode(SHORT).addNode(LONG).addNode(other)
                .addEdge(SHORT.id(), LONG.id()).build());
        model.select(LONG.id());
        BufferedImage image = paint(false);
        assertEquals(LineageTheme.searchMatch().getRGB(), image.getRGB(X + W / 2, Y - 2));
    }

    public void testTheSelectedNodeHasNoOutline() {
        model.select(SHORT.id());
        BufferedImage image = paint(true);
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(X + W / 2, Y - 2));
    }

    public void testSearchDimsTheNodesThatDoNotMatch() {
        model.setSearchQuery("orders");
        assertTrue(renderer.alphaOf(SHORT.id()) < 1f);
        assertEquals(1f, renderer.alphaOf(LONG.id()));
    }

    public void testASearchMatchIsOutlinedInTheSearchColour() {
        model.setSearchQuery("s");
        BufferedImage image = paint(false);
        assertEquals(LineageTheme.searchMatch().getRGB(), image.getRGB(X + W / 2, Y - 2));
    }

    public void testANodeOutsideTheSearchHasNoOutline() {
        BufferedImage image = paint(false);
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(X + W / 2, Y - 2));
    }

    public void testTheGlyphUsesThePrototypeColoursOfTheType() {
        BufferedImage image = paint(false);
        assertEquals(LineageTheme.glyphBackground("table").getRGB(), image.getRGB(X + 11, Y + (H - 18) / 2 + 9));
    }

    public void testTheGlyphIconHasThePrototypeSizes() {
        assertEquals(18, TypeGlyphIcon.of("view").getIconWidth());
        assertEquals(16, TypeGlyphIcon.small("view").getIconWidth());
        assertEquals(16, TypeGlyphIcon.small("view").getIconHeight());
    }

    private BufferedImage paint(boolean selected) {
        BufferedImage image = new BufferedImage(W + 2 * X, H + 2 * Y, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.MAGENTA);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        renderer.paint(g2, SHORT, new NodePosition(SHORT.id(), X, Y, 0, W), H, selected);
        g2.dispose();
        return image;
    }
}
