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

import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.analysis.Confidence;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineageModelCardTest {

    private static final String SRC = LineageNode.idOf("p.d.src");
    private static final String FIN = LineageNode.idOf("p.d.fin");
    private static final String WIDE = LineageNode.idOf("p.d.wide");

    private static LineageNode node(String fullName) {
        return new LineageNode(LineageNode.idOf(fullName), fullName.substring(fullName.lastIndexOf('.') + 1),
                fullName, "d", "table", List.of(), null);
    }

    private static ColumnRef col(String table, String name) {
        return new ColumnRef(table, name);
    }

    private static LineageModel model() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder()
                .addNode(node("p.d.src")).addNode(node("p.d.fin")).addNode(node("p.d.wide")).addNode(node("p.d.bare"))
                .addEdge(SRC, FIN).build());
        ColumnLineageGraph.Builder columns = ColumnLineageGraph.builder()
                .addColumn(col("p.d.src", "id")).addColumn(col("p.d.src", "amount")).addColumn(col("p.d.src", "country"))
                .addColumn(col("p.d.fin", "total")).addColumn(col("p.d.fin", "day"));
        for (int i = 0; i < 15; i++) columns.addColumn(col("p.d.wide", "c" + i));
        columns.addEdge(col("p.d.src", "amount").id(), col("p.d.fin", "total").id(), Confidence.DERIVED);
        model.setColumnGraph(columns.build());
        return model;
    }

    private static List<String> names(CardColumns card) {
        List<String> names = new ArrayList<>();
        card.rows().forEach(c -> names.add(c.columnName()));
        return names;
    }

    @Test
    void aCollapsedCardWithNothingToShowHasNoRows() {
        CardColumns card = model().cardColumns(SRC);
        assertTrue(card.rows().isEmpty());
        assertEquals(0, card.hidden());
    }

    @Test
    void aCollapsedCardListsOnlyTheColumnsOnAShownLineage() {
        LineageModel model = model();
        model.selectColumn(col("p.d.fin", "total").id());
        assertEquals(List.of("amount"), names(model.cardColumns(SRC)));
        assertEquals(List.of("total"), names(model.cardColumns(FIN)));
    }

    @Test
    void aCollapsedCardListsTheFoundColumnsEvenWhenTooManyToDrawLineage() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("c");
        assertTrue(model.tooManyFoundColumns());
        assertEquals(List.of("country"), names(model.cardColumns(SRC)));
        assertEquals(12, model.cardColumns(WIDE).rows().size());
        assertEquals(3, model.cardColumns(WIDE).hidden());
    }

    @Test
    void anExpandedCardListsEveryColumnInTableOrder() {
        LineageModel model = model();
        model.toggleExpanded(SRC);
        assertTrue(model.isExpanded(SRC));
        assertEquals(List.of("id", "amount", "country"), names(model.cardColumns(SRC)));
        assertEquals(0, model.cardColumns(SRC).hidden());
    }

    @Test
    void togglingTwiceCollapsesTheCard() {
        LineageModel model = model();
        model.toggleExpanded(SRC);
        model.toggleExpanded(SRC);
        assertFalse(model.isExpanded(SRC));
        assertTrue(model.cardColumns(SRC).rows().isEmpty());
    }

    @Test
    void aCardShowsAtMostTwelveRowsAndCountsTheRest() {
        LineageModel model = model();
        model.toggleExpanded(WIDE);
        CardColumns card = model.cardColumns(WIDE);
        assertEquals(CardColumns.MAX_ROWS, card.rows().size());
        assertEquals(3, card.hidden());
        assertEquals("c0", card.rows().get(0).columnName());
        assertEquals("c11", card.rows().get(11).columnName());
    }

    @Test
    void aColumnOnTheLineageBeyondTheTwelfthStaysInTheExpandedCard() {
        LineageModel model = model();
        model.toggleExpanded(WIDE);
        model.selectColumn(col("p.d.wide", "c14").id());
        List<String> names = names(model.cardColumns(WIDE));
        assertEquals(12, names.size());
        assertTrue(names.contains("c14"));
        assertEquals("c14", names.get(11));
        assertEquals("c0", names.get(0));
    }

    @Test
    void aCardWithoutColumnsHasNoRowsEvenExpanded() {
        LineageModel model = model();
        String bare = LineageNode.idOf("p.d.bare");
        model.toggleExpanded(bare);
        assertTrue(model.cardColumns(bare).rows().isEmpty());
    }

    @Test
    void expansionIsForgottenForNodesThatLeaveTheGraph() {
        LineageModel model = model();
        model.toggleExpanded(SRC);
        model.setGraph(LineageGraph.builder().addNode(node("p.d.fin")).build());
        assertFalse(model.isExpanded(SRC));
    }

    @Test
    void aCardHasNoNoteYet() {
        assertNull(model().cardNote(SRC));
    }

    @Test
    void togglingACardNotifiesTheListeners() {
        LineageModel model = model();
        int[] calls = {0};
        model.addListener(m -> calls[0]++);
        model.toggleExpanded(SRC);
        assertEquals(1, calls[0]);
    }

    private static final String TARGET = LineageNode.idOf("p.d.target");

    private static LineageModel recorded(String targetType, boolean columnLink) {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder()
                .addNode(node("p.d.src"))
                .addNode(new LineageNode(TARGET, "target", "p.d.target", "d", targetType, List.of(), null))
                .addEdge(SRC, TARGET).build());
        ColumnLineageGraph.Builder columns = ColumnLineageGraph.builder()
                .addColumn(col("p.d.src", "amount")).addColumn(col("p.d.target", "total"));
        if (columnLink) columns.addEdge(col("p.d.src", "amount").id(), col("p.d.target", "total").id(), Confidence.DERIVED);
        model.setColumnGraph(columns.build());
        model.setSource(LineageSource.DATAPLEX);
        return model;
    }










    @Test
    void anIncrementalTableOrAScriptOffTheColumnLineageCarriesNoNote() {
        for (String type : List.of("incremental", "operations")) {
            LineageModel model = recorded(type, false);

            model.selectColumn(col("p.d.src", "amount").id());

            assertNull(model.cardNote(TARGET), type);
            assertTrue(model.cardColumns(TARGET).rows().isEmpty(), type);
        }
    }

    @Test
    void anIncrementalTableOrAScriptOffTheColumnLineageIsFadedLikeAnyOther() {
        for (String type : List.of("incremental", "operations")) {
            LineageModel model = recorded(type, false);

            model.selectColumn(col("p.d.src", "amount").id());

            assertTrue(model.dimmedIds().contains(TARGET), type);
            assertFalse(model.dimmedIds().contains(SRC), type);
        }
    }

    @Test
    void anOrdinaryTableOffTheLineageIsStillFaded() {
        LineageModel model = recorded("table", false);

        model.selectColumn(col("p.d.src", "amount").id());

        assertTrue(model.dimmedIds().contains(TARGET));
    }
}
