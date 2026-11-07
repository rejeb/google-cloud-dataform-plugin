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
import io.github.rejeb.dataform.language.compilation.model.CompiledAssertion;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;
import io.github.rejeb.dataform.language.unittest.preview.TestQueries;
import io.github.rejeb.dataform.language.util.PreOperationsFilter;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The queries of a file the preview can execute, read from a compiled graph.
 */
final class PreviewQueries {

    private static final String AUTO_ASSERTION_INFIX = "_assertions_";

    private PreviewQueries() {
    }

    /**
     * The assertions of a file that a table config declares, by full name, with the full name of
     * their table. An assertion written in its own file has no table and is left out.
     */
    @NotNull
    static Map<String, String> assertionParents(@NotNull CompiledGraph graph, @NotNull String path) {
        Map<String, String> parents = new LinkedHashMap<>();
        for (CompiledAssertion assertion : graph.findAssertionByFileName(path)) {
            if (assertion.getTarget() != null && assertion.getParentAction() != null) {
                parents.put(assertion.getTarget().getFullName(), assertion.getParentAction().getFullName());
            }
        }
        return parents;
    }

    /**
     * Moves each assertion into the action of its table, keeping the order of the other actions.
     * An assertion whose table is not among the queries stays on its own.
     *
     * @param parents the table of each assertion, as {@link #assertionParents} gives it
     */
    @NotNull
    static List<FormattedCompiledQuery> groupAssertions(@NotNull List<FormattedCompiledQuery> queries,
                                                        @NotNull Map<String, String> parents) {
        List<String> shown = queries.stream().map(FormattedCompiledQuery::tableName).toList();
        Map<String, List<FormattedCompiledQuery>> byTable = new LinkedHashMap<>();
        List<FormattedCompiledQuery> kept = new ArrayList<>();
        for (FormattedCompiledQuery query : queries) {
            String parent = parents.get(query.tableName());
            if (parent != null && shown.contains(parent)) {
                byTable.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(query);
            } else {
                kept.add(query);
            }
        }
        return kept.stream()
                .map(query -> byTable.containsKey(query.tableName())
                        ? query.withAssertions(byTable.get(query.tableName()))
                        : query)
                .toList();
    }

    /**
     * The name an assertion goes by inside the section of its table: the kind Dataform appends to
     * the table name, such as {@code uniqueKey_0} or {@code rowConditions}, otherwise its name.
     */
    @NotNull
    static String assertionLabel(@NotNull String fullName) {
        String name = fullName.substring(fullName.lastIndexOf('.') + 1);
        int infix = name.lastIndexOf(AUTO_ASSERTION_INFIX);
        return infix < 0 ? name : name.substring(infix + AUTO_ASSERTION_INFIX.length());
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

    /**
     * The SQL the cost dry-run of an action sends: its query behind the read-only pre-operations,
     * as the action runs, so the variables and temporary functions they declare are known. Null
     * when the action has no query.
     */
    @Nullable
    static String costDryRunSql(@NotNull CompiledQuery q) {
        if (q.query() == null || q.query().isBlank()) return null;
        return Utils.withPreOperations(PreOperationsFilter.keepReadOnly(q.preOps()), q.query());
    }

    @NotNull
    static FormattedCompiledQuery format(@NotNull CompiledQuery q, @NotNull UnaryOperator<String> sql) {
        return format(q, null, sql);
    }

    /**
     * The SQL of an action as the query view shows it, each statement passed through the given
     * formatter, with the type of the action.
     */
    @NotNull
    static FormattedCompiledQuery format(@NotNull CompiledQuery q, @Nullable String type,
                                         @NotNull UnaryOperator<String> sql) {
        return new FormattedCompiledQuery(q.tableName(), joinOrNull(q.preOps(), sql),
                joinOrNull(q.incrementalPreOps(), sql), q.query() == null ? null : sql.apply(q.query()),
                joinOrNull(q.postOps(), sql), joinOrNull(q.compilationErrors(), UnaryOperator.identity()),
                type, sizeOf(q.preOps()), sizeOf(q.incrementalPreOps()), sizeOf(q.postOps()));
    }

    private static int sizeOf(@Nullable List<String> parts) {
        return parts == null ? 0 : (int) parts.stream().filter(part -> part != null && !part.isBlank()).count();
    }

    private static @Nullable String joinOrNull(@Nullable List<String> parts, @NotNull UnaryOperator<String> map) {
        return parts == null || parts.isEmpty() ? null : parts.stream().map(map).collect(Collectors.joining("\n"));
    }
}
