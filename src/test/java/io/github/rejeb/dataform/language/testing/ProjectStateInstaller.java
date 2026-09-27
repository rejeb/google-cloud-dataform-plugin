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
package io.github.rejeb.dataform.language.testing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.GraphErrors;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Installs a compiled graph or extracted schemas on the shared light project for one test and puts
 * the previous ones back when the test's disposable is disposed, so no test leaks them to the next.
 */
public final class ProjectStateInstaller {

    private static final String EMPTY_SCHEMAS = "{}";

    private ProjectStateInstaller() {
    }

    /**
     * Installs the graph, giving it empty graph errors when it has none.
     */
    public static void installGraph(@NotNull Project project, @NotNull Disposable disposable,
                                    @Nullable CompiledGraph graph) {
        DataformCompilationService service = project.getService(DataformCompilationService.class);
        CompiledGraph previous = service.getCompiledGraph();
        Disposer.register(disposable, () -> setGraph(service, previous));
        if (graph != null && graph.getGraphErrors() == null) {
            graph.setGraphErrors(new GraphErrors());
        }
        setGraph(service, graph);
    }

    /**
     * Loads the schema cache JSON into the schema service.
     */
    public static void installSchemas(@NotNull Project project, @NotNull Disposable disposable,
                                      @NotNull String schemaCacheJson) {
        DataformTableSchemaService service = DataformTableSchemaService.getInstance(project);
        DataformTableSchemaService.State previous = service.getState();
        Disposer.register(disposable, () -> service.loadState(restorable(previous)));
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = schemaCacheJson;
        service.loadState(state);
    }

    private static DataformTableSchemaService.State restorable(@Nullable DataformTableSchemaService.State previous) {
        if (previous != null && previous.schemaCacheJson != null && !previous.schemaCacheJson.isBlank()) {
            return previous;
        }
        DataformTableSchemaService.State empty = new DataformTableSchemaService.State();
        empty.schemaCacheJson = EMPTY_SCHEMAS;
        return empty;
    }

    private static void setGraph(@NotNull DataformCompilationService service, @Nullable CompiledGraph graph) {
        try {
            Field field = service.getClass().getDeclaredField("compiledGraph");
            field.setAccessible(true);
            field.set(service, graph);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
