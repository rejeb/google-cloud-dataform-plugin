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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.PsiSqlScope;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlScope;
import io.github.rejeb.dataform.language.diagnostics.sql.mapping.CompiledSourceMapper;
import io.github.rejeb.dataform.language.diagnostics.sql.mapping.SqlTokenLocator;
import io.github.rejeb.dataform.language.diagnostics.sql.mapping.SqlxSourceText;
import io.github.rejeb.dataform.language.schema.sql.DryRunFailure;
import io.github.rejeb.dataform.language.schema.sql.DryRunQueryText;
import io.github.rejeb.dataform.language.util.MappedText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a BigQuery error in the SQLX file it is about.
 *
 * <p>An error with a position is traced from the query sent to the compiled source it was built
 * from, then to the file as it reads now, then to the token there, checked against the name the
 * message gives. An error without one is placed by the name it gives, when that name is written
 * once in the query.</p>
 */
final class BigQueryErrorPlacer {

    enum Outcome {
        LOCATED,
        STALE,
        UNLOCATED
    }

    record Placement(@NotNull Outcome outcome, @Nullable TextRange range) {

        static final Placement STALE = new Placement(Outcome.STALE, null);
        static final Placement UNLOCATED = new Placement(Outcome.UNLOCATED, null);

        static @NotNull Placement at(@NotNull TextRange range) {
            return new Placement(Outcome.LOCATED, range);
        }
    }

    private BigQueryErrorPlacer() {
    }

    static @NotNull Placement place(@NotNull PsiFile hostFile,
                                    @NotNull DryRunFailure failure,
                                    @NotNull BigQueryError error) {
        MappedText sent = failure.query();
        if (sent != null && error.hasPosition()) return placeAtPosition(hostFile, sent, error);
        return placeByName(hostFile, error);
    }

    private static @NotNull Placement placeAtPosition(@NotNull PsiFile hostFile,
                                                      @NotNull MappedText sent,
                                                      @NotNull BigQueryError error) {
        int offset = QueryPositions.offsetOf(sent.text(), error.line(), error.column());
        MappedText.SourcePosition source = offset < 0 ? null : sent.toSource(offset);
        if (source == null) return Placement.UNLOCATED;
        String compiled = sent.sourceText(source.source());
        SqlxSourceText sqlx = SqlxSourceText.of(hostFile, source.source());
        if (compiled == null || sqlx == null) return Placement.UNLOCATED;
        CompiledSourceMapper.HostPlace place = CompiledSourceMapper.toHost(compiled, source.offset(), sqlx);
        if (place == null) return Placement.STALE;
        if (CompiledSourceMapper.HostPlace.UNKNOWN.equals(place)) return Placement.UNLOCATED;
        if (place.hole() != null) return Placement.at(place.hole());
        TextRange range = SqlTokenLocator.locate(hostFile.getText(), place.offset(),
                source.exact() ? expectedToken(error) : null);
        return range == null ? Placement.UNLOCATED : Placement.at(range);
    }

    private static @NotNull Placement placeByName(@NotNull PsiFile hostFile, @NotNull BigQueryError error) {
        String subject = error.subject();
        if (subject == null) return Placement.UNLOCATED;
        return switch (error.kind()) {
            case TABLE_NOT_FOUND, MISSING_DATASET -> placeTable(hostFile, subject);
            case DUPLICATE_COLUMN -> {
                List<SqlScope.SelectItem> items = PsiSqlScope.mainQueryOf(hostFile).selectItemsNamed(subject);
                yield items.size() < 2 ? Placement.UNLOCATED : Placement.at(items.get(1).range());
            }
            default -> Placement.UNLOCATED;
        };
    }

    private static @NotNull Placement placeTable(@NotNull PsiFile hostFile, @NotNull String subject) {
        SqlxSourceText sqlx = SqlxSourceText.of(hostFile, DryRunQueryText.MAIN_QUERY);
        if (sqlx == null) return Placement.UNLOCATED;
        for (String name : tableNames(subject)) {
            List<TextRange> found = SqlTokenLocator.occurrences(sqlx.text(), name, 0, sqlx.text().length());
            if (found.isEmpty()) continue;
            if (found.size() > 1) return Placement.UNLOCATED;
            TextRange range = new TextRange(sqlx.toHost(found.getFirst().getStartOffset()),
                    sqlx.toHost(found.getFirst().getEndOffset()));
            TextRange hole = sqlx.holeAt(range.getStartOffset());
            return Placement.at(hole != null ? hole : range);
        }
        return Placement.UNLOCATED;
    }

    private static @NotNull List<String> tableNames(@NotNull String subject) {
        List<String> names = new ArrayList<>();
        names.add(subject);
        String rest = subject;
        while (rest.indexOf('.') > 0) {
            rest = rest.substring(rest.indexOf('.') + 1);
            names.add(rest);
        }
        return names;
    }

    private static @Nullable String expectedToken(@NotNull BigQueryError error) {
        return switch (error.kind()) {
            case UNRECOGNIZED_NAME, NAME_NOT_FOUND_INSIDE, FIELD_NOT_FOUND, FUNCTION_NOT_FOUND, AMBIGUOUS_COLUMN,
                 NOT_GROUPED_OR_AGGREGATED, AGGREGATE_NOT_ALLOWED, FIELD_ACCESS_ON_ARRAY, UNEXPECTED_TOKEN,
                 EXPECTED_END_OF_INPUT, UNCLOSED_PARENTHESIS -> error.subject();
            case NO_MATCHING_SIGNATURE -> isName(error.subject()) ? error.subject() : null;
            default -> null;
        };
    }

    private static boolean isName(@Nullable String text) {
        return text != null && !text.isEmpty()
                && text.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '.');
    }
}
