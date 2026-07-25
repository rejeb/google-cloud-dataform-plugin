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

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColumnLineageExtractorImplTest {

    private static final Gson GSON = new Gson();

    private CompiledGraph graph(String tablesJson) {
        return GSON.fromJson("{\"tables\":" + tablesJson + "}", CompiledGraph.class);
    }

    private CompiledGraph graph(String tablesJson, String assertionsJson) {
        return GSON.fromJson("{\"tables\":" + tablesJson + ",\"assertions\":" + assertionsJson + "}",
                CompiledGraph.class);
    }

    private String tableJson(String database, String schema, String name, String query, String depsJson) {
        return "{\"target\":{\"database\":\"" + database + "\",\"schema\":\"" + schema + "\",\"name\":\"" + name + "\"},"
                + "\"query\":\"" + query + "\",\"dependencyTargets\":" + depsJson + "}";
    }

    private String targetJson(String database, String schema, String name) {
        return "{\"database\":\"" + database + "\",\"schema\":\"" + schema + "\",\"name\":\"" + name + "\"}";
    }

    private SelectAnalyzer analyzer(Map<String, Map<String, List<InputColumn>>> analyzeStub,
                                    Map<String, Map<String, String>> aliasStub) {
        return new SelectAnalyzer() {
            @Override
            public Map<String, List<InputColumn>> analyze(String sql) {
                return analyzeStub.getOrDefault(sql, Map.of());
            }

            @Override
            public Map<String, String> fromAliases(String sql) {
                return aliasStub.getOrDefault(sql, Map.of());
            }
        };
    }

    @Test
    void tracksRenameAcrossTwoLayers() {
        String midSql = "SELECT amount AS amt FROM p.d.src";
        String finSql = "SELECT amt AS total FROM p.d.mid";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "mid", midSql, "[" + targetJson("p", "d", "src") + "]") + ","
                + tableJson("p", "d", "fin", finSql, "[" + targetJson("p", "d", "mid") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(
                        midSql, Map.of("amt", List.of(new InputColumn(null, "amount", Confidence.RENAME, false))),
                        finSql, Map.of("total", List.of(new InputColumn(null, "amt", Confidence.RENAME, false)))),
                Map.of(
                        midSql, Map.of("p.d.src", "p.d.src"),
                        finSql, Map.of("p.d.mid", "p.d.mid")));

        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(new ColumnInfo("amount", "NUMERIC", "NULLABLE", null)),
                "p.d.mid", List.of(new ColumnInfo("amt", "NUMERIC", "NULLABLE", null)),
                "p.d.fin", List.of(new ColumnInfo("total", "NUMERIC", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        String total = new ColumnRef("p.d.fin", "total").id();
        String amount = new ColumnRef("p.d.src", "amount").id();
        assertTrue(result.upstream(total).contains(amount),
                "final column should trace to the original source column across the rename chain");
    }

    @Test
    void computedColumnWithoutInputsStillAppears() {
        String sql = "SELECT CURRENT_DATE AS PARTITION_DATE FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "t", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("PARTITION_DATE", List.<InputColumn>of())),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(new ColumnInfo("id", "INT64", "NULLABLE", null)),
                "p.d.t", List.of(new ColumnInfo("PARTITION_DATE", "DATE", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);
        assertNotNull(result.column(new ColumnRef("p.d.t", "PARTITION_DATE").id()),
                "a computed column with no upstream must still be a node");
    }

    @Test
    void explicitStructColumnIsExpandedIntoNestedPaths() {
        String sql = "SELECT goalKeeper AS gk FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "view", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("gk", List.of(new InputColumn(null, "goalKeeper", Confidence.RENAME, false)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        ColumnInfo goalKeeper = new ColumnInfo("goalKeeper", "RECORD", "NULLABLE", null, List.of(
                new ColumnInfo("playerName", "STRING", "NULLABLE", null),
                new ColumnInfo("cleanSheets", "INT64", "NULLABLE", null)));
        ColumnInfo gk = new ColumnInfo("gk", "RECORD", "NULLABLE", null, List.of(
                new ColumnInfo("playerName", "STRING", "NULLABLE", null),
                new ColumnInfo("cleanSheets", "INT64", "NULLABLE", null)));
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(goalKeeper),
                "p.d.view", List.of(gk));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        assertNotNull(result.column(new ColumnRef("p.d.view", "gk.playerName").id()));
        assertNotNull(result.column(new ColumnRef("p.d.view", "gk.cleanSheets").id()));
        assertNull(result.column(new ColumnRef("p.d.view", "gk").id()),
                "the struct container itself is not shown as a column");
        assertTrue(result.upstream(new ColumnRef("p.d.view", "gk.playerName").id())
                .contains(new ColumnRef("p.d.src", "goalKeeper.playerName").id()),
                "nested output traces to the matching nested source field");
    }

    @Test
    void structConstructorMapsFieldsToMatchingSourceColumns() {
        String sql = "SELECT STRUCT(playerName, savePercentage) AS goalKeeper FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "stat", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("goalKeeper", List.of(
                        new InputColumn(null, "playerName", Confidence.DERIVED, false),
                        new InputColumn(null, "savePercentage", Confidence.DERIVED, false)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        ColumnInfo goalKeeperStruct = new ColumnInfo("goalKeeper", "RECORD", "NULLABLE", null, List.of(
                new ColumnInfo("playerName", "STRING", "NULLABLE", null),
                new ColumnInfo("savePercentage", "STRING", "NULLABLE", null)));
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(new ColumnInfo("playerName", "STRING", "NULLABLE", null),
                        new ColumnInfo("savePercentage", "STRING", "NULLABLE", null)),
                "p.d.stat", List.of(goalKeeperStruct));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        assertNotNull(result.column(new ColumnRef("p.d.stat", "goalKeeper.savePercentage").id()));
        assertNull(result.column(new ColumnRef("p.d.stat", "goalKeeper").id()),
                "the struct output should be represented by its fields, not the whole struct");
        assertTrue(result.upstream(new ColumnRef("p.d.stat", "goalKeeper.savePercentage").id())
                .contains(new ColumnRef("p.d.src", "savePercentage").id()),
                "the struct field must trace to its own source column");
        assertFalse(result.upstream(new ColumnRef("p.d.stat", "goalKeeper.savePercentage").id())
                .contains(new ColumnRef("p.d.src", "playerName").id()),
                "the struct field must not absorb sibling field sources");
    }

    @Test
    void structFieldLineageChainsAcrossTables() {
        String buildSql = "SELECT STRUCT(savePercentage) AS goalKeeper FROM p.d.src";
        String viewSql = "SELECT goalKeeper.* FROM p.d.stat";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "stat", buildSql, "[" + targetJson("p", "d", "src") + "]") + ","
                + tableJson("p", "d", "view", viewSql, "[" + targetJson("p", "d", "stat") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(
                        buildSql, Map.of("goalKeeper",
                                List.of(new InputColumn(null, "savePercentage", Confidence.DERIVED, false))),
                        viewSql, Map.of("goalKeeper.*",
                                List.of(new InputColumn("goalKeeper", "*", Confidence.STAR, true)))),
                Map.of(
                        buildSql, Map.of("p.d.src", "p.d.src"),
                        viewSql, Map.of("p.d.stat", "p.d.stat")));

        ColumnInfo goalKeeperStruct = new ColumnInfo("goalKeeper", "RECORD", "NULLABLE", null,
                List.of(new ColumnInfo("savePercentage", "STRING", "NULLABLE", null)));
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(new ColumnInfo("savePercentage", "STRING", "NULLABLE", null)),
                "p.d.stat", List.of(goalKeeperStruct),
                "p.d.view", List.of(new ColumnInfo("savePercentage", "STRING", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        String viewCol = new ColumnRef("p.d.view", "savePercentage").id();
        assertTrue(result.upstream(viewCol).contains(new ColumnRef("p.d.stat", "goalKeeper.savePercentage").id()),
                "view column traces through the struct field");
        assertTrue(result.upstream(viewCol).contains(new ColumnRef("p.d.src", "savePercentage").id()),
                "and continues to the original source column across the struct boundary");
    }

    @Test
    void structStarExpandsNestedFieldsNotWholeTable() {
        String sql = "SELECT goalKeeper.* FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "view", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("goalKeeper.*",
                        List.of(new InputColumn("goalKeeper", "*", Confidence.STAR, true)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        ColumnInfo goalKeeper = new ColumnInfo("goalKeeper", "RECORD", "NULLABLE", null, List.of(
                new ColumnInfo("playerName", "STRING", "NULLABLE", null),
                new ColumnInfo("cleanSheets", "INT64", "NULLABLE", null)));
        ColumnInfo unrelated = new ColumnInfo("teamName", "STRING", "NULLABLE", null);
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(goalKeeper, unrelated),
                "p.d.view", List.of(new ColumnInfo("playerName", "STRING", "NULLABLE", null),
                        new ColumnInfo("cleanSheets", "INT64", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        assertNotNull(result.column(new ColumnRef("p.d.view", "playerName").id()));
        assertNotNull(result.column(new ColumnRef("p.d.view", "cleanSheets").id()));
        assertNull(result.column(new ColumnRef("p.d.view", "teamName").id()),
                "struct.* must not pull in sibling table columns");
        assertTrue(result.upstream(new ColumnRef("p.d.view", "playerName").id())
                .contains(new ColumnRef("p.d.src", "goalKeeper.playerName").id()));
    }

    @Test
    void tableWithoutResolvedSchemaIsSkippedAndReported() {
        String sql = "SELECT * FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "copy", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("*", List.of(new InputColumn(null, "*", Confidence.STAR, true)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, Map.of());

        assertTrue(result.columns().isEmpty(),
                "no schema means no column may be invented for any table");
        assertTrue(result.unresolvedTables().contains("p.d.copy"),
                "the skipped table must be reported so the user can be warned");
        assertTrue(result.unresolvedTables().contains("p.d.src"),
                "the skipped dependency must be reported too");
    }

    @Test
    void starExpandsStructColumnsRecursivelyIntoDottedPaths() {
        String sql = "SELECT * FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "copy", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("*", List.of(new InputColumn(null, "*", Confidence.STAR, true)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        ColumnInfo leaf = new ColumnInfo("city", "STRING", "NULLABLE", null);
        ColumnInfo innerStruct = new ColumnInfo("geo", "RECORD", "NULLABLE", null,
                List.of(new ColumnInfo("lat", "FLOAT64", "NULLABLE", null)));
        ColumnInfo address = new ColumnInfo("address", "RECORD", "NULLABLE", null, List.of(leaf, innerStruct));
        ColumnInfo id = new ColumnInfo("id", "INT64", "NULLABLE", null);
        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(id, address),
                "p.d.copy", List.of(id, address));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        assertNotNull(result.column(new ColumnRef("p.d.copy", "id").id()));
        assertNotNull(result.column(new ColumnRef("p.d.copy", "address.city").id()));
        assertNotNull(result.column(new ColumnRef("p.d.copy", "address.geo.lat").id()));
        assertNull(result.column(new ColumnRef("p.d.copy", "address").id()),
                "the struct container itself is not a leaf column");
        assertTrue(result.upstream(new ColumnRef("p.d.copy", "address.geo.lat").id())
                .contains(new ColumnRef("p.d.src", "address.geo.lat").id()));
    }

    @Test
    void assertionColumnsAreExcludedFromLineage() {
        String sql = "SELECT id AS invalid_id FROM p.d.src";
        CompiledGraph graph = graph("[]",
                "[" + tableJson("p", "d", "assert_src", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("invalid_id", List.of(new InputColumn(null, "id", Confidence.RENAME, false)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.src", List.of(new ColumnInfo("id", "INT64", "NULLABLE", null)),
                "p.d.assert_src", List.of(new ColumnInfo("invalid_id", "INT64", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        assertNull(result.column(new ColumnRef("p.d.assert_src", "invalid_id").id()),
                "assertion columns must not be seeded into the column lineage graph");
        assertTrue(result.upstream(new ColumnRef("p.d.assert_src", "invalid_id").id()).isEmpty(),
                "no column edge must be built for an assertion");
    }

    @Test
    void qualifiedColumnWithBacktickedAliasResolvesToItsOwnDependency() {
        String sql = "SELECT c.full_name FROM `p.d.customers` AS c JOIN `p.d.summary` AS s ON TRUE";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "ltv", sql, "["
                + targetJson("p", "d", "customers") + "," + targetJson("p", "d", "summary") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("full_name",
                        List.of(new InputColumn("c", "full_name", Confidence.DIRECT, false)))),
                Map.of(sql, Map.of("c", "`p.d.customers`", "s", "`p.d.summary`")));

        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.customers", List.of(new ColumnInfo("full_name", "STRING", "NULLABLE", null)),
                "p.d.summary", List.of(new ColumnInfo("full_name", "STRING", "NULLABLE", null)),
                "p.d.ltv", List.of(new ColumnInfo("full_name", "STRING", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        String output = new ColumnRef("p.d.ltv", "full_name").id();
        assertTrue(result.upstream(output).contains(new ColumnRef("p.d.customers", "full_name").id()),
                "a backticked alias must resolve to its own dependency");
        assertFalse(result.upstream(output).contains(new ColumnRef("p.d.summary", "full_name").id()),
                "a column qualified by an alias must not be attributed to unrelated dependencies");
    }

    @Test
    void columnsAreNeverInventedOnATableThatDoesNotDeclareThem() {
        String sql = "SELECT c.full_name, s.order_amount FROM `p.d.customers` AS c, `p.d.summary` AS s";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "ltv", sql, "["
                + targetJson("p", "d", "customers") + "," + targetJson("p", "d", "summary") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of(
                        "full_name", List.of(new InputColumn("unknown", "full_name", Confidence.DIRECT, false)),
                        "order_amount", List.of(new InputColumn("s", "order_amount", Confidence.DIRECT, false)))),
                Map.of(sql, Map.of("c", "`p.d.customers`", "s", "`p.d.summary`")));

        Map<String, List<ColumnInfo>> schemas = Map.of(
                "p.d.customers", List.of(new ColumnInfo("full_name", "STRING", "NULLABLE", null)),
                "p.d.summary", List.of(new ColumnInfo("order_amount", "NUMERIC", "NULLABLE", null)),
                "p.d.ltv", List.of(new ColumnInfo("full_name", "STRING", "NULLABLE", null),
                        new ColumnInfo("order_amount", "NUMERIC", "NULLABLE", null)));

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);

        assertEquals(List.of("order_amount"),
                result.columnsForTable(new ColumnRef("p.d.summary", "x").tableNodeId()).stream()
                        .map(ColumnRef::columnName).toList(),
                "a table exposes exactly the columns of its resolved schema, whatever the SQL analysis says");
        assertNull(result.column(new ColumnRef("p.d.summary", "full_name").id()),
                "an unresolvable alias must not fabricate a column on a dependency");
    }

    @Test
    void starWithSchemaExpandsToEachColumn() {
        String sql = "SELECT * FROM p.d.src";
        CompiledGraph graph = graph("["
                + tableJson("p", "d", "copy", sql, "[" + targetJson("p", "d", "src") + "]") + "]");

        SelectAnalyzer analyzer = analyzer(
                Map.of(sql, Map.of("*", List.of(new InputColumn(null, "*", Confidence.STAR, true)))),
                Map.of(sql, Map.of("p.d.src", "p.d.src")));

        List<ColumnInfo> columns = List.of(
                new ColumnInfo("id", "INT64", "NULLABLE", null),
                new ColumnInfo("amount", "NUMERIC", "NULLABLE", null));
        Map<String, List<ColumnInfo>> schemas = Map.of("p.d.src", columns, "p.d.copy", columns);

        ColumnLineageGraph result = new ColumnLineageExtractorImpl(analyzer).extract(graph, schemas);
        assertNotNull(result.column(new ColumnRef("p.d.src", "id").id()));
        assertNotNull(result.column(new ColumnRef("p.d.src", "amount").id()));
    }
}
