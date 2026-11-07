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
package io.github.rejeb.dataform.language.fileEditor;

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreviewQueriesTest {

    private static final String ORDERS = "definitions/orders.sqlx";

    private static CompiledGraph graph(String json) {
        return new Gson().fromJson(json, CompiledGraph.class);
    }

    @Test
    void testExecutableReturnsTheCompiledQueriesOfTheFile() {
        CompiledGraph graph = graph("{\"tables\":[{\"type\":\"table\",\"fileName\":\"" + ORDERS + "\","
                + "\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"orders\"},\"query\":\"SELECT 1\"}]}");

        List<FormattedCompiledQuery> queries = PreviewQueries.executable(graph, ORDERS, false);

        assertEquals(1, queries.size());
        assertEquals("SELECT 1", queries.getFirst().query());
    }

    @Test
    void testExecutableIsEmptyForAFileWithoutAction() {
        CompiledGraph graph = graph("{\"tables\":[]}");

        assertTrue(PreviewQueries.executable(graph, ORDERS, false).isEmpty());
    }

    @Test
    void testErrorsOfJoinsTheMessagesOfTheFile() {
        CompiledGraph graph = graph("{\"graphErrors\":{\"compilationErrors\":["
                + "{\"fileName\":\"" + ORDERS + "\",\"message\":\"a\"},"
                + "{\"fileName\":\"" + ORDERS + "\",\"message\":\"b\"}]}}");

        assertEquals("a\nb", PreviewQueries.errorsOf(graph, ORDERS));
        assertNull(PreviewQueries.errorsOf(graph, "definitions/other.sqlx"));
    }

    @Test
    void testFormatCountsTheStatementsAndKeepsTheType() {
        CompiledQuery query = new CompiledQuery("p.ds.users", List.of("SET a = 1", "SET b = 2"), List.of(),
                "SELECT 1", List.of("DROP x"), null, false);

        FormattedCompiledQuery formatted = PreviewQueries.format(query, "incremental", String::toLowerCase);

        assertEquals("incremental", formatted.type());
        assertEquals(2, formatted.preOpsCount());
        assertEquals(0, formatted.incrementalPreOpsCount());
        assertEquals(1, formatted.postOpsCount());
        assertEquals("set a = 1\nset b = 2", formatted.preOps());
        assertEquals("select 1", formatted.query());
    }

    private static final String ASSERTIONS_GRAPH = "{\"tables\":[{\"type\":\"table\",\"fileName\":\"" + ORDERS + "\","
            + "\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"orders\"},\"query\":\"SELECT 1\"}],"
            + "\"assertions\":[{\"fileName\":\"" + ORDERS + "\","
            + "\"target\":{\"database\":\"p\",\"schema\":\"d_assertions\",\"name\":\"d_orders_assertions_uniqueKey_0\"},"
            + "\"parentAction\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"orders\"},\"query\":\"SELECT 2\"},"
            + "{\"fileName\":\"" + ORDERS + "\","
            + "\"target\":{\"database\":\"p\",\"schema\":\"d_assertions\",\"name\":\"orders_check\"},\"query\":\"SELECT 3\"}]}";

    @Test
    void testAssertionParentsMapsTheAutoAssertionsOfTheFileToTheirTable() {
        Map<String, String> parents = PreviewQueries.assertionParents(graph(ASSERTIONS_GRAPH), ORDERS);

        assertEquals(Map.of("p.d_assertions.d_orders_assertions_uniqueKey_0", "p.d.orders"), parents);
        assertTrue(PreviewQueries.assertionParents(graph(ASSERTIONS_GRAPH), "definitions/other.sqlx").isEmpty());
    }

    @Test
    void testGroupPutsTheAutoAssertionsUnderTheirTable() {
        FormattedCompiledQuery orders = query("p.d.orders");
        FormattedCompiledQuery uniqueKey = query("p.d_assertions.d_orders_assertions_uniqueKey_0");
        FormattedCompiledQuery check = query("p.d_assertions.orders_check");

        List<FormattedCompiledQuery> grouped = PreviewQueries.groupAssertions(List.of(orders, uniqueKey, check),
                Map.of("p.d_assertions.d_orders_assertions_uniqueKey_0", "p.d.orders"));

        assertEquals(List.of("p.d.orders", "p.d_assertions.orders_check"),
                grouped.stream().map(FormattedCompiledQuery::tableName).toList());
        assertEquals(List.of(uniqueKey), grouped.getFirst().assertions());
        assertTrue(grouped.get(1).assertions().isEmpty());
    }

    @Test
    void testAnAssertionWhoseTableIsNotShownStaysOnItsOwn() {
        FormattedCompiledQuery orders = query("p.d.orders");
        FormattedCompiledQuery uniqueKey = query("p.d_assertions.d_customers_assertions_uniqueKey_0");

        List<FormattedCompiledQuery> grouped = PreviewQueries.groupAssertions(List.of(orders, uniqueKey),
                Map.of("p.d_assertions.d_customers_assertions_uniqueKey_0", "p.d.customers"));

        assertEquals(List.of(orders, uniqueKey), grouped);
    }

    @Test
    void testTheShortNameOfAnAutoAssertionIsItsKind() {
        assertEquals("uniqueKey_0", PreviewQueries.assertionLabel("p.d_assertions.d_orders_assertions_uniqueKey_0"));
        assertEquals("rowConditions", PreviewQueries.assertionLabel("p.d_assertions.d_orders_assertions_rowConditions"));
        assertEquals("orders_check", PreviewQueries.assertionLabel("p.d_assertions.orders_check"));
    }

    @Test
    void testTheCostDryRunDeclaresTheVariablesOfThePreOperations() {
        CompiledQuery query = new CompiledQuery("p.gold.gold_products",
                List.of("declare my_int_var decimal;\nset my_int_var=(select 1 as my_int_var);"), List.of(),
                "SELECT * FROM `p.silver.items` oie\nwhere 1  = my_int_var", List.of(), null, false);

        assertEquals("declare my_int_var decimal;\nset my_int_var=(select 1 as my_int_var);\n"
                        + "SELECT * FROM `p.silver.items` oie\nwhere 1  = my_int_var;",
                PreviewQueries.costDryRunSql(query));
    }

    @Test
    void testTheCostDryRunLeavesOutThePreOperationsThatWriteToTheWarehouse() {
        CompiledQuery query = new CompiledQuery("p.ds.orders",
                List.of("DELETE FROM `p.ds.orders` WHERE true; CREATE TEMP FUNCTION f(x INT64) AS (x + 1)"),
                List.of(), "SELECT f(1)", List.of(), null, false);

        assertEquals("CREATE TEMP FUNCTION f(x INT64) AS (x + 1);\nSELECT f(1);", PreviewQueries.costDryRunSql(query));
    }

    @Test
    void testTheCostDryRunOfAQueryWithoutPreOperationsIsTheQuery() {
        CompiledQuery query = new CompiledQuery("p.ds.orders", "SELECT 1", false);

        assertEquals("SELECT 1", PreviewQueries.costDryRunSql(query));
    }

    @Test
    void testAnActionWithoutQueryHasNoCostDryRun() {
        assertNull(PreviewQueries.costDryRunSql(new CompiledQuery("p.ds.orders", List.of("SET a = 1"), List.of(),
                " ", List.of(), null, false)));
        assertNull(PreviewQueries.costDryRunSql(new CompiledQuery("p.ds.orders", List.of("boom"), false)));
    }

    private static FormattedCompiledQuery query(String name) {
        return new FormattedCompiledQuery(name, null, null, "SELECT 1", null, null);
    }
}
