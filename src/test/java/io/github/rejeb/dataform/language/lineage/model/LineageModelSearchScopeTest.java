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

import io.github.rejeb.dataform.language.lineage.dataplex.OutsideTableColumns;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.analysis.Confidence;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineageModelSearchScopeTest {

    private static final ColumnRef SRC_AMOUNT = new ColumnRef("p.d.src", "Amount");
    private static final ColumnRef SRC_ID = new ColumnRef("p.d.src", "id");
    private static final ColumnRef MID_AMT = new ColumnRef("p.d.mid", "amt");
    private static final ColumnRef FIN_TOTAL = new ColumnRef("p.d.fin", "total_amount");
    private static final ColumnRef OTHER_NAME = new ColumnRef("p.d.other", "name");

    private static LineageNode node(String fullName) {
        return new LineageNode(LineageNode.idOf(fullName), fullName.substring(fullName.lastIndexOf('.') + 1),
                fullName, "d", "table", List.of(), null);
    }

    private static LineageModel model() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder()
                .addNode(node("p.d.src")).addNode(node("p.d.mid")).addNode(node("p.d.fin")).addNode(node("p.d.other"))
                .addEdge(LineageNode.idOf("p.d.src"), LineageNode.idOf("p.d.mid"))
                .addEdge(LineageNode.idOf("p.d.mid"), LineageNode.idOf("p.d.fin"))
                .build());
        model.setColumnGraph(ColumnLineageGraph.builder()
                .addColumn(SRC_AMOUNT).addColumn(SRC_ID).addColumn(MID_AMT).addColumn(FIN_TOTAL).addColumn(OTHER_NAME)
                .addEdge(SRC_AMOUNT.id(), MID_AMT.id(), Confidence.RENAME)
                .addEdge(MID_AMT.id(), FIN_TOTAL.id(), Confidence.DERIVED)
                .build());
        return model;
    }

    private static LineageModel wideModel(int columns) {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder().addNode(node("p.d.wide")).build());
        ColumnLineageGraph.Builder builder = ColumnLineageGraph.builder();
        for (int i = 0; i < columns; i++) builder.addColumn(new ColumnRef("p.d.wide", "id_" + i));
        model.setColumnGraph(builder.build());
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("id");
        return model;
    }

    @Test
    void theScopeStartsOnTables() {
        assertEquals(SearchScope.TABLES, model().searchScope());
    }

    @Test
    void tableScopeIgnoresColumns() {
        LineageModel model = model();
        model.setSearchQuery("amount");
        assertTrue(model.searchMatches().isEmpty());
        assertTrue(model.foundColumnIds().isEmpty());
    }

    @Test
    void columnScopeFindsColumnsOfVisibleNodesIgnoringCase() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("AMOUNT");
        assertEquals(List.of(SRC_AMOUNT.id(), FIN_TOTAL.id()), model.foundColumnIds());
        assertTrue(model.searchMatches().isEmpty());
    }

    @Test
    void columnsOfFilteredOutNodesAreNotFound() {
        LineageModel model = model();
        model.setScopeIds(Set.of(LineageNode.idOf("p.d.fin")));
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");
        assertEquals(List.of(FIN_TOTAL.id()), model.foundColumnIds());
    }

    @Test
    void foundColumnsAreTheLineageRoots() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");
        assertEquals(model.foundColumnIds(), model.lineageRootIds());
        assertEquals(Set.of(SRC_AMOUNT.id(), MID_AMT.id(), FIN_TOTAL.id()), model.highlightColumnLineage());
    }

    @Test
    void moreThanTenFoundColumnsDrawNoLineage() {
        LineageModel model = wideModel(11);
        assertEquals(11, model.foundColumnIds().size());
        assertTrue(model.tooManyFoundColumns());
        assertTrue(model.lineageRootIds().isEmpty());
        assertTrue(model.highlightColumnLineage().isEmpty());
    }

    @Test
    void exactlyTenFoundColumnsDrawLineage() {
        LineageModel model = wideModel(10);
        assertFalse(model.tooManyFoundColumns());
        assertEquals(10, model.lineageRootIds().size());
    }

    @Test
    void anExplicitSelectionWinsOverTheSearch() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");
        model.selectColumn(SRC_ID.id());
        assertEquals(List.of(SRC_ID.id()), model.lineageRootIds());
        assertEquals(Set.of(SRC_ID.id()), model.highlightColumnLineage());
    }

    @Test
    void typingClearsTheExplicitSelection() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.selectColumn(MID_AMT.id());
        model.setSearchQuery("am");
        assertTrue(model.selectedColumnIds().isEmpty());
    }

    @Test
    void typingInTablesScopeKeepsTheColumnSelection() {
        LineageModel model = model();
        model.selectColumn(MID_AMT.id());
        model.setSearchQuery("am");
        assertEquals(Set.of(MID_AMT.id()), model.selectedColumnIds());
    }

    @Test
    void tablesOffAnyRootLineageAreDimmedAndStayVisible() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");
        String other = LineageNode.idOf("p.d.other");
        assertTrue(model.visibleIds().contains(other));
        assertEquals(Set.of(other), model.dimmedIds());
    }

    @Test
    void aTableOnTheLineageIsShownEvenWhenTheFiltersExcludeIt() {
        LineageModel model = model();
        model.setScopeIds(Set.of(LineageNode.idOf("p.d.fin")));
        model.selectColumn(FIN_TOTAL.id());
        assertEquals(Set.of(LineageNode.idOf("p.d.src"), LineageNode.idOf("p.d.mid"), LineageNode.idOf("p.d.fin")),
                model.visibleIds());
    }

    @Test
    void switchingScopeKeepsTheTextAndClearsTheColumnSelection() {
        LineageModel model = model();
        model.setSearchQuery("amount");
        model.selectColumn(MID_AMT.id());
        model.setSearchScope(SearchScope.COLUMNS);
        assertEquals("amount", model.searchQuery());
        assertTrue(model.selectedColumnIds().isEmpty());
        assertEquals(2, model.foundColumnIds().size());
    }

    @Test
    void anEmptyColumnQueryFindsNothing() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("   ");
        assertTrue(model.foundColumnIds().isEmpty());
        assertTrue(model.lineageRootIds().isEmpty());
        assertTrue(model.dimmedIds().isEmpty());
    }

    @Test
    void aColumnQueryWithoutMatchFindsNothingAndDimsNothing() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("zzz");
        assertTrue(model.foundColumnIds().isEmpty());
        assertFalse(model.tooManyFoundColumns());
        assertTrue(model.dimmedIds().isEmpty());
    }

    @Test
    void eachRootHasItsOwnColourIndexInRootOrder() {
        LineageModel model = model();
        model.selectColumn(SRC_AMOUNT.id());
        model.toggleColumn(SRC_ID.id());
        Map<String, Integer> index = model.lineageColourIndex();
        assertEquals(0, index.get(SRC_AMOUNT.id()));
        assertEquals(1, index.get(SRC_ID.id()));
    }

    @Test
    void aColumnOnTwoLineagesTakesTheIndexOfTheFirstRoot() {
        LineageModel model = model();
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");
        Map<String, Integer> index = model.lineageColourIndex();
        assertEquals(0, index.get(SRC_AMOUNT.id()));
        assertEquals(0, index.get(MID_AMT.id()));
        assertEquals(0, index.get(FIN_TOTAL.id()));
    }

    @Test
    void theSeventhRootHasIndexSix() {
        LineageModel model = wideModel(7);
        assertEquals(6, model.lineageColourIndex().get(new ColumnRef("p.d.wide", "id_6").id()));
    }

    private static final String SRC = LineageNode.idOf("p.d.src");
    private static final String MID = LineageNode.idOf("p.d.mid");
    private static final String FIN = LineageNode.idOf("p.d.fin");

    @Test
    void aTableEdgeAShownColumnLineageGoesThroughIsAColumnLineageEdge() {
        LineageModel model = model();
        model.selectColumn(SRC_AMOUNT.id());
        assertTrue(model.isColumnLineageEdge(SRC, MID));
        assertTrue(model.isColumnLineageEdge(MID, FIN));
    }

    @Test
    void aTableEdgeBetweenTwoCardsOnTheLineageWithoutAColumnLinkIsNot() {
        LineageModel model = model();
        model.setGraph(LineageGraph.builder()
                .addNode(node("p.d.src")).addNode(node("p.d.mid")).addNode(node("p.d.fin"))
                .addEdge(SRC, MID).addEdge(MID, FIN).addEdge(SRC, FIN)
                .build());
        model.selectColumn(SRC_AMOUNT.id());
        assertFalse(model.isColumnLineageEdge(SRC, FIN));
    }

    @Test
    void aColumnLinkOffTheShownLineageDoesNotMakeItsTableEdgeAColumnLineageEdge() {
        LineageModel model = model();
        model.selectColumn(SRC_ID.id());
        assertFalse(model.isColumnLineageEdge(SRC, MID));
    }

    @Test
    void withoutRootsNoEdgeIsAColumnLineageEdge() {
        LineageModel model = model();
        assertFalse(model.isColumnLineageEdge(SRC, MID));
        assertFalse(model.isColumnLineageEdge(MID, FIN));
    }

    @Test
    void everyConfidenceCountsAsBeingOnTheLineage() {
        LineageModel model = model();
        model.setColumnGraph(ColumnLineageGraph.builder()
                .addColumn(SRC_AMOUNT).addColumn(MID_AMT).addColumn(FIN_TOTAL)
                .addEdge(SRC_AMOUNT.id(), MID_AMT.id(), Confidence.DERIVED)
                .addEdge(MID_AMT.id(), FIN_TOTAL.id(), Confidence.AMBIGUOUS)
                .build());
        model.selectColumn(MID_AMT.id());
        assertTrue(model.isColumnLineageEdge(SRC, MID));
        assertTrue(model.isColumnLineageEdge(MID, FIN));
    }

    private static final String ORDERS = "other.raw.orders";
    private static final String ORDERS_ID = LineageNode.idOf(ORDERS);
    private static final String BUCKET_ID = LineageNode.idOf("gcs:bucket.exports");

    private static LineageModel recorded() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder()
                .addNode(node("p.d.src"))
                .addNode(new LineageNode(ORDERS_ID, "orders", ORDERS, "other.raw", DataplexGraphBuilder.TYPE_OUTSIDE, List.of(), null))
                .addNode(new LineageNode(BUCKET_ID, "exports", "gcs:bucket.exports", "bucket", DataplexGraphBuilder.TYPE_ASSET, List.of(), null))
                .addEdge(ORDERS_ID, LineageNode.idOf("p.d.src"))
                .addEdge(BUCKET_ID, ORDERS_ID)
                .build());
        model.setColumnGraph(ColumnLineageGraph.builder().addColumn(SRC_AMOUNT).addColumn(SRC_ID).build());
        return model;
    }

    private static OutsideTableColumns.Result readable() {
        ColumnRef amount = new ColumnRef(ORDERS, "amount_eur");
        ColumnRef id = new ColumnRef(ORDERS, "id");
        return new OutsideTableColumns.Result(Map.of(ORDERS, List.of(amount, id)),
                Map.of(LineageModel.columnTypeKey(ORDERS, "amount_eur"), "NUMERIC"), Set.of());
    }

    private static OutsideTableColumns.Result unreadable() {
        return new OutsideTableColumns.Result(Map.of(), Map.of(), Set.of(ORDERS));
    }

    @Test
    void aColumnSearchAlsoFindsColumnsOfOutsideTables() {
        LineageModel model = recorded();
        model.setOutsideColumns(readable());
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");

        assertEquals(Set.of(SRC_AMOUNT.id(), ORDERS + "#amount_eur"), Set.copyOf(model.foundColumnIds()));
        assertEquals(List.of(new ColumnRef(ORDERS, "amount_eur")), model.cardColumns(ORDERS_ID).rows());
        assertTrue(model.hasColumns(ORDERS_ID));
        assertTrue(model.isRootColumn(ORDERS + "#amount_eur"));
        assertEquals("NUMERIC", model.columnType(new ColumnRef(ORDERS, "amount_eur")));
        assertEquals(0, model.unsearchedTableCount());
        assertEquals(null, model.cardNote(ORDERS_ID));
    }

    @Test
    void anExpandedOutsideCardListsEveryColumn() {
        LineageModel model = recorded();
        model.setOutsideColumns(readable());

        model.toggleExpanded(ORDERS_ID);

        assertEquals(2, model.cardColumns(ORDERS_ID).rows().size());
    }

    @Test
    void theColumnsOfTheRepositoryWinOverTheOutsideOnes() {
        LineageModel model = recorded();
        model.setOutsideColumns(new OutsideTableColumns.Result(
                Map.of("p.d.src", List.of(new ColumnRef("p.d.src", "stale"))), Map.of(), Set.of()));
        model.toggleExpanded(LineageNode.idOf("p.d.src"));

        assertEquals(List.of(SRC_AMOUNT, SRC_ID), model.cardColumns(LineageNode.idOf("p.d.src")).rows());
    }

    @Test
    void anUnreadableOutsideTableIsCountedAndCarriesTheNote() {
        LineageModel model = recorded();
        model.setOutsideColumns(unreadable());
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");

        assertEquals(1, model.unsearchedTableCount());
        assertEquals("Columns not readable", model.cardNote(ORDERS_ID));
        assertEquals(null, model.cardNote(LineageNode.idOf("p.d.src")));
        assertFalse(model.hasColumns(ORDERS_ID));
    }

    @Test
    void theNoteAndTheCountAreForAColumnSearchOnly() {
        LineageModel model = recorded();
        model.setOutsideColumns(unreadable());
        model.setSearchQuery("amount");
        assertEquals(0, model.unsearchedTableCount());
        assertEquals(null, model.cardNote(ORDERS_ID));

        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery(" ");

        assertEquals(0, model.unsearchedTableCount());
        assertEquals(null, model.cardNote(ORDERS_ID));
    }

    @Test
    void assetsAreNeitherSearchedNorCounted() {
        LineageModel model = recorded();
        model.setOutsideColumns(new OutsideTableColumns.Result(Map.of(), Map.of(), Set.of(ORDERS, "gcs:bucket.exports")));
        model.setSearchScope(SearchScope.COLUMNS);
        model.setSearchQuery("amount");

        assertEquals(1, model.unsearchedTableCount());
        assertEquals(null, model.cardNote(BUCKET_ID));
        assertFalse(model.hasColumns(BUCKET_ID));
    }

    @Test
    void settingTheOutsideColumnsTellsTheListenersOnceForTheSameResult() {
        LineageModel model = recorded();
        int[] fired = {0};
        model.addListener(m -> fired[0]++);

        model.setOutsideColumns(readable());
        model.setOutsideColumns(readable());

        assertEquals(1, fired[0]);
        assertEquals(readable(), model.outsideColumns());
    }
}
