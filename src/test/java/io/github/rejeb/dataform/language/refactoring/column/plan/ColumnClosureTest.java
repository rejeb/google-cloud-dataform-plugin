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
package io.github.rejeb.dataform.language.refactoring.column.plan;

import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.column.Confidence;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scope of a column rename, decided on the lineage graph alone.
 */
class ColumnClosureTest {

    private static final ColumnRef BRONZE = new ColumnRef("p.d.bronze", "order_id");
    private static final ColumnRef SILVER = new ColumnRef("p.d.silver", "order_id");
    private static final ColumnRef GOLD = new ColumnRef("p.d.gold", "order_id");

    private static ColumnLineageGraph.Builder graph(ColumnRef... columns) {
        ColumnLineageGraph.Builder builder = ColumnLineageGraph.builder();
        for (ColumnRef column : columns) builder.addColumn(column);
        return builder;
    }

    @Test
    void followsDirectEdgesUpstreamAndDownstream() {
        ColumnLineageGraph lineage = graph(BRONZE, SILVER, GOLD)
                .addEdge(BRONZE.id(), SILVER.id(), Confidence.DIRECT)
                .addEdge(SILVER.id(), GOLD.id(), Confidence.DIRECT)
                .build();

        Set<ColumnRef> reached = ColumnClosure.of(lineage, SILVER).columns();

        assertEquals(Set.of(BRONZE, SILVER, GOLD), reached,
                "renaming a column renames it wherever it flows under the same name");
    }

    @Test
    void stopsAtARenamedEdge() {
        ColumnRef renamed = new ColumnRef("p.d.gold", "order_reference");
        ColumnLineageGraph lineage = graph(SILVER, renamed)
                .addEdge(SILVER.id(), renamed.id(), Confidence.RENAME)
                .build();

        Set<ColumnRef> reached = ColumnClosure.of(lineage, SILVER).columns();

        assertEquals(Set.of(SILVER), reached,
                "a column the query renames carries another name and is another column");
    }

    @Test
    void stopsAtADerivedEdge() {
        ColumnRef derived = new ColumnRef("p.d.gold", "order_id");
        ColumnLineageGraph lineage = graph(SILVER, derived)
                .addEdge(SILVER.id(), derived.id(), Confidence.DERIVED)
                .build();

        assertEquals(Set.of(SILVER), ColumnClosure.of(lineage, SILVER).columns(),
                "a column built by an expression is not the same column");
    }

    @Test
    void reportsAStarAsAnUnknownOrigin() {
        ColumnLineageGraph lineage = graph(BRONZE, SILVER)
                .addEdge(BRONZE.id(), SILVER.id(), Confidence.STAR)
                .build();

        ColumnClosure closure = ColumnClosure.of(lineage, SILVER);

        assertEquals(Set.of(SILVER), closure.columns(), "an unexpanded star is not walked through");
        assertEquals(java.util.List.of(SILVER), closure.unknownOrigins(),
                "the column whose sources are unknown is reported so the user is asked");
    }

    @Test
    void terminatesOnACycle() {
        ColumnLineageGraph lineage = graph(BRONZE, SILVER)
                .addEdge(BRONZE.id(), SILVER.id(), Confidence.DIRECT)
                .addEdge(SILVER.id(), BRONZE.id(), Confidence.DIRECT)
                .build();

        assertEquals(Set.of(BRONZE, SILVER), ColumnClosure.of(lineage, BRONZE).columns());
    }

    @Test
    void keepsSameNamedColumnsOfUnrelatedTablesApart() {
        ColumnRef unrelated = new ColumnRef("p.d.other", "order_id");
        ColumnLineageGraph lineage = graph(SILVER, unrelated).build();

        assertFalse(ColumnClosure.of(lineage, SILVER).columns().contains(unrelated),
                "two tables having a column of the same name is not a relation");
    }

    @Test
    void downstreamOnlyLeavesTheSourcesAlone() {
        ColumnLineageGraph lineage = graph(BRONZE, SILVER, GOLD)
                .addEdge(BRONZE.id(), SILVER.id(), Confidence.DIRECT)
                .addEdge(SILVER.id(), GOLD.id(), Confidence.DIRECT)
                .build();

        Set<ColumnRef> reached = ColumnClosure.downstreamOf(lineage, SILVER).columns();

        assertEquals(Set.of(SILVER, GOLD), reached);
        assertTrue(!reached.contains(BRONZE),
                "declaring the new name in the current file leaves everything upstream untouched");
    }
}
