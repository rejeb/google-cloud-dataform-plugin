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
package io.github.rejeb.dataform.language.lineage.model;

import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.column.Confidence;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LineageModelColumnTest {

    @Test
    void highlightIsEmptyWithoutSelection() {
        LineageModel model = new LineageModel(null);
        assertTrue(model.highlightColumnLineage().isEmpty());
    }

    @Test
    void highlightReturnsUpstreamAndDownstream() {
        ColumnRef a = new ColumnRef("p.d.src", "amount");
        ColumnRef b = new ColumnRef("p.d.mid", "amt");
        ColumnRef c = new ColumnRef("p.d.fin", "total");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(a).addColumn(b).addColumn(c)
                .addEdge(a.id(), b.id(), Confidence.RENAME)
                .addEdge(b.id(), c.id(), Confidence.RENAME)
                .build();

        LineageModel model = new LineageModel(null);
        model.setColumnGraph(g);
        model.selectColumn(b.id());

        assertEquals(Set.of(a.id(), b.id(), c.id()), model.highlightColumnLineage());
    }

    @Test
    void selectingColumnScopesVisibleTablesToItsLineage() {
        LineageNode src = node("p.d.src");
        LineageNode mid = node("p.d.mid");
        LineageNode other = node("p.d.other");
        LineageGraph tableGraph = LineageGraph.builder()
                .addNode(src).addNode(mid).addNode(other).build();

        ColumnRef a = new ColumnRef("p.d.src", "amount");
        ColumnRef b = new ColumnRef("p.d.mid", "amt");
        ColumnLineageGraph columnGraph = ColumnLineageGraph.builder()
                .addColumn(a).addColumn(b)
                .addEdge(a.id(), b.id(), Confidence.RENAME)
                .build();

        LineageModel model = new LineageModel(null);
        model.setGraph(tableGraph);
        model.setColumnGraph(columnGraph);

        assertEquals(Set.of(src.id(), mid.id(), other.id()), model.visibleIds());

        model.selectColumn(b.id());
        assertEquals(Set.of(src.id(), mid.id()), model.visibleIds());

        model.clearColumnSelection();
        assertEquals(Set.of(src.id(), mid.id(), other.id()), model.visibleIds());
    }

    private LineageNode node(String fullName) {
        return new LineageNode(LineageNode.idOf(fullName), fullName, fullName,
                "d", "table", List.of(), null);
    }

    @Test
    void multipleColumnsOnSameNodeUnionTheirLineage() {
        ColumnRef srcA = new ColumnRef("p.d.src", "a");
        ColumnRef srcB = new ColumnRef("p.d.src", "b");
        ColumnRef outA = new ColumnRef("p.d.out", "a");
        ColumnRef outB = new ColumnRef("p.d.out", "b");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(srcA).addColumn(srcB).addColumn(outA).addColumn(outB)
                .addEdge(srcA.id(), outA.id(), Confidence.DIRECT)
                .addEdge(srcB.id(), outB.id(), Confidence.DIRECT)
                .build();

        LineageModel model = new LineageModel(null);
        model.setColumnGraph(g);
        model.selectColumn(outA.id());
        model.toggleColumn(outB.id());

        assertEquals(Set.of(outA.id(), outB.id()), model.selectedColumnIds());
        assertEquals(Set.of(srcA.id(), outA.id(), srcB.id(), outB.id()), model.highlightColumnLineage());
    }

    @Test
    void togglingColumnOnDifferentNodeReplacesSelection() {
        ColumnRef a = new ColumnRef("p.d.t1", "x");
        ColumnRef b = new ColumnRef("p.d.t2", "y");
        ColumnLineageGraph g = ColumnLineageGraph.builder().addColumn(a).addColumn(b).build();
        LineageModel model = new LineageModel(null);
        model.setColumnGraph(g);
        model.selectColumn(a.id());
        model.toggleColumn(b.id());
        assertEquals(Set.of(b.id()), model.selectedColumnIds());
    }

    @Test
    void clearSelectionEmptiesHighlight() {
        ColumnRef a = new ColumnRef("p.d.src", "amount");
        ColumnLineageGraph g = ColumnLineageGraph.builder().addColumn(a).build();
        LineageModel model = new LineageModel(null);
        model.setColumnGraph(g);
        model.selectColumn(a.id());
        model.clearColumnSelection();
        assertNull(model.selectedColumnId());
        assertTrue(model.highlightColumnLineage().isEmpty());
    }
}
