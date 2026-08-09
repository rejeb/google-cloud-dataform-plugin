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
package io.github.rejeb.dataform.language.lineage.column;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.directChild;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.directChildren;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.expressionChildren;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.isType;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.lastIdentifier;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.qualifier;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.topLevelColumnRefs;

/**
 * Reads the columns a {@code PIVOT} or {@code UNPIVOT} operator produces. Both rewrite the shape of
 * their source — rows become columns and back — so their outputs cannot be read off the SELECT list
 * and are derived from the operator clauses instead.
 */
final class PivotAnalyzer {

    private static final String AS_EXPRESSION = "BigQueryAsExpressionImpl";
    private static final String PIVOTED_QUERY = "BigQueryPivotedQueryExpressionImpl";
    private static final String UNPIVOTED_QUERY = "SqlUnpivotedQueryExpressionImpl";
    private static final String PIVOT_COLUMNS_CLAUSE = "SqlPivotColumnsClauseImpl";
    private static final String CLAUSE = "SqlClauseImpl";
    private static final String REFERENCE = "SqlReferenceExpressionImpl";
    private static final String PARENTHESIZED = "SqlParenthesizedExpressionImpl";

    private PivotAnalyzer() {
    }

    /**
     * Columns produced by {@code PIVOT(agg [AS name] ... FOR key IN (value [AS name] ...))}: one
     * per aggregate and pivot value, named as BigQuery names them, and fed by the columns the
     * aggregate reads together with the pivot key that selects the row.
     */
    static @NotNull Map<String, List<InputColumn>> pivotOutputs(@NotNull PsiElement pivot,
                                                                 @NotNull Map<String, List<InputColumn>> sourceOutputs) {
        PsiElement columnsClause = directChild(pivot, PIVOT_COLUMNS_CLAUSE);
        PsiElement forClause = clauseStartingWith(pivot, "FOR");
        PsiElement inClause = clauseStartingWith(pivot, "IN");
        if (columnsClause == null || forClause == null || inClause == null) return Map.of();

        List<InputColumn> keyInputs = inputsOf(forClause, sourceOutputs);
        Map<String, List<InputColumn>> outputs = new LinkedHashMap<>();
        for (PsiElement value : expressionChildren(inClause)) {
            String valueName = pivotValueName(value);
            if (valueName == null) continue;
            for (PsiElement aggregate : expressionChildren(columnsClause)) {
                String aggregateName = isType(aggregate, AS_EXPRESSION) ? lastIdentifier(aggregate) : null;
                String name = aggregateName == null ? valueName : aggregateName + "_" + valueName;
                List<InputColumn> inputs = new ArrayList<>(inputsOf(aggregate, sourceOutputs));
                inputs.addAll(keyInputs);
                outputs.put(name, inputs);
            }
        }
        return outputs;
    }

    /**
     * Columns produced by {@code UNPIVOT(value FOR key IN (column ...))}: the value column and the
     * key column, both fed by every column listed in the IN clause, since each of them supplies
     * one output row.
     */
    static @NotNull Map<String, List<InputColumn>> unpivotOutputs(@NotNull PsiElement pivot,
                                                                   @NotNull Map<String, List<InputColumn>> sourceOutputs) {
        PsiElement forClause = clauseStartingWith(pivot, "FOR");
        PsiElement inClause = clauseStartingWith(pivot, "IN");
        PsiElement valueClause = valueClauseOf(pivot, forClause, inClause);
        if (forClause == null || inClause == null || valueClause == null) return Map.of();

        List<InputColumn> inputs = inputsOf(inClause, sourceOutputs);
        if (inputs.isEmpty()) return Map.of();

        Map<String, List<InputColumn>> outputs = new LinkedHashMap<>();
        for (PsiElement ref : topLevelColumnRefs(valueClause)) {
            String name = lastIdentifier(ref);
            if (name != null) outputs.put(name, inputs);
        }
        for (PsiElement ref : topLevelColumnRefs(forClause)) {
            String name = lastIdentifier(ref);
            if (name != null) outputs.put(name, inputs);
        }
        return outputs;
    }

    static @Nullable PsiElement valueClauseOf(@NotNull PsiElement pivot,
                                               @Nullable PsiElement forClause,
                                               @Nullable PsiElement inClause) {
        for (PsiElement clause : directChildren(pivot, CLAUSE)) {
            if (clause != forClause && clause != inClause) return clause;
        }
        return null;
    }

    /**
     * Column inputs read by a pivot clause, chained through the source scope when the pivoted
     * source is a derived table so that lineage reaches the underlying tables.
     */
    static @NotNull List<InputColumn> inputsOf(@NotNull PsiElement clause,
                                                @NotNull Map<String, List<InputColumn>> sourceOutputs) {
        List<InputColumn> inputs = new ArrayList<>();
        for (PsiElement ref : topLevelColumnRefs(clause)) {
            String name = lastIdentifier(ref);
            if (name == null) continue;
            List<InputColumn> chained = sourceOutputs.get(name);
            if (chained != null && !chained.isEmpty()) {
                inputs.addAll(chained);
            } else {
                inputs.add(new InputColumn(qualifier(ref), name, Confidence.DERIVED, false));
            }
        }
        return inputs;
    }

    /**
     * Output name of a pivot value as BigQuery derives it: the alias when there is one, otherwise
     * the literal itself, with a leading underscore when it does not start with a letter.
     */
    static @Nullable String pivotValueName(@NotNull PsiElement value) {
        if (isType(value, AS_EXPRESSION)) return lastIdentifier(value);
        String text = value.getText().replace("'", "").replace("\"", "").replace("`", "").trim();
        if (text.isEmpty()) return null;
        return Character.isLetter(text.charAt(0)) || text.charAt(0) == '_' ? text : "_" + text;
    }

    static @Nullable PsiElement clauseStartingWith(@NotNull PsiElement pivot, @NotNull String keyword) {
        for (PsiElement clause : directChildren(pivot, CLAUSE)) {
            String text = clause.getText().trim();
            if (text.regionMatches(true, 0, keyword, 0, keyword.length())) return clause;
        }
        return null;
    }

    /** The PIVOT or UNPIVOT expression of a FROM item, with or without an alias. */
    static @Nullable PsiElement pivotOf(@NotNull PsiElement fromItem) {
        if (isType(fromItem, PIVOTED_QUERY) || isType(fromItem, UNPIVOTED_QUERY)) return fromItem;
        if (!isType(fromItem, AS_EXPRESSION)) return null;
        PsiElement pivoted = directChild(fromItem, PIVOTED_QUERY);
        return pivoted != null ? pivoted : directChild(fromItem, UNPIVOTED_QUERY);
    }

    /** The table reference or derived table a PIVOT or UNPIVOT operator reads. */
    static @Nullable PsiElement pivotSourceOf(@NotNull PsiElement pivot) {
        for (PsiElement child : pivot.getChildren()) {
            if (isType(child, REFERENCE) || isType(child, PARENTHESIZED)) return child;
        }
        return null;
    }
}
