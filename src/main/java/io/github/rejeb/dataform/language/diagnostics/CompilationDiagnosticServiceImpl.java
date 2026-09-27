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
import io.github.rejeb.dataform.language.diagnostics.compile.CompilationErrorParser;
import io.github.rejeb.dataform.language.diagnostics.compile.ParsedCompilationError;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link CompilationDiagnosticService} backed by the cached compiled graph.
 */
public final class CompilationDiagnosticServiceImpl implements CompilationDiagnosticService {

    private final Project project;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> raisedCache = new ConcurrentHashMap<>();
    private final AtomicLong modificationCount = new AtomicLong();

    public CompilationDiagnosticServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    private record GraphStamp(int identity, long modificationCount) {
    }

    private record CacheEntry(GraphStamp graph, List<CompilationDiagnostic> diagnostics) {
    }

    private @NotNull GraphStamp stampOf(@Nullable CompiledGraph graph) {
        return new GraphStamp(System.identityHashCode(graph),
                DataformCompilationService.getInstance(project).getModificationCount());
    }

    @Override
    public @NotNull List<CompilationDiagnostic> getDiagnostics(@NotNull VirtualFile file) {
        if (!DataformProjectLayout.isInDataformProject(file)) {
            return List.of();
        }
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        GraphStamp stamp = stampOf(graph);

        CacheEntry cached = cache.get(file.getPath());
        if (cached != null && cached.graph().equals(stamp)) {
            return cached.diagnostics();
        }
        List<CompilationDiagnostic> computed = compute(file, graph);
        cache.put(file.getPath(), new CacheEntry(stamp, computed));
        return computed;
    }

    @Override
    public void invalidate() {
        cache.clear();
        raisedCache.clear();
        modificationCount.incrementAndGet();
    }

    @Override
    public @NotNull List<CompilationDiagnostic> getDiagnosticsRaisedIn(@NotNull VirtualFile file) {
        if (!DataformProjectLayout.isInDataformProject(file)) {
            return List.of();
        }
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null || graph.getGraphErrors() == null || graph.getGraphErrors().getCompilationErrors() == null) {
            return List.of();
        }
        GraphStamp stamp = stampOf(graph);
        CacheEntry cached = raisedCache.get(file.getPath());
        if (cached != null && cached.graph().equals(stamp)) {
            return cached.diagnostics();
        }
        List<CompilationDiagnostic> raised = new ArrayList<>();
        for (CompilationError error : graph.getGraphErrors().getCompilationErrors()) {
            if (error.getStack() == null || error.matchFileName(file.getPath())) {
                continue;
            }
            ParsedCompilationError parsed =
                    CompilationErrorParser.parse(error.getMessage(), error.getStack(), error.getFileName());
            if (parsed.indexOfFrameIn(file.getPath()) >= 0) {
                raised.add(diagnosticOf(file, error));
            }
        }
        List<CompilationDiagnostic> result = List.copyOf(raised);
        raisedCache.put(file.getPath(), new CacheEntry(stamp, result));
        return result;
    }

    /**
     * Moves on every {@link #invalidate()} and on every change of the compiled graph, so what is
     * computed from the diagnostics follows a compilation whoever ran it.
     */
    @Override
    public long getModificationCount() {
        return modificationCount.get() + DataformCompilationService.getInstance(project).getModificationCount();
    }

    private static @NotNull CompilationDiagnostic diagnosticOf(@NotNull VirtualFile file, @NotNull CompilationError error) {
        return new CompilationDiagnostic(file,
                error.getMessage() != null ? error.getMessage() : "Compilation error",
                error.getActionName(), error.getStack(), error.getFileName());
    }

    private List<CompilationDiagnostic> compute(@NotNull VirtualFile file,
                                                @Nullable CompiledGraph graph) {
        if (graph == null || graph.getGraphErrors() == null) {
            return List.of();
        }
        List<CompilationError> errors =
                graph.findCompilationErrorByFileName(file.getPath());
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        List<CompilationDiagnostic> result = new ArrayList<>();
        for (CompilationError error : errors) {
            result.add(diagnosticOf(file, error));
        }
        return List.copyOf(result);
    }
}
