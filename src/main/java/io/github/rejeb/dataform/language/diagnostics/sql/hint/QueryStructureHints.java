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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Queries BigQuery reads but cannot run as written: an ambiguous column, a column neither grouped
 * nor aggregated, an aggregate or a window function filtered in WHERE, two output columns of the
 * same name, a field read from an array, arguments of the wrong type.
 */
final class QueryStructureHints implements SqlHintProvider {

    private static final Pattern SIGNATURE =
            Pattern.compile("(?:Supported signatures?|Signature): (.+?)(?: Argument \\d+:.*)?$");

    @Override
    public @NotNull Set<BigQueryErrorKind> kinds() {
        return Set.of(BigQueryErrorKind.AMBIGUOUS_COLUMN, BigQueryErrorKind.NOT_GROUPED_OR_AGGREGATED,
                BigQueryErrorKind.AGGREGATE_NOT_ALLOWED, BigQueryErrorKind.ANALYTIC_NOT_ALLOWED,
                BigQueryErrorKind.DUPLICATE_COLUMN, BigQueryErrorKind.FIELD_ACCESS_ON_ARRAY,
                BigQueryErrorKind.NO_MATCHING_SIGNATURE);
    }

    @Override
    public @NotNull SqlHint hint(@NotNull SqlErrorContext context) {
        BigQueryError error = context.error();
        return switch (error.kind()) {
            case AMBIGUOUS_COLUMN -> ambiguous(context);
            case NOT_GROUPED_OR_AGGREGATED -> notGrouped(context);
            case AGGREGATE_NOT_ALLOWED -> new SqlHint("Aggregates are computed after " + clause(error)
                    + ": filter on them in HAVING, after a GROUP BY.", List.of());
            case ANALYTIC_NOT_ALLOWED -> new SqlHint("Window functions are computed after " + clause(error)
                    + ": filter on them with QUALIFY.", List.of());
            case DUPLICATE_COLUMN -> duplicate(context);
            case FIELD_ACCESS_ON_ARRAY -> new SqlHint("'" + error.subject() + "' is read from an ARRAY: flatten it "
                    + "first, e.g. CROSS JOIN UNNEST(array_column) AS item, then read item." + error.subject() + ".",
                    List.of());
            case NO_MATCHING_SIGNATURE -> signature(error);
            default -> SqlHint.NONE;
        };
    }

    private static @NotNull SqlHint ambiguous(@NotNull SqlErrorContext context) {
        String column = context.error().subject();
        String written = context.rangeText();
        List<String> sources = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : context.scope().columnsBySource().entrySet()) {
            if (entry.getValue().stream().anyMatch(name -> name.equalsIgnoreCase(column))) sources.add(entry.getKey());
        }
        if (sources.isEmpty()) {
            return new SqlHint("Qualify it with the alias of the table it should come from.", List.of());
        }
        List<SqlFix> fixes = sources.stream()
                .map(source -> new SqlFix("Qualify as '" + source + "." + written + "'", context.range(),
                        source + "." + written))
                .toList();
        return new SqlHint("'" + column + "' exists in " + String.join(" and ", sources)
                + ": qualify it with the one it should come from.", fixes);
    }

    private static @NotNull SqlHint notGrouped(@NotNull SqlErrorContext context) {
        String written = context.rangeText();
        List<SqlFix> fixes = new ArrayList<>();
        int groupByEnd = context.scope().groupByEnd();
        if (groupByEnd >= 0) {
            fixes.add(new SqlFix("Add '" + written + "' to GROUP BY", TextRange.from(groupByEnd, 0), ", " + written));
        }
        fixes.add(new SqlFix("Wrap in ANY_VALUE()", context.range(), "ANY_VALUE(" + written + ")"));
        return new SqlHint("Add it to GROUP BY, or aggregate it (ANY_VALUE, MAX, …).", fixes);
    }

    private static @NotNull SqlHint duplicate(@NotNull SqlErrorContext context) {
        String column = context.error().subject();
        String text = "Give each '" + column + "' output column its own name with AS.";
        List<SqlScope.SelectItem> items = context.scope().selectItemsNamed(column);
        if (items.size() < 2) return new SqlHint(text, List.of());
        SqlScope.SelectItem second = items.get(1);
        String name = column + "_2";
        SqlFix fix = second.alias() != null
                ? new SqlFix("Rename to '" + name + "'", second.alias(), name)
                : new SqlFix("Name it '" + name + "'", TextRange.from(second.range().getEndOffset(), 0), " AS " + name);
        return new SqlHint(text, List.of(fix));
    }

    private static @NotNull SqlHint signature(@NotNull BigQueryError error) {
        String expected = expectedSignature(error.qualifier());
        return new SqlHint((expected == null ? "" : "Expected: " + expected + ". ")
                + "Convert the arguments with CAST or SAFE_CAST.", List.of());
    }

    private static @Nullable String expectedSignature(@Nullable String details) {
        if (details == null) return null;
        Matcher matcher = SIGNATURE.matcher(details);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static @NotNull String clause(@NotNull BigQueryError error) {
        return error.qualifier() == null ? "WHERE" : error.qualifier();
    }
}
