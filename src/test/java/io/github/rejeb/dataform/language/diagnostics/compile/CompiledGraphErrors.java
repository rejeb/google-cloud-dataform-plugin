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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.GraphErrors;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.diagnostics.compile.CompilationProblemsService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs a compiled graph reporting given compilation errors, the way a failed compilation leaves it.
 */
public final class CompiledGraphErrors {

    private CompiledGraphErrors() {
    }

    public static CompilationError error(String fileName, String actionName, String message, String stack) {
        CompilationError error = new CompilationError();
        set(error, "fileName", fileName);
        set(error, "actionName", actionName);
        set(error, "message", message);
        set(error, "stack", stack);
        return error;
    }

    public static void install(Project project, List<String> actionNames, CompilationError... errors) {
        List<CompiledTable> tables = new ArrayList<>();
        for (String name : actionNames) {
            Target target = new Target();
            set(target, "database", "proj");
            set(target, "schema", "ds");
            set(target, "name", name);
            CompiledTable table = new CompiledTable();
            set(table, "type", "table");
            set(table, "target", target);
            set(table, "fileName", "definitions/" + name + ".sqlx");
            set(table, "tags", List.of());
            set(table, "dependencyTargets", List.of());
            tables.add(table);
        }
        CompiledGraph graph = new CompiledGraph();
        set(graph, "tables", tables);
        set(graph, "operations", List.of());
        set(graph, "declarations", List.of());
        set(graph, "assertions", List.of());
        GraphErrors graphErrors = new GraphErrors();
        graphErrors.setCompilationErrors(List.of(errors));
        graph.setGraphErrors(graphErrors);
        set(project.getService(DataformCompilationService.class), "compiledGraph", graph);
        CompilationProblemsService.getInstance(project).invalidate();
    }

    public static void clear(Project project) {
        set(project.getService(DataformCompilationService.class), "compiledGraph", null);
        CompilationProblemsService.getInstance(project).invalidate();
    }

    private static void set(Object target, String field, Object value) {
        try {
            Class<?> type = target.getClass();
            while (type != null) {
                try {
                    Field f = type.getDeclaredField(field);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                }
            }
            throw new IllegalStateException("no field " + field);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("set " + field, e);
        }
    }
}
