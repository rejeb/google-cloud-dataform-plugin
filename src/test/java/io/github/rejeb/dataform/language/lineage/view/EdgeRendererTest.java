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
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EdgeRendererTest extends BasePlatformTestCase {

    private static final int NODE_H = 52;
    private static final int EDGE_Y = NODE_H / 2;
    private static final int MIDDLE_X = 215;

    private static final LineageNode A = LineageViewFixtures.node("p.s.a", "s", "table", List.of(), null);
    private static final LineageNode B = LineageViewFixtures.node("p.s.b", "s", "view", List.of(), null);

    private LineageModel model;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(A).addNode(B).addEdge(A.id(), B.id()).build());
    }

    public void testEdgeOfTheSelectedNodeIsAccented() {
        model.select(A.id());
        assertTrue(edgeColours().contains(LineageTheme.accentColor().getRGB()));
    }

    public void testAnEdgeFurtherUpstreamOfTheSelectionIsAccented() {
        LineageNode c = LineageViewFixtures.node("p.s.c", "s", "table", List.of(), null);
        model.setGraph(LineageGraph.builder().addNode(A).addNode(B).addNode(c)
                .addEdge(A.id(), B.id()).addEdge(B.id(), c.id()).build());
        model.select(c.id());
        assertTrue(edgeColours().contains(LineageTheme.accentColor().getRGB()));
    }

    public void testAnEdgeOutsideTheLineageOfTheSelectionIsDimmed() {
        LineageNode c = LineageViewFixtures.node("p.s.c", "s", "table", List.of(), null);
        model.setGraph(LineageGraph.builder().addNode(A).addNode(B).addNode(c)
                .addEdge(A.id(), B.id()).build());
        model.select(c.id());
        List<Integer> colours = edgeColours();
        assertFalse(colours.contains(LineageTheme.edgeColor().getRGB()));
        assertFalse(colours.contains(LineageTheme.accentColor().getRGB()));
    }

    public void testEdgeAwayFromTheSelectionKeepsTheEdgeColour() {
        assertTrue(edgeColours().contains(LineageTheme.edgeColor().getRGB()));
        assertFalse(edgeColours().contains(LineageTheme.accentColor().getRGB()));
    }

    public void testAnEdgeToANodeOutsideTheSearchIsDimmed() {
        model.setSearchQuery("a");
        assertFalse(edgeColours().contains(LineageTheme.edgeColor().getRGB()));
    }

    public void testHoveringANodeDimsNoEdge() {
        model.setHover(B.id());
        assertTrue(edgeColours().contains(LineageTheme.edgeColor().getRGB()));
    }

    private List<Integer> edgeColours() {
        Map<String, NodePosition> positions = new LinkedHashMap<>();
        positions.put(A.id(), new NodePosition(A.id(), 0, 0, 0, 170));
        positions.put(B.id(), new NodePosition(B.id(), 260, 0, 1, 170));
        LayoutResult layout = new LayoutResult(positions, new Rectangle2D.Double(0, 0, 430, NODE_H), 170, NODE_H);
        BufferedImage image = new BufferedImage(440, NODE_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.MAGENTA);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        new EdgeRenderer(model).paintEdges(g2, layout, Map.of());
        g2.dispose();
        return java.util.stream.IntStream.rangeClosed(EDGE_Y - 2, EDGE_Y + 2)
                .mapToObj(y -> image.getRGB(MIDDLE_X, y))
                .toList();
    }
}
