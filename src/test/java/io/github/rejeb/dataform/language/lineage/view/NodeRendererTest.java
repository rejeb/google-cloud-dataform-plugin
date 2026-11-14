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

    public void testACardIsAsTallAsItsHeaderPlusItsRows() {
        BufferedImage image = new BufferedImage(W + 2 * X, H + 2 * Y + 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.MAGENTA);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        renderer.paint(g2, SHORT, new NodePosition(SHORT.id(), X, Y, 0, W), H, 72, false);
        g2.dispose();
        assertEquals(LineageTheme.nodeBackground().getRGB(), image.getRGB(X + W / 2, Y + H + 60));
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(X + W / 2, Y + H + 72 + 6));
    }

    public void testTheToggleBandUnderThePointerIsDarkenedAndTheRestOfTheHeaderIsNot() {
        renderer.setHoverToggle(SHORT.id());
        BufferedImage image = paint(false);
        int background = LineageTheme.nodeBackground().getRGB();
        assertTrue("the band is not tinted", image.getRGB(X + W / 2, Y + H - 9) != background);
        assertEquals(background, image.getRGB(X + W / 2, Y + 6));
    }

    public void testTheBandIsNotTintedWithoutThePointer() {
        BufferedImage image = paint(false);
        assertEquals(LineageTheme.nodeBackground().getRGB(), image.getRGB(X + W / 2, Y + H - 9));
    }

    private static final LineageNode OUTSIDE = LineageViewFixtures.node("other.landing.raw", "other.landing", "outside", List.of(), null);

    private BufferedImage paintNode(LineageNode node) {
        model.setGraph(LineageGraph.builder().addNode(SHORT).addNode(node).build());
        BufferedImage image = new BufferedImage(W + 2 * X, H + 2 * Y, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.MAGENTA);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        renderer.paint(g2, node, new NodePosition(node.id(), X, Y, 0, W), H, false);
        g2.dispose();
        return image;
    }

    public void testAnOutsideNodeHasADashedBorderOnTheGraphBackground() {
        BufferedImage image = paintNode(OUTSIDE);

        assertEquals(LineageTheme.graphBackground().getRGB(), image.getRGB(X + W - 30, Y + H / 2));
        java.util.Set<Integer> top = new java.util.HashSet<>();
        for (int x = X + 20; x < X + W - 20; x++) top.add(image.getRGB(x, Y));
        assertTrue("no border on the top edge", top.contains(LineageTheme.outsideBorder().getRGB()));
        assertTrue("the border is not dashed", top.size() > 1);
    }

    public void testARepositoryNodeKeepsItsSolidBorder() {
        BufferedImage image = paintNode(SHORT);

        for (int x = X + 20; x < X + W - 20; x++) {
            assertEquals(LineageTheme.nodeBorder().getRGB(), image.getRGB(x, Y));
        }
    }

    private boolean infoPainted(BufferedImage image, int background) {
        for (int x = X + W - 17; x < X + W - 5; x++) {
            for (int y = Y + 4; y < Y + 16; y++) {
                if (image.getRGB(x, y) != background) return true;
            }
        }
        return false;
    }

    public void testTheInfoIconIsPaintedOnlyWithKnowledgeCatalog() {
        int background = LineageTheme.nodeBackground().getRGB();
        assertFalse(infoPainted(paint(false), background));

        model.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);

        assertTrue(infoPainted(paint(false), background));
    }

    public void testTheInfoIconWidensTheNode() {
        int declared = renderer.measureWidth(LONG.id());

        model.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);

        assertEquals(declared + 14, renderer.measureWidth(LONG.id()));
    }

    public void testAnExpanderIsAPillOnThePanelBackgroundStraddlingTheCorner() {
        BufferedImage image = new BufferedImage(W + 60, H + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.MAGENTA);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        NodePosition pos = new NodePosition(SHORT.id(), 30, 30, 0, W);
        renderer.paintExpanders(g2, pos, H, 3, 0);
        g2.dispose();

        assertEquals(com.intellij.util.ui.UIUtil.getPanelBackground().getRGB(), image.getRGB(30 - 12 + 3, 30));
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(30 + W + 5, 30 + H + 3));
    }

    public void testACardIsWideEnoughForItsNote() {
        LineageNode source = LineageViewFixtures.node("p.s.src", "s", "table", List.of(), null);
        LineageNode outside = LineageViewFixtures.node("other.raw.orders", "raw",
                io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder.TYPE_OUTSIDE, List.of(), null);
        model.setGraph(LineageGraph.builder().addNode(source).addNode(outside).addEdge(outside.id(), source.id()).build());
        io.github.rejeb.dataform.language.columns.model.ColumnRef amount =
                new io.github.rejeb.dataform.language.columns.model.ColumnRef("p.s.src", "amount");
        model.setColumnGraph(io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph.builder()
                .addColumn(amount).build());
        model.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        model.setOutsideColumns(new io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns.Result(
                java.util.Map.of(), java.util.Map.of(), java.util.Set.of(outside.fullName())));

        model.setSearchScope(io.github.rejeb.dataform.language.lineage.model.SearchScope.COLUMNS);
        model.setSearchQuery("amount");

        String note = model.cardNote(outside.id());
        assertNotNull(note);
        int noteWidth = new JPanel().getFontMetrics(LineageTheme.columnTypeFont()).stringWidth(note);
        assertTrue(renderer.measureWidth(outside.id()) >= noteWidth + 20);
    }
}
