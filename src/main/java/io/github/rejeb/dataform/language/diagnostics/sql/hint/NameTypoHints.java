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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import com.intellij.openapi.util.TextRange;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryError;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Names BigQuery could not find: a column, a column of a given source, a field of a struct, a
 * function. The fix is the name most likely meant, BigQuery's own suggestion first.
 */
final class NameTypoHints implements SqlHintProvider {

    private static final int MAX_SUGGESTIONS = 3;

    @Override
    public @NotNull Set<BigQueryErrorKind> kinds() {
        return Set.of(BigQueryErrorKind.UNRECOGNIZED_NAME, BigQueryErrorKind.NAME_NOT_FOUND_INSIDE,
                BigQueryErrorKind.FIELD_NOT_FOUND, BigQueryErrorKind.FUNCTION_NOT_FOUND);
    }

    @Override
    public @NotNull SqlHint hint(@NotNull SqlErrorContext context) {
        BigQueryError error = context.error();
        String name = error.subject();
        if (name == null) return SqlHint.NONE;
        if (error.kind() == BigQueryErrorKind.UNRECOGNIZED_NAME && isSelectAlias(context, name)) {
            return new SqlHint("'" + name + "' is a SELECT alias, which BigQuery does not accept here: "
                    + "repeat its expression, or compute it in a subquery first.", List.of());
        }
        List<String> suggestions = suggestions(context, name);
        if (suggestions.isEmpty()) return new SqlHint(fallback(error), List.of());
        TextRange target = nameRange(context, name);
        List<SqlFix> fixes = new ArrayList<>();
        if (target != null) {
            for (String suggestion : suggestions) {
                fixes.add(new SqlFix("Replace with '" + suggestion + "'", target, suggestion));
            }
        }
        return new SqlHint("Did you mean " + SqlHints.quoted(suggestions) + "?", fixes);
    }

    private static @NotNull List<String> suggestions(@NotNull SqlErrorContext context, @NotNull String name) {
        Set<String> suggestions = new LinkedHashSet<>();
        if (context.error().suggestion() != null) suggestions.add(context.error().suggestion());
        suggestions.addAll(NameSuggester.closest(name, candidates(context), MAX_SUGGESTIONS));
        return suggestions.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private static @NotNull Collection<String> candidates(@NotNull SqlErrorContext context) {
        BigQueryError error = context.error();
        return switch (error.kind()) {
            case NAME_NOT_FOUND_INSIDE -> columnsOf(context.scope(), error.qualifier());
            case FIELD_NOT_FOUND -> structFields(error.qualifier());
            case FUNCTION_NOT_FOUND -> context.scope().functions();
            default -> context.scope().columns();
        };
    }

    private static @NotNull Collection<String> columnsOf(@NotNull SqlScope scope, @Nullable String source) {
        if (source != null) {
            for (Map.Entry<String, List<String>> entry : scope.columnsBySource().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(source)) return entry.getValue();
            }
        }
        return scope.columns();
    }

    static @NotNull List<String> structFields(@Nullable String type) {
        if (type == null) return List.of();
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close <= open) return List.of();
        List<String> fields = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = open + 1; i <= close; i++) {
            char c = type.charAt(i);
            if (c == '<' || c == '(') depth++;
            else if ((c == '>' || c == ')') && i < close) depth--;
            if ((c == ',' && depth == 0) || i == close) {
                String field = type.substring(start, i).trim();
                int space = field.indexOf(' ');
                if (space > 0) fields.add(field.substring(0, space).replace("`", ""));
                start = i + 1;
            }
        }
        return fields;
    }

    private static @Nullable TextRange nameRange(@NotNull SqlErrorContext context, @NotNull String name) {
        String written = context.rangeText().toLowerCase(Locale.ROOT);
        int at = written.lastIndexOf(name.toLowerCase(Locale.ROOT));
        return at < 0 ? null : TextRange.from(context.range().getStartOffset() + at, name.length());
    }

    /**
     * Whether the name is an alias the query at the error gives one of its other select items: read
     * anywhere but in the select list, BigQuery does not know it.
     */
    private static boolean isSelectAlias(@NotNull SqlErrorContext context, @NotNull String name) {
        return context.scope().selectItemsNamed(name).stream()
                .anyMatch(item -> item.alias() != null && !item.range().contains(context.range()));
    }

    private static @NotNull String fallback(@NotNull BigQueryError error) {
        return switch (error.kind()) {
            case NAME_NOT_FOUND_INSIDE -> "'" + error.qualifier() + "' has no column '" + error.subject() + "'.";
            case FIELD_NOT_FOUND -> {
                List<String> fields = structFields(error.qualifier());
                yield fields.isEmpty() ? "The struct has no field of that name." : "Fields available: " + String.join(", ", fields) + ".";
            }
            case FUNCTION_NOT_FOUND -> "A user-defined function must be qualified with its dataset, e.g. my_dataset."
                    + error.subject() + "().";
            default -> "No column of that name in the tables the query reads: check the spelling, or the table it comes from.";
        };
    }
}
