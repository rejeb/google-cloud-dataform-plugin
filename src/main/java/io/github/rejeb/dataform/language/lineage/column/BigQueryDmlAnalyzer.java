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
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.firstQueryScope;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.isExpression;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.isStar;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.isType;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.lastIdentifier;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.topLevelColumnRefs;

/**
 * Column lineage of the statements that write a table.
 *
 * <p>An operation builds its output with whatever statements it likes — a {@code CREATE TABLE …
 * AS SELECT}, an {@code INSERT}, a {@code MERGE}, an {@code UPDATE} — and none of them lists the
 * output columns the way a table action's query does. What each one writes to a column is read
 * from the statement: the select item behind an inserted column, the expression assigned to an
 * updated one, the source column a {@code MERGE} copies. A statement writing another table, such
 * as a log, says nothing about this one and is left alone.</p>
 *
 * <p>The reads are resolved the way a query's are. The {@code USING} source of a {@code MERGE}
 * and the {@code FROM} of an {@code UPDATE} are registered as scopes, so a column taken from a
 * subquery is inlined down to the table it came from. A reference qualified with the alias of the
 * table being written reads the table itself, which is not lineage, and is skipped.</p>
 */
final class BigQueryDmlAnalyzer {

    private static final String CREATE_TABLE = "SqlCreateTableStatementImpl";
    private static final String INSERT_STATEMENT = "SqlInsertStatementImpl";
    private static final String MERGE_STATEMENT = "SqlMergeStatementImpl";
    private static final String UPDATE_STATEMENT = "SqlUpdateStatementImpl";
    private static final String DML_INSTRUCTION = "SqlDmlInstructionImpl";
    private static final String INSERT_INSTRUCTION = "SqlInsertDmlInstructionImpl";
    private static final String TABLE_COLUMNS_LIST = "SqlTableColumnsListImpl";
    private static final String REFERENCE_LIST = "SqlReferenceListImpl";
    private static final String VALUES_EXPRESSION = "SqlValuesExpressionImpl";
    private static final String USING_CLAUSE = "SqlUsingClauseImpl";
    private static final String SET_CLAUSE = "SqlSetClauseImpl";
    private static final String SET_ASSIGNMENT = "SqlSetAssignmentImpl";
    private static final String FROM_CLAUSE = "SqlFromClauseImpl";
    private static final String CLAUSE = "SqlClauseImpl";
    private static final String REFERENCE = SqlPsiNavigator.REFERENCE;
    private static final String IDENTIFIER = SqlPsiNavigator.IDENTIFIER;
    private static final String AS_EXPRESSION = "BigQueryAsExpressionImpl";
    private static final String PARENTHESIZED = SqlPsiNavigator.PARENTHESIZED;
    private static final String VALUES_ROW = "BigQueryParenthesizedExpression";

    private final BigQuerySelectAnalyzer selects;
    private final String tableName;
    private final List<String> tableColumns;
    private final Map<String, List<InputColumn>> outputs = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private boolean wrote;

    BigQueryDmlAnalyzer(@NotNull BigQuerySelectAnalyzer selects,
                        @NotNull String tableName,
                        @NotNull List<String> tableColumns) {
        this.selects = selects;
        this.tableName = tableName;
        this.tableColumns = tableColumns;
    }

    /** Reads every top-level statement of the file, keeping what writes the table. */
    @NotNull SelectAnalyzer.QueryAnalysis analyze(@NotNull PsiElement file) {
        for (PsiElement statement : file.getChildren()) {
            if (isType(statement, CREATE_TABLE)) createTable(statement);
            else if (isType(statement, INSERT_STATEMENT)) insert(statement);
            else if (isType(statement, MERGE_STATEMENT)) merge(statement);
            else if (isType(statement, UPDATE_STATEMENT)) update(statement);
        }
        return new SelectAnalyzer.QueryAnalysis(outputs, aliases);
    }

    /** Whether at least one statement wrote the table. */
    boolean wroteTheTable() {
        return wrote;
    }

    private void createTable(@NotNull PsiElement statement) {
        PsiElement name = directChild(statement, IDENTIFIER);
        if (name == null || !tableName.equalsIgnoreCase(name.getText())) return;
        PsiElement asClause = directChildren(statement, CLAUSE).stream()
                .filter(clause -> firstQueryScope(clause) != null)
                .findFirst().orElse(null);
        if (asClause == null) return;
        wrote = true;
        positional(List.of(), selectOutputs(asClause));
    }

    private void insert(@NotNull PsiElement statement) {
        PsiElement instruction = directChild(statement, INSERT_INSTRUCTION);
        if (instruction == null) return;
        PsiElement target = directChild(instruction, TABLE_COLUMNS_LIST);
        if (target == null || !tableName.equalsIgnoreCase(writtenName(target))) return;
        wrote = true;
        List<String> columns = columnList(directChild(target, REFERENCE_LIST));
        PsiElement values = directChild(instruction, VALUES_EXPRESSION);
        if (values != null) {
            values(columns, values, Map.of(), Map.of());
            return;
        }
        positional(columns, selectOutputs(instruction));
    }

    private void merge(@NotNull PsiElement statement) {
        PsiElement instruction = directChild(statement, DML_INSTRUCTION);
        if (instruction == null || !tableName.equalsIgnoreCase(writtenName(instruction))) return;
        wrote = true;
        String targetAlias = writtenAlias(instruction);
        Map<String, String> statementAliases = new LinkedHashMap<>();
        Map<String, Map<String, List<InputColumn>>> scopes = new LinkedHashMap<>();
        PsiElement using = directChild(instruction, USING_CLAUSE);
        if (using != null) registerSources(using, statementAliases, scopes);

        for (PsiElement when : directChildren(instruction, CLAUSE)) {
            PsiElement update = directChild(when, DML_INSTRUCTION);
            if (update != null) assignments(update, targetAlias, statementAliases, scopes);
            PsiElement insert = directChild(when, INSERT_INSTRUCTION);
            if (insert == null) continue;
            PsiElement values = directChild(insert, VALUES_EXPRESSION);
            if (values == null) continue;
            values(columnList(directChild(insert, REFERENCE_LIST)), values,
                    withoutTarget(statementAliases, targetAlias), scopes);
        }
        aliases.putAll(withoutTarget(statementAliases, targetAlias));
    }

    private void update(@NotNull PsiElement statement) {
        PsiElement instruction = directChild(statement, DML_INSTRUCTION);
        if (instruction == null || !tableName.equalsIgnoreCase(writtenName(instruction))) return;
        wrote = true;
        String targetAlias = writtenAlias(instruction);
        Map<String, String> statementAliases = new LinkedHashMap<>();
        Map<String, Map<String, List<InputColumn>>> scopes = new LinkedHashMap<>();
        PsiElement from = directChild(instruction, FROM_CLAUSE);
        if (from != null) registerSources(from, statementAliases, scopes);
        assignments(instruction, targetAlias, statementAliases, scopes);
        aliases.putAll(withoutTarget(statementAliases, targetAlias));
    }

    /**
     * Registers the sources a clause reads: a table under its alias, a subquery as a scope whose
     * outputs are already resolved to the tables inside it.
     */
    private void registerSources(@NotNull PsiElement clause,
                                 @NotNull Map<String, String> statementAliases,
                                 @NotNull Map<String, Map<String, List<InputColumn>>> scopes) {
        selects.collectFromSources(clause, statementAliases);
        selects.collectFromScopes(clause, Map.of(), scopes, statementAliases, new LinkedHashMap<>(), new int[]{0});
        for (PsiElement item : clause.getChildren()) {
            PsiElement derived = isType(item, AS_EXPRESSION) ? directChild(item, PARENTHESIZED) : null;
            if (derived == null && isType(item, PARENTHESIZED)) derived = item;
            if (derived == null) continue;
            PsiElement scope = firstQueryScope(derived);
            if (scope != null) selects.collectScopeAliases(scope, statementAliases);
        }
    }

    private void assignments(@NotNull PsiElement instruction,
                             @Nullable String targetAlias,
                             @NotNull Map<String, String> statementAliases,
                             @NotNull Map<String, Map<String, List<InputColumn>>> scopes) {
        PsiElement set = directChild(instruction, SET_CLAUSE);
        if (set == null) return;
        Map<String, String> readable = withoutTarget(statementAliases, targetAlias);
        for (PsiElement assignment : directChildren(set, SET_ASSIGNMENT)) {
            PsiElement column = directChild(assignment, REFERENCE);
            if (column == null) continue;
            String name = lastIdentifier(column);
            PsiElement value = lastExpression(assignment);
            if (name == null || value == null) continue;
            written(name, value, targetAlias, readable, scopes);
        }
    }

    /** Pairs an insert's column list with the expressions of its {@code VALUES} row. */
    private void values(@NotNull List<String> columns,
                        @NotNull PsiElement values,
                        @NotNull Map<String, String> statementAliases,
                        @NotNull Map<String, Map<String, List<InputColumn>>> scopes) {
        PsiElement row = directChild(values, VALUES_ROW);
        if (row == null) return;
        List<PsiElement> expressions = SqlPsiNavigator.expressionChildren(row);
        List<String> names = columns.isEmpty() ? tableColumns : columns;
        for (int i = 0; i < expressions.size() && i < names.size(); i++) {
            written(names.get(i), expressions.get(i), null, statementAliases, scopes);
        }
    }

    /**
     * Pairs the columns a statement names with the outputs of the query it reads, in order. A
     * statement naming no column writes the table's columns in schema order; when that order is
     * unknown the query's own names stand in.
     */
    private void positional(@NotNull List<String> columns,
                            @NotNull SelectAnalyzer.QueryAnalysis query) {
        List<String> names = columns.isEmpty() ? tableColumns : columns;
        List<Map.Entry<String, List<InputColumn>>> produced = new ArrayList<>(query.outputs().entrySet());
        boolean byPosition = !names.isEmpty()
                && produced.stream().noneMatch(entry -> isStar(entry.getKey()));
        for (int i = 0; i < produced.size(); i++) {
            String name = byPosition && i < names.size() ? names.get(i) : produced.get(i).getKey();
            add(name, produced.get(i).getValue());
        }
        aliases.putAll(query.aliases());
    }

    /** The lineage of one written column: what the expression assigned to it reads. */
    private void written(@NotNull String column,
                         @NotNull PsiElement value,
                         @Nullable String targetAlias,
                         @NotNull Map<String, String> statementAliases,
                         @NotNull Map<String, Map<String, List<InputColumn>>> scopes) {
        outputs.computeIfAbsent(column, k -> new ArrayList<>());
        if (isType(value, REFERENCE)) {
            if (readsTarget(value, targetAlias) || isStar(value.getText())) return;
            String name = lastIdentifier(value);
            if (name == null) return;
            Confidence kind = name.equalsIgnoreCase(column) ? Confidence.DIRECT : Confidence.RENAME;
            add(column, selects.resolve(
                    selects.columnInput(value, statementAliases, Map.of(), kind), statementAliases, scopes));
            return;
        }
        for (PsiElement ref : topLevelColumnRefs(value)) {
            if (readsTarget(ref, targetAlias) || isStar(ref.getText())) continue;
            if (lastIdentifier(ref) == null) continue;
            add(column, selects.resolve(
                    selects.columnInput(ref, statementAliases, Map.of(), Confidence.DERIVED),
                    statementAliases, scopes));
        }
    }

    private @NotNull SelectAnalyzer.QueryAnalysis selectOutputs(@NotNull PsiElement holder) {
        PsiElement scope = firstQueryScope(holder);
        if (scope == null) return SelectAnalyzer.QueryAnalysis.EMPTY;
        Map<String, List<InputColumn>> produced = new LinkedHashMap<>();
        selects.processScope(scope, new LinkedHashMap<>(), produced);
        Map<String, String> read = new LinkedHashMap<>();
        selects.collectScopeAliases(scope, read);
        return new SelectAnalyzer.QueryAnalysis(produced, read);
    }

    private boolean readsTarget(@NotNull PsiElement reference, @Nullable String targetAlias) {
        String qualifier = SqlPsiNavigator.qualifier(reference);
        return qualifier != null && targetAlias != null && qualifier.equalsIgnoreCase(targetAlias);
    }

    private @NotNull Map<String, String> withoutTarget(@NotNull Map<String, String> statementAliases,
                                                       @Nullable String targetAlias) {
        if (targetAlias == null) return statementAliases;
        Map<String, String> copy = new LinkedHashMap<>(statementAliases);
        copy.keySet().removeIf(alias -> alias.equalsIgnoreCase(targetAlias));
        return copy;
    }

    private void add(@NotNull String column, @NotNull List<InputColumn> inputs) {
        outputs.computeIfAbsent(column, k -> new ArrayList<>()).addAll(inputs);
    }

    /**
     * The name of the table a DML instruction writes, as the last identifier of the reference
     * naming it. A quoted name is split around its backticks by the parser, which leaves the last
     * segment as an identifier of its own or as the first identifier of the alias expression.
     */
    private static @Nullable String writtenName(@NotNull PsiElement instruction) {
        String name = null;
        for (PsiElement child : instruction.getChildren()) {
            if (isType(child, REFERENCE_LIST) || isType(child, USING_CLAUSE)
                    || isType(child, SET_CLAUSE) || isType(child, FROM_CLAUSE)) {
                break;
            }
            if (isType(child, IDENTIFIER)) {
                name = child.getText();
            } else if (isType(child, REFERENCE)) {
                name = lastIdentifier(child);
            } else if (isType(child, AS_EXPRESSION)) {
                PsiElement reference = directChild(child, REFERENCE);
                PsiElement identifier = directChild(child, IDENTIFIER);
                name = reference != null ? lastIdentifier(reference)
                        : identifier != null ? identifier.getText() : name;
                break;
            }
        }
        return name == null ? null : name.replace("`", "");
    }

    private static @Nullable String writtenAlias(@NotNull PsiElement instruction) {
        PsiElement as = directChild(instruction, AS_EXPRESSION);
        return as == null ? null : lastIdentifier(as);
    }

    private static @NotNull List<String> columnList(@Nullable PsiElement referenceList) {
        List<String> columns = new ArrayList<>();
        if (referenceList == null) return columns;
        for (PsiElement reference : directChildren(referenceList, REFERENCE)) {
            String name = lastIdentifier(reference);
            if (name != null) columns.add(name);
        }
        return columns;
    }

    private static @Nullable PsiElement lastExpression(@NotNull PsiElement assignment) {
        PsiElement last = null;
        for (PsiElement child : assignment.getChildren()) {
            if (isExpression(child)) last = child;
        }
        return last;
    }
}
