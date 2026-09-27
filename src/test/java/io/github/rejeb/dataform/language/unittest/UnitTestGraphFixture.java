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
package io.github.rejeb.dataform.language.unittest;

import com.google.gson.Gson;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;

import java.lang.reflect.Field;

public final class UnitTestGraphFixture {

    static final String GRAPH = """
            {
              "tables": [
                {"type": "table", "target": {"database": "p", "schema": "d", "name": "orders"},
                 "fileName": "definitions/orders.sqlx", "disabled": false,
                 "dependencyTargets": [
                   {"database": "p", "schema": "raw", "name": "raw_orders"},
                   {"database": "p", "schema": "d", "name": "customers"}]},
                {"type": "view", "target": {"database": "p", "schema": "d", "name": "customers"},
                 "fileName": "definitions/customers.sqlx", "disabled": false},
                {"type": "table", "target": {"database": "p", "schema": "d", "name": "stats"},
                 "fileName": "definitions/stats.sqlx", "disabled": false},
                {"type": "view", "target": {"database": "p", "schema": "d", "name": "hidden"},
                 "fileName": "definitions/hidden.sqlx", "disabled": true},
                {"type": "incremental", "target": {"database": "p", "schema": "d", "name": "events"},
                 "fileName": "definitions/events.sqlx", "disabled": false},
                {"type": "table", "target": {"database": "p", "schema": "d", "name": "js_table"},
                 "fileName": "definitions/multi.js", "disabled": false},
                {"type": "view", "target": {"database": "p", "schema": "d", "name": "js_view"},
                 "fileName": "definitions/multi.js", "disabled": false},
                {"type": "incremental", "target": {"database": "p", "schema": "d", "name": "js_incremental"},
                 "fileName": "definitions/multi.js", "disabled": false}
              ],
              "operations": [
                {"target": {"database": "p", "schema": "d", "name": "cleanup"},
                 "fileName": "definitions/cleanup.sqlx"}
              ],
              "declarations": [
                {"target": {"database": "p", "schema": "raw", "name": "raw_orders"},
                 "fileName": "definitions/sources.js"}
              ],
              "graphErrors": {"compilationErrors": []}
            }""";

    private UnitTestGraphFixture() {
    }

    public static void install(Project project, Disposable disposable) {
        CompiledGraph previous = project.getService(DataformCompilationService.class).getCompiledGraph();
        Disposer.register(disposable, () -> set(project, previous));
        set(project, new Gson().fromJson(GRAPH, CompiledGraph.class));
    }

    public static void installWithBackslashes(Project project, Disposable disposable) {
        CompiledGraph previous = project.getService(DataformCompilationService.class).getCompiledGraph();
        Disposer.register(disposable, () -> set(project, previous));
        set(project, new Gson().fromJson(GRAPH.replace("definitions/", "definitions\\\\"), CompiledGraph.class));
    }

    public static void clear(Project project) {
        set(project, null);
    }

    private static void set(Project project, CompiledGraph graph) {
        try {
            DataformCompilationService service = project.getService(DataformCompilationService.class);
            Field field = service.getClass().getDeclaredField("compiledGraph");
            field.setAccessible(true);
            field.set(service, graph);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
