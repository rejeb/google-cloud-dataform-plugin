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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.SqlxFileType;
import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the disabled flag of the actions of the last compiled graph. A file is disabled when every
 * action it defines is disabled, so a file mixing a disabled table with an enabled one is not
 * reported as disabled.
 */
public final class DataformDisabledActionsImpl implements DataformDisabledActions {

    private final Project project;
    private final Map<String, Boolean> byPath = new ConcurrentHashMap<>();

    private volatile CompiledGraph cachedGraph;

    public DataformDisabledActionsImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public boolean isDisabled(@NotNull VirtualFile file) {
        if (!SqlxFileType.INSTANCE.getDefaultExtension().equals(file.getExtension())) return false;

        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return false;
        if (graph != cachedGraph) {
            byPath.clear();
            cachedGraph = graph;
        }
        return byPath.computeIfAbsent(file.getPath(), path -> allActionsDisabled(graph, path));
    }

    /**
     * Whether the given file defines at least one action and all of them are disabled. Assertions
     * are included: a file whose only action is a disabled assertion is disabled too.
     */
    static boolean allActionsDisabled(@Nullable CompiledGraph graph, @NotNull String path) {
        if (graph == null) return false;
        String normalized = path.replace("\\", "/");
        List<Boolean> states = new ArrayList<>();
        for (CompiledTable table : graph.getTables()) {
            if (matches(table.getFileName(), normalized)) states.add(table.isDisabled());
        }
        for (CompiledOperation operation : graph.getOperations()) {
            if (matches(operation.getFileName(), normalized)) states.add(operation.isDisabled());
        }
        for (CompiledAssertion assertion : graph.getAssertions()) {
            if (matches(assertion.getFileName(), normalized)) states.add(assertion.isDisabled());
        }
        return !states.isEmpty() && states.stream().allMatch(Boolean::booleanValue);
    }

    private static boolean matches(@Nullable String actionFileName, @NotNull String path) {
        if (actionFileName == null || actionFileName.isBlank()) return false;
        return path.endsWith(actionFileName.replace("\\", "/"));
    }
}
