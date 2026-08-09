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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link CompilationDiagnosticService} backed by the cached compiled graph.
 */
public final class CompilationDiagnosticServiceImpl implements CompilationDiagnosticService {

    private final Project project;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CompilationDiagnosticServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    private record CacheEntry(int graphIdentity, List<CompilationDiagnostic> diagnostics) {
    }

    @Override
    public @NotNull List<CompilationDiagnostic> getDiagnostics(@NotNull VirtualFile file) {
        if (!DataformProjectLayout.isInDataformProject(file)) {
            return List.of();
        }
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        int graphIdentity = System.identityHashCode(graph);

        CacheEntry cached = cache.get(file.getPath());
        if (cached != null && cached.graphIdentity() == graphIdentity) {
            return cached.diagnostics();
        }
        List<CompilationDiagnostic> computed = compute(file, graph);
        cache.put(file.getPath(), new CacheEntry(graphIdentity, computed));
        return computed;
    }

    @Override
    public void invalidate() {
        cache.clear();
    }

    private List<CompilationDiagnostic> compute(@NotNull VirtualFile file,
                                                @Nullable CompiledGraph graph) {
        if (graph == null || graph.getGraphErrors() == null) {
            return List.of();
        }
        List<CompilationError> errors =
                graph.findCompilationErrorByFileName(relativePath(file));
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        List<CompilationDiagnostic> result = new ArrayList<>();
        for (CompilationError error : errors) {
            result.add(new CompilationDiagnostic(file,
                    error.getMessage() != null ? error.getMessage() : "Compilation error",
                    error.getActionName()));
        }
        return List.copyOf(result);
    }

    private @NotNull String relativePath(@NotNull VirtualFile file) {
        String basePath = project.getBasePath();
        String path = file.getPath();
        if (basePath != null && path.startsWith(basePath)) {
            return path.substring(basePath.length()).replaceAll("^/", "");
        }
        return file.getName();
    }
}
