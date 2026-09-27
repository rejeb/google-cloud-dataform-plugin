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
package io.github.rejeb.dataform.language.compilation.model;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledGraphTestsTest {

    private static final String GRAPH = """
            {"tests": [
              {"name": "orders_test", "testQuery": " SELECT 1 AS id ", "expectedOutputQuery": "SELECT 1 AS id\\n",
               "fileName": "definitions\\\\tests\\\\orders_test.sqlx", "disabled": false, "tags": ["unit"]},
              {"name": "stats_test", "testQuery": "SELECT 2", "expectedOutputQuery": "SELECT 2",
               "fileName": "definitions/stats_test.sqlx", "disabled": true}
            ]}""";

    @Test
    void readsTheTestsOfTheCompiledGraph() {
        CompiledGraph graph = new Gson().fromJson(GRAPH, CompiledGraph.class);

        assertEquals(2, graph.getTests().size());
        CompiledTest orders = graph.getTests().getFirst();
        assertEquals("orders_test", orders.getName());
        assertEquals("SELECT 1 AS id", orders.getTestQuery());
        assertEquals("SELECT 1 AS id", orders.getExpectedOutputQuery());
        assertEquals("definitions/tests/orders_test.sqlx", orders.getFileName());
        assertEquals(List.of("unit"), orders.getTags());
        assertTrue(graph.getTests().get(1).isDisabled());
    }

    @Test
    void findsTestsByFileNameWhateverTheSeparator() {
        CompiledGraph graph = new Gson().fromJson(GRAPH, CompiledGraph.class);

        assertEquals(1, graph.findTestByFileName("/home/u/p/definitions/tests/orders_test.sqlx").size());
        assertEquals(1, graph.findTestByFileName("C:\\p\\definitions\\stats_test.sqlx").size());
        assertEquals(0, graph.findTestByFileName("/home/u/p/definitions/old_orders_test.sqlx").size());
    }

    @Test
    void aGraphWithoutTestsHasNone() {
        assertEquals(List.of(), new Gson().fromJson("{}", CompiledGraph.class).getTests());
    }
}
