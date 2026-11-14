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
package io.github.rejeb.dataform.language.lineage.dataplex;

import io.github.rejeb.dataform.language.columns.analysis.ColumnEdge;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.analysis.Confidence;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.rejeb.dataform.language.lineage.dataplex.FakeLineageClient.fqn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataplexColumnLineageTest {

    private static final LineageScope SCOPE = new LineageScope("p", "eu");

    private static ColumnRef col(String table, String name) {
        return new ColumnRef("p.d." + table, name);
    }

    private static Set<String> loaded(String... tables) {
        return Set.of(tables).stream().map(table -> "p.d." + table).collect(java.util.stream.Collectors.toSet());
    }

    private static DataplexColumnLineage.Result load(FakeLineageClient client, List<ColumnRef> roots, String... tables) {
        return DataplexColumnLineage.load(client, SCOPE, roots, loaded(tables));
    }

    @Test
    void walksColumnLinksBothWaysFromEachRootWithinTheLoadedTables() {
        FakeLineageClient client = new FakeLineageClient()
                .columnLink("a", "x", "b", "y").columnLink("b", "y", "c", "z").columnLink("b", "other", "c", "other");

        DataplexColumnLineage.Result result = load(client, List.of(col("b", "y")), "a", "b", "c");

        ColumnLineageGraph graph = result.graph();
        assertEquals(Set.of(col("a", "x").id()), graph.upstream(col("b", "y").id()));
        assertEquals(Set.of(col("c", "z").id()), graph.downstream(col("b", "y").id()));
        assertNull(graph.column(col("b", "other").id()));
        assertNull(result.reason());
        assertEquals(List.of("COLUMN STREAM [UPSTREAM, DOWNSTREAM] {" + fqn("b") + "=[y]} depth=100"), client.calls);
        assertEquals(List.of(SCOPE), client.columnScopes);
    }

    @Test
    void anExactCopyIsDirectAndAnythingElseDerived() {
        FakeLineageClient client = new FakeLineageClient()
                .exactColumnLink("a", "x", "b", "y").columnLink("a", "w", "b", "y");

        ColumnLineageGraph graph = load(client, List.of(col("b", "y")), "a", "b").graph();

        Map<String, Confidence> kinds = new java.util.HashMap<>();
        for (ColumnEdge edge : graph.edges()) kinds.put(edge.from().columnName(), edge.kind());
        assertEquals(Map.of("x", Confidence.DIRECT, "w", Confidence.DERIVED), kinds);
    }

    @Test
    void aColumnLinkLeavingTheLoadedTablesIsNotFollowedEvenBackIntoThem() {
        FakeLineageClient client = new FakeLineageClient()
                .columnLink("outside", "w", "a", "x").columnLink("a", "x", "b", "y").columnLink("b", "y", "far", "v")
                .columnLink("far", "v", "c", "z").columnLink("c", "z", "c2", "z");

        ColumnLineageGraph graph = load(client, List.of(col("b", "y")), "a", "b", "c", "c2").graph();

        assertEquals(Set.of(col("a", "x").id(), col("b", "y").id()),
                graph.columns().stream().map(ColumnRef::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, graph.edges().size());
    }

    @Test
    void aCycleOfColumnLinksTerminates() {
        FakeLineageClient client = new FakeLineageClient().columnLink("a", "x", "b", "y").columnLink("b", "y", "a", "x");

        ColumnLineageGraph graph = load(client, List.of(col("b", "y")), "a", "b").graph();

        assertEquals(2, graph.columns().size());
        assertEquals(2, graph.edges().size());
        assertEquals(1, client.calls.size());
    }

    @Test
    void everyRootIsAskedInOneSearchByTableAndTheSameLinkIsKeptOnce() {
        FakeLineageClient client = new FakeLineageClient().columnLink("a", "x", "b", "y").columnLink("a", "x", "b", "y2");

        ColumnLineageGraph graph = load(client, List.of(col("b", "y"), col("a", "x"), col("b", "y2")), "a", "b").graph();

        assertEquals(List.of("COLUMN STREAM [UPSTREAM, DOWNSTREAM] {" + fqn("b") + "=[y, y2], " + fqn("a") + "=[x]} depth=100"),
                client.calls);
        assertEquals(2, graph.edges().size());
    }

    @Test
    void aRootOutsideTheLoadedTablesIsNotAsked() {
        FakeLineageClient client = new FakeLineageClient().columnLink("a", "x", "b", "y");

        DataplexColumnLineage.Result result = load(client, List.of(col("b", "y")), "a");

        assertTrue(result.graph().columns().isEmpty());
        assertTrue(client.calls.isEmpty());
    }

    @Test
    void withoutRootNothingIsAsked() {
        FakeLineageClient client = new FakeLineageClient().columnLink("a", "x", "b", "y");

        DataplexColumnLineage.Result result = load(client, List.of(), "a", "b");

        assertTrue(result.graph().columns().isEmpty());
        assertNull(result.reason());
        assertTrue(client.calls.isEmpty());
    }

    @Test
    void permissionDeniedOnFieldsGivesAnEmptyGraphAndAReason() {
        FakeLineageClient client = new FakeLineageClient().columnLink("a", "x", "b", "y")
                .failColumns(DataplexLineageException.Kind.PERMISSION_DENIED);

        DataplexColumnLineage.Result result = load(client, List.of(col("b", "y")), "a", "b");

        assertTrue(result.graph().edges().isEmpty());
        assertEquals("Your account cannot read column lineage in p: it needs the permission datalineage.events.getFields",
                result.reason());
    }

    @Test
    void anyOtherFailureGivesAnEmptyGraphAndItsMessage() {
        FakeLineageClient client = new FakeLineageClient().columnLink("a", "x", "b", "y")
                .failColumns(DataplexLineageException.Kind.OTHER);

        DataplexColumnLineage.Result result = load(client, List.of(col("b", "y")), "a", "b");

        assertTrue(result.graph().edges().isEmpty());
        assertEquals("Knowledge Catalog cannot give the column lineage: failed on columns", result.reason());
    }

    @Test
    void theRecordedLinksAreLaidOverTheKnownColumnsWithoutTheirDataformEdges() {
        ColumnLineageGraph known = ColumnLineageGraph.builder()
                .addColumn(col("a", "X")).addColumn(col("a", "unused")).addColumn(col("b", "y"))
                .addEdge(col("a", "unused").id(), col("b", "y").id(), Confidence.DIRECT)
                .addUnresolvedTable("p.d.c").build();
        ColumnLineageGraph recorded = ColumnLineageGraph.builder()
                .addColumn(col("a", "x")).addColumn(col("b", "y")).addColumn(col("o", "w"))
                .addEdge(col("a", "x").id(), col("b", "y").id(), Confidence.DERIVED)
                .addEdge(col("o", "w").id(), col("a", "x").id(), Confidence.DERIVED).build();
        Map<String, List<ColumnRef>> outside = Map.of("p.d.o", List.of(col("o", "w"), col("o", "v")));

        ColumnLineageGraph graph = DataplexColumnLineage.over(known, outside, recorded);

        assertEquals(List.of("X", "unused"), graph.columnsForTable(col("a", "x").tableNodeId()).stream()
                .map(ColumnRef::columnName).toList());
        assertEquals(2, graph.columnsForTable(col("o", "w").tableNodeId()).size());
        assertEquals(Set.of(col("a", "X").id(), col("o", "w").id()), graph.upstream(col("b", "y").id()));
        assertEquals(Set.of("p.d.c"), graph.unresolvedTables());
    }

    @Test
    void withoutRecordedLinksTheKnownColumnsHaveNoEdge() {
        ColumnLineageGraph known = ColumnLineageGraph.builder()
                .addColumn(col("a", "x")).addColumn(col("b", "y"))
                .addEdge(col("a", "x").id(), col("b", "y").id(), Confidence.DIRECT).build();

        ColumnLineageGraph graph = DataplexColumnLineage.over(known, Map.of(), null);

        assertEquals(2, graph.columns().size());
        assertTrue(graph.edges().isEmpty());
        assertTrue(DataplexColumnLineage.over(null, Map.of(), null).columns().isEmpty());
    }

    @Test
    void theColumnsFoundAreKeptLevelByLevel() {
        FakeLineageClient client = new FakeLineageClient()
                .columnLink("s", "x", "a", "x").columnLink("t", "y", "b", "y")
                .columnLink("a", "x", "c", "z").columnLink("b", "y", "c", "w");

        DataplexColumnLineage.Result result = load(client, List.of(col("c", "z"), col("c", "w")), "a", "b", "c", "s", "t");

        assertEquals(List.of(col("c", "z"), col("c", "w"), col("a", "x"), col("b", "y"), col("s", "x"), col("t", "y")),
                List.copyOf(result.graph().columns()));
    }
}
