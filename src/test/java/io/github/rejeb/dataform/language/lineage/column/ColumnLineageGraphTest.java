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
package io.github.rejeb.dataform.language.lineage.column;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ColumnLineageGraphTest {

    private ColumnRef c(String t, String col) { return new ColumnRef(t, col); }

    @Test
    void directNeighborsAreExposed() {
        ColumnRef a = c("p.d.src", "amount");
        ColumnRef b = c("p.d.mid", "total");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(a).addColumn(b)
                .addEdge(a.id(), b.id(), Confidence.RENAME)
                .build();
        assertEquals(Set.of(b.id()), g.successors(a.id()));
        assertEquals(Set.of(a.id()), g.predecessors(b.id()));
    }

    @Test
    void upstreamAndDownstreamAreTransitive() {
        ColumnRef a = c("p.d.src", "amount");
        ColumnRef b = c("p.d.mid", "total");
        ColumnRef d = c("p.d.final", "grand_total");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(a).addColumn(b).addColumn(d)
                .addEdge(a.id(), b.id(), Confidence.RENAME)
                .addEdge(b.id(), d.id(), Confidence.RENAME)
                .build();
        assertEquals(Set.of(a.id(), b.id()), g.upstream(d.id()));
        assertEquals(Set.of(b.id(), d.id()), g.downstream(a.id()));
    }

    @Test
    void cyclesDoNotLoopForever() {
        ColumnRef a = c("p.d.a", "x");
        ColumnRef b = c("p.d.b", "x");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(a).addColumn(b)
                .addEdge(a.id(), b.id(), Confidence.DIRECT)
                .addEdge(b.id(), a.id(), Confidence.DIRECT)
                .build();
        assertEquals(Set.of(a.id(), b.id()), g.upstream(a.id()));
    }

    @Test
    void columnsForTableIndexesByTableNode() {
        ColumnRef a1 = c("p.d.t1", "a");
        ColumnRef a2 = c("p.d.t1", "b");
        ColumnRef b1 = c("p.d.t2", "a");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(a1).addColumn(a2).addColumn(b1)
                .build();
        assertEquals(2, g.columnsForTable(a1.tableNodeId()).size());
        assertEquals(1, g.columnsForTable(b1.tableNodeId()).size());
        assertTrue(g.columnsForTable("node:missing").isEmpty());
    }

    @Test
    void edgeToUnknownEndpointIsIgnored() {
        ColumnRef a = c("p.d.a", "x");
        ColumnLineageGraph g = ColumnLineageGraph.builder()
                .addColumn(a)
                .addEdge(a.id(), "p.d.missing#y", Confidence.DIRECT)
                .build();
        assertTrue(g.successors(a.id()).isEmpty());
    }
}
