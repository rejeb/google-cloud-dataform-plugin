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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CanvasHitTestNodeTest {

    private static Map<String, NodePosition> positions() {
        Map<String, NodePosition> p = new LinkedHashMap<>();
        p.put("a", new NodePosition("a", 0, 0, 0, 180));
        p.put("b", new NodePosition("b", 300, 100, 1, 180));
        return p;
    }

    @Test
    void pointInsideNodeReturnsItsId() {
        assertEquals("b", CanvasHitTest.nodeAt(positions(), 44, 320, 120));
        assertEquals("a", CanvasHitTest.nodeAt(positions(), 44, 10, 10));
    }

    @Test
    void pointInEmptySpaceReturnsNull() {
        assertNull(CanvasHitTest.nodeAt(positions(), 44, 250, 250));
    }

    @Test
    void theInfoIconIsHitInTheTopRightCornerOfTheHeader() {
        assertEquals("a", CanvasHitTest.infoAt(positions(), 169, 10));
        assertEquals("b", CanvasHitTest.infoAt(positions(), 469, 110));
        assertNull(CanvasHitTest.infoAt(positions(), 100, 10));
        assertNull(CanvasHitTest.infoAt(positions(), 169, 30));
    }

    private static int hidden(String nodeId, DataplexLineageClient.Direction direction) {
        if (nodeId.equals("a")) return direction == DataplexLineageClient.Direction.UPSTREAM ? 3 : -1;
        return 0;
    }

    @Test
    void theUpstreamExpanderStraddlesTheTopLeftCornerAndTheDownstreamOneTheBottomRightOfTheCard() {
        assertEquals(new CanvasHitTest.ExpanderHit("a", DataplexLineageClient.Direction.UPSTREAM),
                CanvasHitTest.expanderAt(positions(), id -> 92, CanvasHitTestNodeTest::hidden, -5, -3));
        assertEquals(new CanvasHitTest.ExpanderHit("a", DataplexLineageClient.Direction.UPSTREAM),
                CanvasHitTest.expanderAt(positions(), id -> 92, CanvasHitTestNodeTest::hidden, 8, 6));
        assertEquals(new CanvasHitTest.ExpanderHit("a", DataplexLineageClient.Direction.DOWNSTREAM),
                CanvasHitTest.expanderAt(positions(), id -> 92, CanvasHitTestNodeTest::hidden, 185, 95));
        assertNull(CanvasHitTest.expanderAt(positions(), id -> 92, CanvasHitTestNodeTest::hidden, 185, 47));
        assertNull(CanvasHitTest.expanderAt(positions(), id -> 92, CanvasHitTestNodeTest::hidden, 90, 40));
    }

    @Test
    void aNodeWithNothingHiddenHasNoExpander() {
        assertNull(CanvasHitTest.expanderAt(positions(), id -> 44, CanvasHitTestNodeTest::hidden, 295, 98));
        assertNull(CanvasHitTest.expanderAt(positions(), id -> 44, CanvasHitTestNodeTest::hidden, 485, 146));
    }

    @Test
    void theExpanderReadsPlusNAndAPlainPlusWhenTheCountIsUnknown() {
        assertEquals("+3", NodeCardMetrics.expanderLabel(3));
        assertEquals("+", NodeCardMetrics.expanderLabel(-1));
        assertEquals(26, NodeCardMetrics.expanderBounds(positions().get("a"), 44,
                DataplexLineageClient.Direction.UPSTREAM, "+").width);
        assertEquals(new java.awt.Rectangle(-12, -9, 26, 18), NodeCardMetrics.expanderBounds(positions().get("a"), 44,
                DataplexLineageClient.Direction.UPSTREAM, "+3"));
        assertEquals(new java.awt.Rectangle(180 + 12 - 40, 92 + 9 - 18, 40, 18), NodeCardMetrics.expanderBounds(
                positions().get("a"), 92, DataplexLineageClient.Direction.DOWNSTREAM, "+120"));
    }

    @Test
    void aPointWithinSixPixelsOfAnEdgeHitsIt() {
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        path.moveTo(180, 22);
        path.lineTo(300, 22);
        Map<LinkRef, java.awt.Shape> edges = Map.of(new LinkRef("a", "b"), path);

        assertEquals(new LinkRef("a", "b"), CanvasHitTest.edgeAt(edges, 240, 27));
        assertEquals(new LinkRef("a", "b"), CanvasHitTest.edgeAt(edges, 240, 17));
        assertNull(CanvasHitTest.edgeAt(edges, 240, 30));
        assertNull(CanvasHitTest.edgeAt(edges, 320, 22));
    }
}
