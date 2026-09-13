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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class DataformCteQueryBuilder {

    private DataformCteQueryBuilder() {
    }

    @NotNull
    public static String buildDryRunQuery(@NotNull String originalQuery,
                                          @NotNull Map<String, List<ColumnInfo>> knownSchemas,
                                          @NotNull Project project) {
        if (knownSchemas.isEmpty()) return originalQuery;

        List<TextRange> excluded = ReadAction.computeBlocking(
                () -> collectExcludedRanges(originalQuery, project));

        Map<String, String> fqnToAlias = new LinkedHashMap<>();
        Map<String, List<ColumnInfo>> aliasToColumns = new LinkedHashMap<>();
        List<Pair<TextRange, String>> substitutions = new ArrayList<>();

        for (Map.Entry<String, List<ColumnInfo>> entry : knownSchemas.entrySet()) {
            String fqn = entry.getKey();
            List<TextRange> occurrences = findSafeOccurrences(originalQuery, fqn, excluded);
            if (occurrences.isEmpty()) continue;

            String alias = toSafeAlias(fqn, fqnToAlias.values());
            fqnToAlias.put(fqn, alias);
            aliasToColumns.put(alias, entry.getValue());
            for (TextRange range : occurrences) {
                substitutions.add(Pair.pair(range, alias));
            }
        }

        if (substitutions.isEmpty()) return originalQuery;

        substitutions.sort(Comparator.comparingInt(p -> -p.first.getStartOffset()));

        StringBuilder result = new StringBuilder(originalQuery);
        TextRange lastReplaced = null;
        for (Pair<TextRange, String> sub : substitutions) {
            if (lastReplaced != null && sub.first.intersects(lastReplaced)) continue;
            result.replace(sub.first.getStartOffset(), sub.first.getEndOffset(), sub.second);
            lastReplaced = sub.first;
        }

        return mergeWithClauses(buildCteList(aliasToColumns), result.toString());
    }

    @NotNull
    private static List<TextRange> collectExcludedRanges(@NotNull String query,
                                                         @NotNull Project project) {
        PsiFile psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText("_df_dry_run.sql", BigQueryDialect.INSTANCE, query);

        List<TextRange> excluded = new ArrayList<>();

        PsiTreeUtil.processElements(psiFile, element -> {
            if (element instanceof PsiComment) {
                excluded.add(element.getTextRange());
                return false;
            }

            if (element.getFirstChild() == null) {
                if (isSqlStringToken(element.getNode().getElementType())) {
                    excluded.add(element.getTextRange());
                }
            }
            return true;
        });

        return excluded;
    }

    private static boolean isSqlStringToken(@NotNull IElementType type) {
        String name = type.toString().toUpperCase();
        return name.contains("STRING")
                || name.contains("CHAR_LITERAL")
                || name.contains("RAW_STRING")
                || name.contains("BYTES")
                || name.contains("QUOTED");
    }

    @NotNull
    private static List<TextRange> findSafeOccurrences(@NotNull String query,
                                                       @NotNull String fqn,
                                                       @NotNull List<TextRange> excluded) {
        String[] parts = fqn.split("\\.", 3);
        if (parts.length != 3) return Collections.emptyList();

        List<String> variants = getParts(fqn, parts);

        List<TextRange> result = new ArrayList<>();

        for (String variant : variants) {
            int searchFrom = 0;
            while (searchFrom < query.length()) {
                int idx = query.indexOf(variant, searchFrom);
                if (idx == -1) break;

                TextRange candidate = new TextRange(idx, idx + variant.length());

                if (!isInsideExcluded(candidate, excluded)) {
                    if (isBoundaryMatch(query, idx, idx + variant.length())) {
                        result.add(candidate);
                    }
                }
                searchFrom = idx + 1;
            }
        }

        return deduplicateOverlapping(result);
    }

    private static @NonNull List<String> getParts(@NonNull String fqn, String[] parts) {
        String db = parts[0], schema = parts[1], name = parts[2];

        List<String> variants = List.of(
                db + "." + schema + "." + name,                              // unquoted
                "`" + fqn + "`",                                             // `project.dataset.table`
                "`" + db + "`.`" + schema + "`.`" + name + "`",             // `p`.`d`.`t`
                "`" + db + "`." + schema + "." + name,                      // `p`.d.t
                "`" + db + "`." + schema + ".`" + name + "`",               // `p`.d.`t`
                "`" + db + "`.`" + schema + "`." + name                     // `p`.`d`.t
        );
        return variants;
    }

    private static boolean isInsideExcluded(@NotNull TextRange range,
                                            @NotNull List<TextRange> excluded) {
        return excluded.stream().anyMatch(ex -> ex.contains(range));
    }

    private static boolean isBoundaryMatch(@NotNull String text, int start, int end) {
        if (start > 0) {
            char before = text.charAt(start - 1);
            // If the match starts with a backtick, no boundary check needed
            if (text.charAt(start) != '`' && (Character.isLetterOrDigit(before) || before == '_')) {
                return false;
            }
        }
        if (end < text.length()) {
            char after = text.charAt(end);
            if (text.charAt(end - 1) != '`' && (Character.isLetterOrDigit(after) || after == '_')) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    private static List<TextRange> deduplicateOverlapping(@NotNull List<TextRange> ranges) {
        if (ranges.size() <= 1) return ranges;
        ranges.sort(Comparator.comparingInt(TextRange::getStartOffset));
        List<TextRange> result = new ArrayList<>();
        TextRange last = null;
        for (TextRange r : ranges) {
            if (last == null || !r.intersects(last)) {
                result.add(r);
                last = r;
            } else if (r.getLength() > last.getLength()) {
                // Keep the longer match (e.g. backtick variant over unquoted)
                result.set(result.size() - 1, r);
                last = r;
            }
        }
        return result;
    }

    /** The stub definitions, as a CTE list without the {@code WITH} keyword owning them. */
    @NotNull
    private static String buildCteList(@NotNull Map<String, List<ColumnInfo>> aliasToColumns) {
        StringJoiner ctes = new StringJoiner(",\n");
        for (Map.Entry<String, List<ColumnInfo>> entry : aliasToColumns.entrySet()) {
            ctes.add(buildCte(entry.getKey(), entry.getValue()));
        }
        return ctes.toString();
    }

    @NotNull
    private static String buildCte(@NotNull String alias, @NotNull List<ColumnInfo> columns) {
        StringJoiner cols = new StringJoiner(",\n    ", "  SELECT\n    ", "");
        if (columns.isEmpty()) {
            cols.add("CAST(NULL AS STRING) AS _df_placeholder_");
        } else {
            for (ColumnInfo col : columns) {
                cols.add("CAST(NULL AS " + toBigQueryTypeExpression(col) + ") AS "
                        + quoteColIfNeeded(col.name()));
            }
        }
        return alias + " AS (\n" + cols + "\n)";
    }

    @NotNull
    private static String toBigQueryTypeExpression(@NotNull ColumnInfo col) {
        String base = switch (col.type()) {
            case "INTEGER" -> "INT64";
            case "FLOAT" -> "FLOAT64";
            case "BOOLEAN" -> "BOOL";
            case "RECORD", "STRUCT" -> buildStructType(col.subFields());
            default -> col.type();
        };
        return col.isRepeated() ? "ARRAY<" + base + ">" : base;
    }

    @NotNull
    private static String buildStructType(@NotNull List<ColumnInfo> fields) {
        if (fields.isEmpty()) return "STRUCT<_placeholder_ STRING>";
        StringJoiner sj = new StringJoiner(", ", "STRUCT<", ">");
        for (ColumnInfo f : fields) {
            sj.add(f.name() + " " + toBigQueryTypeExpression(f));
        }
        return sj.toString();
    }

    @NotNull
    private static String toSafeAlias(@NotNull String fqn,
                                      @NotNull Collection<String> used) {
        String[] parts = fqn.split("\\.");
        String base = "_df_" + parts[parts.length - 1].replaceAll("[^a-zA-Z0-9_]", "_");
        if (!used.contains(base)) return base;

        String withSchema = "_df_"
                + parts[parts.length - 2].replaceAll("[^a-zA-Z0-9_]", "_")
                + "_" + parts[parts.length - 1].replaceAll("[^a-zA-Z0-9_]", "_");
        if (!used.contains(withSchema)) return withSchema;

        int i = 2;
        while (used.contains(withSchema + "_" + i)) i++;
        return withSchema + "_" + i;
    }

    private static @NotNull String quoteColIfNeeded(@NotNull String name) {
        return name.matches("[a-zA-Z_][a-zA-Z0-9_]*") ? name : "`" + name + "`";
    }

    /**
     * The stubs and the query under one {@code WITH}.
     *
     * <p>A query already opening with a {@code WITH} has its CTEs joined to the stubs, because two
     * clauses side by side are not a query any dialect accepts. The keyword is looked for past
     * comments as well as whitespace: a compiled Dataform query carries the comments of the SQLX
     * file in front of it, and a clause hidden behind one used to be missed entirely, which made
     * the dry-run of every action built by a CTE fail on a syntax error — and with it the schema of
     * that table, so nothing downstream had columns to offer.</p>
     *
     * <p>{@code RECURSIVE} belongs to the clause rather than to one definition, so it moves to the
     * front when the query carried it. The stubs stay plain: a list introduced by
     * {@code WITH RECURSIVE} may hold definitions that do not recurse.</p>
     */
    @NotNull
    private static String mergeWithClauses(@NotNull String cteList,
                                           @NotNull String modifiedQuery) {
        int keyword = ownWithKeyword(modifiedQuery);
        if (keyword < 0) return "WITH\n" + cteList + "\n" + modifiedQuery;

        int body = keyword + WITH.length();
        int afterRecursive = keywordEnd(modifiedQuery, body, RECURSIVE);
        boolean recursive = afterRecursive > 0;
        if (recursive) body = afterRecursive;

        return "WITH" + (recursive ? " " + RECURSIVE : "") + "\n" + cteList + ",\n"
                + modifiedQuery.substring(0, keyword) + modifiedQuery.substring(body);
    }

    private static final String WITH = "WITH";
    private static final String RECURSIVE = "RECURSIVE";

    /**
     * The offset of the query's own {@code WITH} keyword, or {@code -1} when it opens with anything
     * else.
     */
    private static int ownWithKeyword(@NotNull String query) {
        int start = skipWhitespaceAndComments(query, 0);
        return start >= 0 && isKeywordAt(query, start, WITH) ? start : -1;
    }

    /**
     * The offset just past {@code keyword} when it stands next in the query, or {@code -1} when
     * something else does.
     */
    private static int keywordEnd(@NotNull String query, int from, @NotNull String keyword) {
        int start = skipWhitespaceAndComments(query, from);
        return start >= 0 && isKeywordAt(query, start, keyword) ? start + keyword.length() : -1;
    }

    /**
     * The first offset at or after {@code from} holding neither whitespace nor a comment, or
     * {@code -1} when the query holds nothing else.
     */
    private static int skipWhitespaceAndComments(@NotNull String query, int from) {
        int at = from;
        while (at < query.length()) {
            char current = query.charAt(at);
            if (Character.isWhitespace(current)) {
                at++;
            } else if (current == '#' || isPairAt(query, at, '-', '-')) {
                int end = query.indexOf('\n', at);
                at = end < 0 ? query.length() : end + 1;
            } else if (isPairAt(query, at, '/', '*')) {
                int end = query.indexOf("*/", at + 2);
                at = end < 0 ? query.length() : end + 2;
            } else {
                return at;
            }
        }
        return -1;
    }

    private static boolean isPairAt(@NotNull String query, int at, char first, char second) {
        return query.charAt(at) == first
                && at + 1 < query.length()
                && query.charAt(at + 1) == second;
    }

    /** Whether a keyword stands at an offset as a whole word rather than as the start of a name. */
    private static boolean isKeywordAt(@NotNull String query, int at, @NotNull String keyword) {
        if (!query.regionMatches(true, at, keyword, 0, keyword.length())) return false;
        int after = at + keyword.length();
        return after >= query.length() || !isWordCharacter(query.charAt(after));
    }

    private static boolean isWordCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

}