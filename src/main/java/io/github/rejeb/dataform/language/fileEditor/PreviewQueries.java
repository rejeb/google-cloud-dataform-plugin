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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;
import io.github.rejeb.dataform.language.unittest.preview.TestQueries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The queries of a file the preview can execute, read from a compiled graph.
 */
final class PreviewQueries {

    private PreviewQueries() {
    }

    @NotNull
    static List<FormattedCompiledQuery> executable(@NotNull CompiledGraph graph,
                                                   @NotNull String path,
                                                   boolean unitTestFile) {
        if (unitTestFile) {
            return TestQueries.of(graph.findTestByFileName(path).stream().findFirst().orElse(null)).stream()
                    .map(q -> new FormattedCompiledQuery(q.label(), null, null, q.sql(), null, null))
                    .toList();
        }
        return graph.findCompiledQueryByFileName(path).stream()
                .map(q -> format(q, UnaryOperator.identity()))
                .toList();
    }

    @Nullable
    static String errorsOf(@NotNull CompiledGraph graph, @NotNull String path) {
        return graph.findCompilationErrorByFileName(path).stream()
                .map(CompilationError::getMessage)
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
    }

    @NotNull
    static FormattedCompiledQuery format(@NotNull CompiledQuery q, @NotNull UnaryOperator<String> sql) {
        return new FormattedCompiledQuery(q.tableName(), joinOrNull(q.preOps(), sql),
                joinOrNull(q.incrementalPreOps(), sql), q.query() == null ? null : sql.apply(q.query()),
                joinOrNull(q.postOps(), sql), joinOrNull(q.compilationErrors(), UnaryOperator.identity()));
    }

    private static @Nullable String joinOrNull(@Nullable List<String> parts, @NotNull UnaryOperator<String> map) {
        return parts == null || parts.isEmpty() ? null : parts.stream().map(map).collect(Collectors.joining("\n"));
    }
}
