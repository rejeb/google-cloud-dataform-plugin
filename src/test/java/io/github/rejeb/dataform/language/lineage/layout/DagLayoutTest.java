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
package io.github.rejeb.dataform.language.lineage.layout;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagLayoutTest {

    private static LineageNode node(String full) {
        String name = full.substring(full.lastIndexOf('.') + 1);
        return new LineageNode(LineageNode.idOf(full), name, full, "s", "table", List.of(), null);
    }

    /** A -> B -> C, plus isolated D. */
    private static LineageGraph chain() {
        LineageNode a = node("p.s.a");
        LineageNode b = node("p.s.b");
        LineageNode c = node("p.s.c");
        LineageNode d = node("p.s.d");
        return LineageGraph.builder()
                .addNode(a).addNode(b).addNode(c).addNode(d)
                .addEdge(a.id(), b.id())
                .addEdge(b.id(), c.id())
                .build();
    }

    @Test
    void chainLayersIncreaseLeftToRight() {
        LineageGraph g = chain();
        LayoutResult r = DagLayout.compute(g, Set.of(
                LineageNode.idOf("p.s.a"), LineageNode.idOf("p.s.b"),
                LineageNode.idOf("p.s.c"), LineageNode.idOf("p.s.d")),
                Direction.LR, Density.COMFORTABLE);

        assertEquals(0, r.positions().get(LineageNode.idOf("p.s.a")).layer());
        assertEquals(1, r.positions().get(LineageNode.idOf("p.s.b")).layer());
        assertEquals(2, r.positions().get(LineageNode.idOf("p.s.c")).layer());
        assertEquals(0, r.positions().get(LineageNode.idOf("p.s.d")).layer(), "isolated node at layer 0");

        double xa = r.positions().get(LineageNode.idOf("p.s.a")).x();
        double xb = r.positions().get(LineageNode.idOf("p.s.b")).x();
        double xc = r.positions().get(LineageNode.idOf("p.s.c")).x();
        assertTrue(xa < xb && xb < xc, "x increases with layer in LR");
    }

    @Test
    void tbSwapsAxesSoLayersIncreaseTopToBottom() {
        LineageGraph g = chain();
        LayoutResult r = DagLayout.compute(g, Set.of(
                LineageNode.idOf("p.s.a"), LineageNode.idOf("p.s.b"), LineageNode.idOf("p.s.c")),
                Direction.TB, Density.COMFORTABLE);

        double ya = r.positions().get(LineageNode.idOf("p.s.a")).y();
        double yb = r.positions().get(LineageNode.idOf("p.s.b")).y();
        double yc = r.positions().get(LineageNode.idOf("p.s.c")).y();
        assertTrue(ya < yb && yb < yc, "y increases with layer in TB");
    }

    @Test
    void boundsEncloseAllNodes() {
        LineageGraph g = chain();
        LayoutResult r = DagLayout.compute(g, Set.of(
                LineageNode.idOf("p.s.a"), LineageNode.idOf("p.s.b"),
                LineageNode.idOf("p.s.c"), LineageNode.idOf("p.s.d")),
                Direction.LR, Density.COMFORTABLE);
        for (NodePosition p : r.positions().values()) {
            assertTrue(p.x() >= r.bounds().x - 0.001);
            assertTrue(p.y() >= r.bounds().y - 0.001);
            assertTrue(p.x() + r.nodeW() <= r.bounds().x + r.bounds().width + 0.001);
            assertTrue(p.y() + r.nodeH() <= r.bounds().y + r.bounds().height + 0.001);
        }
    }
}
