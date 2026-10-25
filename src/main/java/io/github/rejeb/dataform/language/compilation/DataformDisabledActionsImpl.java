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
import com.intellij.util.PathUtil;
import io.github.rejeb.dataform.language.util.DataformPaths;
import io.github.rejeb.dataform.language.SqlxFileType;
import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
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
    private volatile Map<String, List<ActionState>> actionsByName = Map.of();

    public DataformDisabledActionsImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public boolean isDisabled(@NotNull VirtualFile file) {
        if (!SqlxFileType.INSTANCE.getDefaultExtension().equals(file.getExtension())) return false;

        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return false;
        Map<String, List<ActionState>> index = indexFor(graph);
        return byPath.computeIfAbsent(file.getPath(), path -> allDisabled(index, path));
    }

    private @NotNull Map<String, List<ActionState>> indexFor(@NotNull CompiledGraph graph) {
        if (graph != cachedGraph) {
            synchronized (byPath) {
                if (graph != cachedGraph) {
                    actionsByName = indexByName(graph);
                    byPath.clear();
                    cachedGraph = graph;
                }
            }
        }
        return actionsByName;
    }

    /**
     * Whether the given file defines at least one action and all of them are disabled. Assertions
     * are included: a file whose only action is a disabled assertion is disabled too.
     */
    static boolean allActionsDisabled(@Nullable CompiledGraph graph, @NotNull String path) {
        if (graph == null) return false;
        return allDisabled(indexByName(graph), path);
    }

    private static boolean allDisabled(@NotNull Map<String, List<ActionState>> index, @NotNull String path) {
        boolean found = false;
        for (ActionState action : index.getOrDefault(PathUtil.getFileName(DataformPaths.normalize(path)), List.of())) {
            if (DataformPaths.pointsTo(path, action.fileName())) {
                if (!action.disabled()) return false;
                found = true;
            }
        }
        return found;
    }

    private static @NotNull Map<String, List<ActionState>> indexByName(@NotNull CompiledGraph graph) {
        Map<String, List<ActionState>> index = new HashMap<>();
        for (ActionState action : actionsOf(graph)) {
            if (action.fileName() == null) continue;
            index.computeIfAbsent(PathUtil.getFileName(DataformPaths.normalize(action.fileName())),
                    name -> new ArrayList<>()).add(action);
        }
        return index;
    }

    private static @NotNull List<ActionState> actionsOf(@NotNull CompiledGraph graph) {
        List<ActionState> actions = new ArrayList<>();
        for (CompiledTable table : graph.getTables()) {
            actions.add(new ActionState(table.getFileName(), table.isDisabled()));
        }
        for (CompiledOperation operation : graph.getOperations()) {
            actions.add(new ActionState(operation.getFileName(), operation.isDisabled()));
        }
        for (CompiledAssertion assertion : graph.getAssertions()) {
            actions.add(new ActionState(assertion.getFileName(), assertion.isDisabled()));
        }
        return actions;
    }

    private record ActionState(@Nullable String fileName, boolean disabled) {
    }
}
