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

import com.intellij.openapi.util.TextRange;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.NameSuggester;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHint;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Explains how to fix a Dataform compilation error, and offers the name most likely meant where a
 * name is misspelled.
 */
final class CompilationHints {

    static final List<String> ACTION_TYPES =
            List.of("table", "view", "incremental", "operations", "assertion", "declaration", "test");
    private static final Pattern SQL_START =
            Pattern.compile("(?i)^\\s*(SELECT|WITH|FROM|INSERT|MERGE|CREATE|DELETE|UPDATE|DECLARE)\\b");
    private static final int MAX_SUGGESTIONS = 3;

    private CompilationHints() {
    }

    static @NotNull SqlHint hintFor(@NotNull ParsedCompilationError error, @NotNull TextRange range,
                                    @NotNull CharSequence text, @NotNull CompilationScope scope) {
        String name = error.name();
        return switch (error.kind()) {
            case UNDEFINED_NAME -> suggest(name, scope.jsNames(), range, text, scope.isInConfig()
                    ? "A config block cannot read what a js { } block declares: export '" + name + "' from a file "
                    + "of includes/ and read it through the name of that file."
                    : "Define '" + name + "' in a js { } block, or export it from a file of includes/.");
            case UNDEFINED_PROPERTY -> new SqlHint("The value read before '." + name + "' is undefined: check the "
                    + "variable or the property it comes from.", List.of());
            case NOT_A_FUNCTION -> new SqlHint("'" + name + "' is not a function: check its name, or that its include "
                    + "exports it with module.exports.", List.of());
            case UNRESOLVED_REF -> {
                String action = name == null ? null : name.substring(name.lastIndexOf('.') + 1);
                yield suggest(action, scope.actionNames(), range, text,
                        "No action is named '" + action + "': check the name, or declare the table with a declaration.");
            }
            case UNKNOWN_ACTION_TYPE -> suggest(name, ACTION_TYPES, range, text,
                    "Valid types: " + String.join(", ", ACTION_TYPES) + ".");
            case UNEXPECTED_CONFIG_PROPERTY -> suggest(name, scope.configKeys(), range, text,
                    "This config does not accept '" + name + "': see the Dataform config reference.");
            case SYNTAX_ERROR -> syntax(error);
            default -> SqlHint.NONE;
        };
    }

    private static @NotNull SqlHint suggest(@Nullable String name, @NotNull Collection<String> candidates,
                                            @NotNull TextRange range, @NotNull CharSequence text,
                                            @NotNull String fallback) {
        if (name == null) return SqlHint.NONE;
        List<String> suggestions = NameSuggester.closest(name, candidates, MAX_SUGGESTIONS);
        if (suggestions.isEmpty()) return new SqlHint(fallback, List.of());
        boolean replaceable = range.getEndOffset() <= text.length()
                && range.subSequence(text).toString().equals(name);
        List<SqlFix> fixes = replaceable
                ? suggestions.stream().map(suggestion -> new SqlFix("Replace with '" + suggestion + "'", range, suggestion)).toList()
                : List.of();
        return new SqlHint("Did you mean " + SqlHints.quoted(suggestions) + "?", fixes);
    }

    private static @NotNull SqlHint syntax(@NotNull ParsedCompilationError error) {
        SourceSnippet snippet = error.snippet();
        if (snippet != null && SQL_START.matcher(snippet.text()).find()) {
            return new SqlHint("The SQL is read as JavaScript: a config, js or operations block above is missing "
                    + "its closing '}'.", List.of());
        }
        return new SqlHint("JavaScript syntax error in a js block or a ${…} expression: check the code at the caret.",
                List.of());
    }
}
