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
package io.github.rejeb.dataform.language.unittest.creation;

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFileContentTest {

    private static final String GRAPH = """
            {"tables": [
              {"type": "table", "target": {"database": "p", "schema": "d", "name": "pre_orders"},
               "canonicalTarget": {"database": "p", "schema": "d", "name": "orders"},
               "fileName": "definitions/orders.sqlx",
               "dependencyTargets": [
                 {"database": "p", "schema": "raw", "name": "raw_orders"},
                 {"database": "p", "schema": "d", "name": "customers"},
                 {"database": "p", "schema": "raw", "name": "raw_orders"}]},
              {"type": "view", "target": {"database": "p", "schema": "d", "name": "customers"},
               "fileName": "definitions/customers.sqlx"},
              {"type": "view", "target": {"database": "p", "schema": "other", "name": "customers"},
               "fileName": "definitions/other.sqlx"}],
             "declarations": [
              {"target": {"database": "p", "schema": "raw", "name": "raw_orders"}, "fileName": "definitions/sources.js"}]}
            """;

    private static final List<ColumnInfo> ORDERS = List.of(new ColumnInfo("order_id", "STRING", "NULLABLE", null));

    private final CompiledGraph graph = new Gson().fromJson(GRAPH, CompiledGraph.class);

    private CompiledTable orders() {
        return graph.getTables().get(0);
    }

    @Test
    void theCanonicalNameNamesTheFileAndTheDataset() {
        Target tested = TestFileNames.testedTarget(orders());
        assertEquals("orders", tested.getName());
        assertEquals("test_orders.sqlx", TestFileNames.fileName(tested, false));
    }

    @Test
    void anAmbiguousNameIsQualifiedBySchema() {
        Target customers = graph.getTables().get(1).getTarget();
        assertTrue(TestFileNames.isAmbiguous(graph, customers));
        assertFalse(TestFileNames.isAmbiguous(graph, orders().getDependencyTargets().get(0)));
        assertEquals("test_d_customers.sqlx", TestFileNames.fileName(customers, true));
    }

    @Test
    void theContentHasConfigOneInputPerDependencyAndTheExpectedOutput() {
        CompiledTable orders = orders();
        Target tested = TestFileNames.testedTarget(orders);
        Map<Target, List<ColumnInfo>> schemas = Map.of(
                tested, ORDERS, orders.getDependencyTargets().get(0), UnitTestSchemaFixture.RAW_ORDERS);
        TestInputLabels labels = TestInputLabels.of(graph, null);
        String content = TestFileContent.of(tested, orders.getDependencyTargets(),
                target -> labels.partsOf(target, TestFileNames.isAmbiguous(graph, target)),
                target -> schemas.getOrDefault(target, List.of()));
        assertEquals("""
                config {
                  type: "test",
                  dataset: {
                    schema: "d",
                    name: "orders"
                  }
                }

                input "raw_orders" {
                  SELECT
                    '' AS id,
                    0.0 AS amount
                }

                -- TODO: schema of customers not extracted yet
                input "d", "customers" {
                  SELECT
                    1 AS id
                }

                -- Expected output
                SELECT
                  '' AS order_id
                """, content);
    }

    @Test
    void anInputIsLabelledAsTheTestedQueryWroteItsRef() {
        TestInputLabels labels = TestInputLabels.of(graph,
                "SELECT * FROM ${ref(\"raw\", \"raw_orders\")} JOIN ${ctx.ref('customers')}");
        List<Target> dependencies = orders().getDependencyTargets();

        assertEquals(List.of("raw", "raw_orders"), labels.partsOf(dependencies.get(0), false));
        assertEquals(List.of("customers"), labels.partsOf(dependencies.get(1), true));
    }

    @Test
    void aRefToAPrefixedTableKeepsTheNameTheQueryWrote() {
        CompiledGraph prefixed = new Gson().fromJson("""
                {"tables": [
                  {"type": "view", "target": {"database": "p", "schema": "d", "name": "dev_customers"},
                   "canonicalTarget": {"database": "p", "schema": "d", "name": "customers"},
                   "fileName": "definitions/customers.sqlx"}]}
                """, CompiledGraph.class);
        Target compiled = prefixed.getTables().get(0).getTarget();

        TestInputLabels labels = TestInputLabels.of(prefixed, "SELECT * FROM ${ref(\"customers\")}");

        assertEquals(List.of("customers"), labels.partsOf(compiled, false));
    }

    @Test
    void anInputWithoutRefFallsBackToItsName() {
        TestInputLabels labels = TestInputLabels.of(graph, "SELECT 1");
        Target customers = graph.getTables().get(1).getTarget();

        assertEquals(List.of("d", "customers"), labels.partsOf(customers, true));
        assertEquals(List.of("customers"), labels.partsOf(customers, false));
    }
}
