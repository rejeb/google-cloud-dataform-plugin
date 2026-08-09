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

import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.GraphErrors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the per-action failures of a compiled graph. A compilation error is scoped to the source
 * file that produced it, so consumers can keep working with the actions that did compile instead
 * of discarding the whole graph.
 */
public final class CompilationFailures {

    private CompilationFailures() {
    }

    /**
     * Source file names the last compilation reported an error for. Errors without a file name are
     * global and yield an empty set, which callers treat as "nothing individually identified".
     */
    public static @NotNull Set<String> fileNamesOf(@Nullable CompiledGraph graph) {
        Set<String> fileNames = new LinkedHashSet<>();
        for (CompilationError error : errorsOf(graph)) {
            String fileName = error.getFileName();
            if (fileName != null && !fileName.isBlank()) fileNames.add(fileName);
        }
        return fileNames;
    }

    /** Compilation errors carried by a graph, empty when it compiled cleanly. */
    public static @NotNull List<CompilationError> errorsOf(@Nullable CompiledGraph graph) {
        if (graph == null) return List.of();
        GraphErrors errors = graph.getGraphErrors();
        if (errors == null || errors.getCompilationErrors() == null) return List.of();
        return errors.getCompilationErrors();
    }

    /**
     * Whether the compilation failed as a whole rather than on identifiable actions. A graph with
     * errors but no action left standing cannot be used to refresh anything.
     */
    public static boolean isTotalFailure(@Nullable CompiledGraph graph) {
        if (graph == null) return true;
        if (errorsOf(graph).isEmpty()) return false;
        return graph.getTables().isEmpty()
                && graph.getOperations().isEmpty()
                && graph.getDeclarations().isEmpty();
    }
}
