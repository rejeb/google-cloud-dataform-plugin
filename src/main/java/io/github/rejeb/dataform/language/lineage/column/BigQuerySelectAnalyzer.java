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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the output columns of a BigQuery SELECT query to their input columns by
 * walking the SQL PSI tree. Renames and CTE aliases are chained so a final output
 * column resolves to the underlying source column name. All PSI work runs inside a
 * read action; any parse failure yields an empty result.
 */
public class BigQuerySelectAnalyzer implements SelectAnalyzer {

    private static final String SELECT_STATEMENT = "SqlSelectStatementImpl";
    private static final String WITH_QUERY = "SqlWithQueryExpressionImpl";
    private static final String WITH_CLAUSE = "SqlWithClauseImpl";
    private static final String NAMED_QUERY = "SqlNamedQueryDefinitionImpl";
    private static final String UNION = "BigQueryUnionExpressionImpl";
    private static final String QUERY = "SqlQueryExpressionImpl";
    private static final String SELECT_CLAUSE = "SqlSelectClauseImpl";
    private static final String TABLE_EXPRESSION = "SqlTableExpressionImpl";
    private static final String FROM_CLAUSE = "SqlFromClauseImpl";
    private static final String JOIN_CONDITION = "SqlJoinConditionClauseImpl";
    private static final String REFERENCE = "SqlReferenceExpressionImpl";
    private static final String IDENTIFIER = "SqlIdentifierImpl";
    private static final String AS_EXPRESSION = "BigQueryAsExpressionImpl";
    private static final String FUNCTION_CALL = "SqlFunctionCallExpressionImpl";

    private final Project project;

    public BigQuerySelectAnalyzer(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Returns a map of output-column name to the input columns that produce it.
     * Returns an empty map when the SQL cannot be parsed.
     */
    @Override
    public @NotNull Map<String, List<InputColumn>> analyze(@NotNull String sql) {
        return analyzeQuery(sql).outputs();
    }

    /**
     * Returns a map of source alias (or bare table token) to the table token as written
     * in the outermost FROM/JOIN clause. Returns an empty map on parse failure.
     */
    @Override
    public @NotNull Map<String, String> fromAliases(@NotNull String sql) {
        return analyzeQuery(sql).aliases();
    }

    /**
     * Parses the SQL once and returns both the output-to-input column map and the FROM aliases.
     * Preferred over calling {@link #analyze} and {@link #fromAliases} separately, which would
     * parse the same SQL twice.
     */
    @Override
    public @NotNull QueryAnalysis analyzeQuery(@NotNull String sql) {
        return ReadAction.compute(() -> {
            try {
                PsiElement stmt = parseStatement(sql);
                if (stmt == null) return QueryAnalysis.EMPTY;
                return new QueryAnalysis(outputsOf(stmt), aliasesOf(stmt));
            } catch (Exception e) {
                return QueryAnalysis.EMPTY;
            }
        });
    }

    private @NotNull Map<String, List<InputColumn>> outputsOf(@NotNull PsiElement stmt) {
        Map<String, Map<String, List<InputColumn>>> cteMap = new LinkedHashMap<>();
        PsiElement top = firstComposite(stmt);
        PsiElement outer = top;
        if (isType(top, WITH_QUERY)) {
            PsiElement withClause = directChild(top, WITH_CLAUSE);
            if (withClause != null) collectCtes(withClause, cteMap);
            outer = firstQueryOrUnion(top);
        }
        Map<String, List<InputColumn>> result = new LinkedHashMap<>();
        for (PsiElement query : expandQueries(outer)) {
            processQuery(query, cteMap, result);
        }
        return result;
    }

    private @NotNull Map<String, String> aliasesOf(@NotNull PsiElement stmt) {
        PsiElement top = firstComposite(stmt);
        PsiElement outer = top;
        if (isType(top, WITH_QUERY)) outer = firstQueryOrUnion(top);
        Map<String, String> aliases = new LinkedHashMap<>();
        for (PsiElement query : expandQueries(outer)) {
            aliases.putAll(fromAliasesOf(query));
        }
        return aliases;
    }

    private @Nullable PsiElement parseStatement(@NotNull String sql) {
        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("temp.sql", BigQueryDialect.INSTANCE, sql);
        return findDescendant(file, SELECT_STATEMENT);
    }

    private void collectCtes(@NotNull PsiElement withClause,
                             @NotNull Map<String, Map<String, List<InputColumn>>> cteMap) {
        for (PsiElement def : directChildren(withClause, NAMED_QUERY)) {
            PsiElement nameId = directChild(def, IDENTIFIER);
            PsiElement innerExpr = firstQueryOrUnion(def);
            if (nameId == null || innerExpr == null) continue;
            Map<String, List<InputColumn>> inner = new LinkedHashMap<>();
            for (PsiElement query : expandQueries(innerExpr)) {
                processQuery(query, cteMap, inner);
            }
            cteMap.put(nameId.getText(), inner);
        }
    }

    private void processQuery(@NotNull PsiElement query,
                              @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                              @NotNull Map<String, List<InputColumn>> result) {
        PsiElement selectClause = directChild(query, SELECT_CLAUSE);
        if (selectClause == null) return;
        Map<String, String> aliases = fromAliasesOf(query);
        for (PsiElement item : selectItems(selectClause)) {
            classifyItem(item, aliases, cteMap, result);
        }
    }

    private void classifyItem(@NotNull PsiElement item,
                              @NotNull Map<String, String> aliases,
                              @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                              @NotNull Map<String, List<InputColumn>> result) {
        if (isType(item, REFERENCE)) {
            String text = item.getText();
            if (isStar(text)) {
                add(result, text, resolve(new InputColumn(qualifier(item), "*", Confidence.STAR, true), aliases, cteMap));
                return;
            }
            String name = lastIdentifier(item);
            if (name == null) return;
            add(result, name, resolve(new InputColumn(qualifier(item), name, Confidence.DIRECT, false), aliases, cteMap));
            return;
        }
        String outputName = lastIdentifier(item);
        PsiElement inner = firstExpression(item);
        if (outputName == null || inner == null) return;
        if (isType(inner, REFERENCE)) {
            String text = inner.getText();
            if (isStar(text)) {
                add(result, outputName, resolve(new InputColumn(qualifier(inner), "*", Confidence.STAR, true), aliases, cteMap));
                return;
            }
            String name = lastIdentifier(inner);
            if (name == null) return;
            Confidence kind = name.equalsIgnoreCase(outputName) ? Confidence.DIRECT : Confidence.RENAME;
            add(result, outputName, resolve(new InputColumn(qualifier(inner), name, kind, false), aliases, cteMap));
            return;
        }
        result.computeIfAbsent(outputName, k -> new ArrayList<>());
        for (PsiElement ref : topLevelColumnRefs(inner)) {
            String name = lastIdentifier(ref);
            if (name == null || isStar(ref.getText())) continue;
            add(result, outputName, resolve(new InputColumn(qualifier(ref), name, Confidence.DERIVED, false), aliases, cteMap));
        }
    }

    private @NotNull List<InputColumn> resolve(@NotNull InputColumn input,
                                               @NotNull Map<String, String> aliases,
                                               @NotNull Map<String, Map<String, List<InputColumn>>> cteMap) {
        if (input.star()) return List.of(input);
        String source = resolveSource(input.sourceAlias(), aliases);
        if (source != null && cteMap.containsKey(source)) {
            List<InputColumn> sub = cteMap.get(source).get(input.columnName());
            if (sub != null && !sub.isEmpty()) return new ArrayList<>(sub);
        }
        if (input.sourceAlias() != null && cteMap.containsKey(input.sourceAlias())) {
            List<InputColumn> sub = cteMap.get(input.sourceAlias()).get(input.columnName());
            if (sub != null && !sub.isEmpty()) return new ArrayList<>(sub);
        }
        return List.of(input);
    }

    private @Nullable String resolveSource(@Nullable String alias, @NotNull Map<String, String> aliases) {
        if (alias != null) return aliases.getOrDefault(alias, alias);
        if (aliases.size() == 1) return aliases.values().iterator().next();
        return null;
    }

    private void add(@NotNull Map<String, List<InputColumn>> result,
                     @NotNull String outputName,
                     @NotNull List<InputColumn> inputs) {
        result.computeIfAbsent(outputName, k -> new ArrayList<>()).addAll(inputs);
    }

    private @NotNull Map<String, String> fromAliasesOf(@NotNull PsiElement query) {
        Map<String, String> aliases = new LinkedHashMap<>();
        PsiElement tableExpression = directChild(query, TABLE_EXPRESSION);
        PsiElement fromClause = tableExpression != null ? directChild(tableExpression, FROM_CLAUSE) : null;
        if (fromClause == null) return aliases;
        collectFromSources(fromClause, aliases);
        return aliases;
    }

    private void collectFromSources(@NotNull PsiElement element, @NotNull Map<String, String> aliases) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, JOIN_CONDITION)) continue;
            if (isType(child, AS_EXPRESSION)) {
                PsiElement tableRef = firstChild(child, REFERENCE);
                String alias = lastIdentifier(child);
                if (tableRef != null && alias != null) {
                    aliases.put(alias, tableRef.getText());
                }
            } else if (isType(child, REFERENCE)) {
                aliases.put(child.getText(), child.getText());
            } else {
                collectFromSources(child, aliases);
            }
        }
    }

    private @NotNull List<PsiElement> selectItems(@NotNull PsiElement selectClause) {
        List<PsiElement> items = new ArrayList<>();
        for (PsiElement child : selectClause.getChildren()) {
            String name = child.getClass().getSimpleName();
            if (name.endsWith("Expression") || name.endsWith("ExpressionImpl")) {
                items.add(child);
            }
        }
        return items;
    }

    private @NotNull List<PsiElement> topLevelColumnRefs(@NotNull PsiElement expression) {
        List<PsiElement> refs = new ArrayList<>();
        collectColumnRefs(expression, refs);
        return refs;
    }

    /**
     * Collects column references from an expression, skipping function names (callees) and
     * digging into their arguments, so {@code SAFE_CAST(x AS INT64)} yields {@code x}, not
     * {@code SAFE_CAST}. A plain column reference is added without descending into it (its
     * qualifier is part of the same reference).
     */
    private void collectColumnRefs(@NotNull PsiElement element, @NotNull List<PsiElement> refs) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, REFERENCE)) {
                if (isFunctionCallee(child)) {
                    collectColumnRefs(child, refs);
                } else if (!isStar(child.getText())) {
                    refs.add(child);
                }
            } else {
                collectColumnRefs(child, refs);
            }
        }
    }

    private boolean isFunctionCallee(@NotNull PsiElement reference) {
        PsiElement parent = reference.getParent();
        return parent != null && parent.getClass().getSimpleName().equals(FUNCTION_CALL);
    }

    private @Nullable String qualifier(@NotNull PsiElement reference) {
        PsiElement child = firstChild(reference, REFERENCE);
        return child != null ? child.getText() : null;
    }

    private @Nullable String lastIdentifier(@NotNull PsiElement element) {
        String result = null;
        for (PsiElement child : element.getChildren()) {
            if (isType(child, IDENTIFIER)) result = child.getText();
        }
        return result;
    }

    private @Nullable PsiElement firstExpression(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            String name = child.getClass().getSimpleName();
            if (name.endsWith("Expression") || name.endsWith("ExpressionImpl")) return child;
        }
        return null;
    }

    private @NotNull List<PsiElement> expandQueries(@Nullable PsiElement expression) {
        List<PsiElement> queries = new ArrayList<>();
        if (expression == null) return queries;
        if (isType(expression, UNION)) {
            queries.addAll(directChildren(expression, QUERY));
        } else if (isType(expression, QUERY)) {
            queries.add(expression);
        } else {
            PsiElement query = directChild(expression, QUERY);
            if (query != null) queries.add(query);
        }
        return queries;
    }

    private @Nullable PsiElement firstQueryOrUnion(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, QUERY) || isType(child, UNION)) return child;
        }
        return null;
    }

    private @Nullable PsiElement firstComposite(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (!child.getClass().getSimpleName().equals("SqlTokenElement")) return child;
        }
        return null;
    }

    private @Nullable PsiElement directChild(@NotNull PsiElement element, @NotNull String simpleName) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, simpleName)) return child;
        }
        return null;
    }

    private @NotNull List<PsiElement> directChildren(@NotNull PsiElement element, @NotNull String simpleName) {
        List<PsiElement> children = new ArrayList<>();
        for (PsiElement child : element.getChildren()) {
            if (isType(child, simpleName)) children.add(child);
        }
        return children;
    }

    private @Nullable PsiElement firstChild(@NotNull PsiElement element, @NotNull String simpleName) {
        return directChild(element, simpleName);
    }

    private @Nullable PsiElement findDescendant(@NotNull PsiElement root, @NotNull String simpleName) {
        for (PsiElement child : root.getChildren()) {
            if (isType(child, simpleName)) return child;
            PsiElement found = findDescendant(child, simpleName);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isType(@Nullable PsiElement element, @NotNull String simpleName) {
        return element != null && element.getClass().getSimpleName().equals(simpleName);
    }

    private boolean isStar(@NotNull String text) {
        return text.equals("*") || text.endsWith(".*");
    }
}
