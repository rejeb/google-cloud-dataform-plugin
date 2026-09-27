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
package io.github.rejeb.dataform.language.gcp.execution.unittest.engine;

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTestCasesTest {

    private static final CompiledGraph GRAPH = new Gson().fromJson("""
            {"tests": [
              {"name": "orders_test", "testQuery": "SELECT 1", "expectedOutputQuery": "SELECT 1",
               "fileName": "definitions\\\\tests\\\\orders_test.sqlx"},
              {"name": "stats_test", "testQuery": "SELECT 2", "expectedOutputQuery": "SELECT 2",
               "fileName": "definitions/stats_test.sqlx", "disabled": true}
            ],
             "graphErrors": {"compilationErrors": [
               {"fileName": "definitions/broken_test.sqlx", "message": "Input for dataset \\"raw\\" has not been provided."}
             ]}}""", CompiledGraph.class);

    private static List<String> names(List<UnitTestCase> cases) {
        return cases.stream().map(UnitTestCase::name).toList();
    }

    @Test
    void allScopeSelectsEveryTest() {
        assertEquals(List.of("orders_test", "stats_test"), names(UnitTestCases.select(GRAPH, DataformTestScope.ALL, "")));
    }

    @Test
    void directoryAndFileScopes() {
        assertEquals(List.of("orders_test"),
                names(UnitTestCases.select(GRAPH, DataformTestScope.DIRECTORY, "definitions/tests")));
        assertEquals(List.of("stats_test"),
                names(UnitTestCases.select(GRAPH, DataformTestScope.FILE, "definitions/stats_test.sqlx")));
        assertEquals(List.of("orders_test", "stats_test"),
                names(UnitTestCases.select(GRAPH, DataformTestScope.DIRECTORY, "")));
    }

    @Test
    void scopesMatchWindowsFileNames() {
        assertTrue(UnitTestCases.selects(DataformTestScope.FILE, "definitions\\tests\\orders_test.sqlx",
                "definitions\\tests\\orders_test.sqlx"));
        assertTrue(UnitTestCases.selects(DataformTestScope.DIRECTORY, "definitions", "definitions\\tests\\orders_test.sqlx"));
        assertFalse(UnitTestCases.selects(DataformTestScope.DIRECTORY, "defin", "definitions/tests/orders_test.sqlx"));
    }

    @Test
    void disabledTestsAreKeptAndFlagged() {
        assertTrue(UnitTestCases.select(GRAPH, DataformTestScope.FILE, "definitions/stats_test.sqlx").getFirst().disabled());
    }

    @Test
    void fileScopeReportsTheCompileErrorOfATestThatDidNotCompile() {
        List<UnitTestCase> cases = UnitTestCases.select(GRAPH, DataformTestScope.FILE, "definitions/broken_test.sqlx");

        assertEquals(List.of("broken_test"), names(cases));
        assertEquals("Input for dataset \"raw\" has not been provided.", cases.getFirst().compilationError());
    }

    @Test
    void anUnknownFileSelectsNothing() {
        assertEquals(List.of(), UnitTestCases.select(GRAPH, DataformTestScope.FILE, "definitions/nope.sqlx"));
    }
}
