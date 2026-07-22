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
package io.github.rejeb.dataform.language.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps only the pre-operation statements that are safe to replay when a query is executed for
 * preview or dry-run purposes. Statements that create or alter objects, or that insert, update or
 * delete data, are dropped so that inspecting a compiled query never mutates the warehouse.
 * <p>
 * The filter is closed by default: a statement whose leading keyword is not explicitly known to be
 * side effect free is dropped.
 */
public final class PreOperationsFilter {

    private static final Logger LOG = Logger.getInstance(PreOperationsFilter.class);

    private static final List<String> SAFE_PREFIXES = List.of(
            "declare",
            "set",
            "select",
            "with",
            "create temp function",
            "create temporary function",
            "create or replace temp function",
            "create or replace temporary function"
    );

    private PreOperationsFilter() {
    }

    /**
     * Filters out every pre-operation statement that could modify the warehouse.
     *
     * @param preOperations raw pre-operations, each entry may hold several statements
     * @return the statements that are safe to run, in their original order
     */
    @NotNull
    public static List<String> keepReadOnly(@Nullable List<String> preOperations) {
        if (preOperations == null || preOperations.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (String preOperation : preOperations) {
            if (preOperation == null) {
                continue;
            }
            for (String statement : splitStatements(preOperation)) {
                if (isReadOnly(statement)) {
                    kept.add(statement);
                } else {
                    LOG.info("Skipping pre-operation with possible side effects: "
                            + summarize(statement));
                }
            }
        }
        return kept;
    }

    /**
     * Tells whether a single statement is known to be free of side effects.
     *
     * @param statement statement to classify
     * @return {@code true} when the statement can safely be replayed
     */
    public static boolean isReadOnly(@Nullable String statement) {
        String normalized = normalize(statement);
        if (normalized.isEmpty()) {
            return false;
        }
        return SAFE_PREFIXES.stream().anyMatch(prefix ->
                normalized.equals(prefix) || normalized.startsWith(prefix + " ")
                        || normalized.startsWith(prefix + "("));
    }

    private static String normalize(@Nullable String statement) {
        if (statement == null) {
            return "";
        }
        String withoutComments = stripComments(statement);
        return withoutComments.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String stripComments(@NotNull String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i = skipToLineEnd(sql, i);
            } else if (c == '#') {
                i = skipToLineEnd(sql, i);
            } else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? sql.length() : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int skipToLineEnd(@NotNull String sql, int from) {
        int end = sql.indexOf('\n', from);
        return end < 0 ? sql.length() : end;
    }

    private static List<String> splitStatements(@NotNull String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == '\\' && i + 1 < sql.length()) {
                    current.append(sql.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
                current.append(c);
            } else if (c == ';') {
                addIfNotBlank(statements, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addIfNotBlank(statements, current.toString());
        return statements;
    }

    private static void addIfNotBlank(@NotNull List<String> statements, @NotNull String statement) {
        if (!statement.isBlank()) {
            statements.add(statement.strip());
        }
    }

    private static String summarize(@NotNull String statement) {
        String single = statement.strip().replaceAll("\\s+", " ");
        return single.length() <= 120 ? single : single.substring(0, 120) + "...";
    }
}
