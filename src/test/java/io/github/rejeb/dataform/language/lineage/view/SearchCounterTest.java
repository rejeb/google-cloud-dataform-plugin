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

import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.SearchScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchCounterTest {

    private static LineageModel model() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder()
                .addNode(LineageViewFixtures.node("p.d.alpha", "d", "table", List.of(), null))
                .addNode(LineageViewFixtures.node("p.d.alpine", "d", "table", List.of(), null))
                .addNode(LineageViewFixtures.node("p.d.beta", "d", "view", List.of(), null))
                .build());
        ColumnLineageGraph.Builder columns = ColumnLineageGraph.builder();
        columns.addColumn(new ColumnRef("p.d.alpha", "total"));
        for (String name : List.of("order_ts", "order_type", "order_tax", "order_tag")) {
            columns.addColumn(new ColumnRef("p.d.alpine", name));
        }
        for (int i = 0; i < 11; i++) columns.addColumn(new ColumnRef("p.d.beta", "id_" + i));
        model.setColumnGraph(columns.build());
        return model;
    }

    private static LineageModel tables(String query) {
        LineageModel model = model();
        model.setSearchQuery(query);
        return model;
    }

    private static LineageModel columns(String query) {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery(query);
        return model;
    }

    @Test
    void aTableSearchCountsTheTablesFound() {
        assertEquals("2 found", SearchCounter.text(tables("al")));
        assertEquals("0 found", SearchCounter.text(tables("zzz")));
    }

    @Test
    void aColumnSearchCountsTheColumnsFound() {
        assertEquals("1 column", SearchCounter.text(columns("total")));
        assertEquals("4 columns", SearchCounter.text(columns("order_t")));
        assertEquals("0 columns", SearchCounter.text(columns("zzz")));
    }

    @Test
    void beyondTenColumnsTheCounterAsksForMoreText() {
        assertEquals("11 columns · type more to show lineage", SearchCounter.text(columns("id")));
    }

    @Test
    void aBlankSearchHasNoCounter() {
        assertEquals("", SearchCounter.text(columns("  ")));
        assertEquals("", SearchCounter.text(tables("")));
    }

    private static LineageModel recorded(String query, String... unreadable) {
        LineageModel model = new LineageModel(null);
        LineageGraph.Builder graph = LineageGraph.builder()
                .addNode(LineageViewFixtures.node("p.d.alpine", "d", "table", List.of(), null));
        for (String name : List.of("other.raw.orders", "other.raw.refunds")) {
            graph.addNode(new io.github.rejeb.dataform.language.lineage.graph.LineageNode(
                    io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf(name),
                    name.substring(name.lastIndexOf('.') + 1), name, "other.raw",
                    io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder.TYPE_OUTSIDE, List.of(), null));
        }
        model.setGraph(graph.build());
        ColumnLineageGraph.Builder columns = ColumnLineageGraph.builder();
        for (String name : List.of("order_ts", "order_type", "order_tax", "order_tag")) {
            columns.addColumn(new ColumnRef("p.d.alpine", name));
        }
        for (int i = 0; i < 11; i++) columns.addColumn(new ColumnRef("p.d.alpine", "id_" + i));
        model.setColumnGraph(columns.build());
        model.setOutsideColumns(new io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns.Result(
                java.util.Map.of(), java.util.Map.of(), java.util.Set.of(unreadable)));
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery(query);
        return model;
    }

    @Test
    void tablesWhoseColumnsCannotBeReadAreCounted() {
        assertEquals("4 columns · 1 table not searched", SearchCounter.text(recorded("order_t", "other.raw.orders")));
        assertEquals("0 columns · 2 tables not searched",
                SearchCounter.text(recorded("zzz", "other.raw.orders", "other.raw.refunds")));
        assertEquals("4 columns", SearchCounter.text(recorded("order_t")));
    }

    @Test
    void theUnsearchedTablesComeAfterTheHintOnTooManyColumns() {
        assertEquals("11 columns · type more to show lineage · 1 table not searched",
                SearchCounter.text(recorded("id", "other.raw.orders")));
    }
}
