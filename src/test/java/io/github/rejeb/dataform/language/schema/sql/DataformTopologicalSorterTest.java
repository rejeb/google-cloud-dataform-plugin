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
package io.github.rejeb.dataform.language.schema.sql;

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataformTopologicalSorterTest {

    @Test
    public void dependenciesComeBeforeTheirDependents() {
        List<String> order = names(DataformTopologicalSorter.sort(graph("""
                {
                  "tables": [
                    {"target": %s, "dependencyTargets": [%s, %s]},
                    {"target": %s, "dependencyTargets": [%s]},
                    {"target": %s, "dependencyTargets": []}
                  ],
                  "operations": [],
                  "declarations": []
                }
                """.formatted(t("c"), t("a"), t("b"), t("b"), t("a"), t("a")))));
        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    public void operationsWithOutputTakePartOthersAreLeftOut() {
        List<String> order = names(DataformTopologicalSorter.sort(graph("""
                {
                  "tables": [
                    {"target": %s, "dependencyTargets": [%s]}
                  ],
                  "operations": [
                    {"target": %s, "hasOutput": true, "disabled": false, "dependencyTargets": []},
                    {"target": %s, "hasOutput": false, "disabled": false, "dependencyTargets": []},
                    {"target": %s, "hasOutput": true, "disabled": true, "dependencyTargets": []}
                  ],
                  "declarations": []
                }
                """.formatted(t("t"), t("op"), t("op"), t("silent"), t("off")))));
        assertEquals(List.of("op", "t"), order);
    }

    @Test
    public void declarationsAreAppendedAfterEveryAction() {
        List<SortableAction> sorted = DataformTopologicalSorter.sort(graph("""
                {
                  "tables": [
                    {"target": %s, "dependencyTargets": [%s]}
                  ],
                  "operations": [],
                  "declarations": [
                    {"target": %s}
                  ]
                }
                """.formatted(t("t"), t("src"), t("src"))));
        assertEquals(List.of("t", "src"), names(sorted));
        assertTrue(sorted.get(0).isTable());
        assertTrue(sorted.get(1).isDeclaration());
    }

    @Test
    public void unknownDependenciesAreIgnored() {
        List<String> order = names(DataformTopologicalSorter.sort(graph("""
                {
                  "tables": [
                    {"target": %s, "dependencyTargets": [%s]}
                  ],
                  "operations": [],
                  "declarations": []
                }
                """.formatted(t("t"), t("external")))));
        assertEquals(List.of("t"), order);
    }

    @Test
    public void cyclesStillYieldEveryAction() {
        List<String> order = names(DataformTopologicalSorter.sort(graph("""
                {
                  "tables": [
                    {"target": %s, "dependencyTargets": [%s]},
                    {"target": %s, "dependencyTargets": [%s]},
                    {"target": %s, "dependencyTargets": []}
                  ],
                  "operations": [],
                  "declarations": []
                }
                """.formatted(t("a"), t("b"), t("b"), t("a"), t("free")))));
        assertEquals(3, order.size());
        assertEquals("free", order.get(0));
        assertTrue(order.containsAll(List.of("a", "b")));
    }

    @Test
    public void emptyGraphSortsToNothing() {
        assertTrue(DataformTopologicalSorter.sort(graph(
                "{\"tables\": [], \"operations\": [], \"declarations\": []}")).isEmpty());
    }

    private static String t(String name) {
        return "{\"database\": \"p\", \"schema\": \"d\", \"name\": \"" + name + "\"}";
    }

    private static CompiledGraph graph(String json) {
        return new Gson().fromJson(json, CompiledGraph.class);
    }

    private static List<String> names(List<SortableAction> actions) {
        return actions.stream().map(a -> a.target().getName()).toList();
    }
}
