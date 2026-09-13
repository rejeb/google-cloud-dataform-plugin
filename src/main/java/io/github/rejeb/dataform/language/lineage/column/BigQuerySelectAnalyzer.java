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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.selectItems;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.expressionChildren;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.topLevelColumnRefs;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.qualifier;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.lastIdentifier;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.firstExpression;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.expandQueries;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.firstQueryScope;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.firstQueryOrUnion;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.firstComposite;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.directChild;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.directChildren;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.firstChild;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.findDescendant;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.isType;
import static io.github.rejeb.dataform.language.lineage.column.SqlPsiNavigator.isStar;
import static io.github.rejeb.dataform.language.lineage.column.PivotAnalyzer.pivotOutputs;
import static io.github.rejeb.dataform.language.lineage.column.PivotAnalyzer.unpivotOutputs;
import static io.github.rejeb.dataform.language.lineage.column.PivotAnalyzer.pivotOf;
import static io.github.rejeb.dataform.language.lineage.column.PivotAnalyzer.pivotSourceOf;
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

    private static final Logger LOG = Logger.getInstance(BigQuerySelectAnalyzer.class);

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
    private static final String PARENTHESIZED = "SqlParenthesizedExpressionImpl";
    private static final String STRUCT_EXPRESSION = "BigQueryParenthesizedExpression";
    private static final String DERIVED_SCOPE_PREFIX = "#derived";
    private static final String PIVOTED_QUERY = "BigQueryPivotedQueryExpressionImpl";
    private static final String UNPIVOTED_QUERY = "SqlUnpivotedQueryExpressionImpl";
    private static final String PIVOT_COLUMNS_CLAUSE = "SqlPivotColumnsClauseImpl";
    private static final String CLAUSE = "SqlClauseImpl";
    private static final String FUNCTION_CALL_TABLE = "SqlFunctionCallTableExpressionImpl";
    private static final String EXPRESSION_LIST = "SqlExpressionListImpl";
    private static final String UNNEST = "UNNEST";

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
        return ReadAction.computeBlocking(() -> {
            try {
                PsiElement stmt = parseStatement(sql);
                if (stmt == null) {
                    LOG.debug("No SELECT statement found in the analyzed query");
                    return QueryAnalysis.EMPTY;
                }
                return new QueryAnalysis(outputsOf(stmt), aliasesOf(stmt));
            } catch (Exception e) {
                LOG.warn("Column lineage analysis failed; the SQL PSI shape may have changed", e);
                return QueryAnalysis.EMPTY;
            }
        });
    }

    /**
     * Lineage of the statements writing {@code tableName}, read by {@link BigQueryDmlAnalyzer}.
     * When no statement writes it, the last {@code SELECT} of the SQL stands for the output, as it
     * does for a query action.
     */
    @Override
    public @NotNull QueryAnalysis analyzeWrites(@NotNull String sql,
                                                @NotNull String tableName,
                                                @NotNull List<String> tableColumns) {
        return ReadAction.computeBlocking(() -> {
            try {
                PsiFile file = PsiFileFactory.getInstance(project)
                        .createFileFromText("temp.sql", BigQueryDialect.INSTANCE, sql);
                BigQueryDmlAnalyzer writes = new BigQueryDmlAnalyzer(this, tableName, tableColumns);
                QueryAnalysis written = writes.analyze(file);
                if (writes.wroteTheTable()) return written;
                PsiElement stmt = lastSelectStatement(file);
                if (stmt == null) return QueryAnalysis.EMPTY;
                return new QueryAnalysis(outputsOf(stmt), aliasesOf(stmt));
            } catch (Exception e) {
                LOG.warn("Column lineage analysis failed; the SQL PSI shape may have changed", e);
                return QueryAnalysis.EMPTY;
            }
        });
    }

    private static @Nullable PsiElement lastSelectStatement(@NotNull PsiFile file) {
        PsiElement last = null;
        for (PsiElement child : file.getChildren()) {
            if (isType(child, SELECT_STATEMENT)) last = child;
        }
        return last;
    }

    private @NotNull Map<String, List<InputColumn>> outputsOf(@NotNull PsiElement stmt) {
        Map<String, List<InputColumn>> result = new LinkedHashMap<>();
        PsiElement top = firstComposite(stmt);
        if (top != null) processScope(top, new LinkedHashMap<>(), result);
        return result;
    }

    /**
     * Processes a query scope: a query, a union, or a WITH expression whose CTEs are visible to
     * its own body only. Scopes nest, so a derived table or a CTE body may declare CTEs of its
     * own on top of the ones it inherits.
     */
    void processScope(@NotNull PsiElement expression,
                              @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                              @NotNull Map<String, List<InputColumn>> result) {
        PsiElement body = expression;
        Map<String, Map<String, List<InputColumn>>> scopes = cteMap;
        if (isType(expression, WITH_QUERY)) {
            scopes = new LinkedHashMap<>(cteMap);
            PsiElement withClause = directChild(expression, WITH_CLAUSE);
            if (withClause != null) collectCtes(withClause, scopes);
            body = firstQueryOrUnion(expression);
        }
        for (PsiElement query : expandQueries(body)) {
            processQuery(query, scopes, result);
        }
    }

    private @NotNull Map<String, String> aliasesOf(@NotNull PsiElement stmt) {
        Map<String, String> aliases = new LinkedHashMap<>();
        PsiElement top = firstComposite(stmt);
        if (top != null) collectScopeAliases(top, aliases);
        return aliases;
    }

    void collectScopeAliases(@NotNull PsiElement expression, @NotNull Map<String, String> aliases) {
        PsiElement body = expression;
        if (isType(expression, WITH_QUERY)) {
            PsiElement withClause = directChild(expression, WITH_CLAUSE);
            if (withClause != null) collectCteAliases(withClause, aliases);
            body = firstQueryOrUnion(expression);
        }
        for (PsiElement query : expandQueries(body)) {
            collectQueryAliases(query, aliases);
        }
    }

    /**
     * Collects the FROM aliases of a query and of every derived table nested in its FROM clause.
     * Columns of a derived table are inlined with the aliases they carry inside it, so those
     * aliases must be resolvable from the outermost scope too.
     */
    private void collectQueryAliases(@NotNull PsiElement query, @NotNull Map<String, String> aliases) {
        aliases.putAll(fromAliasesOf(query));
        PsiElement fromClause = fromClauseOf(query);
        if (fromClause != null) collectDerivedAliases(fromClause, aliases);
    }

    private void collectDerivedAliases(@NotNull PsiElement element, @NotNull Map<String, String> aliases) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, JOIN_CONDITION)) continue;
            PsiElement derived = derivedTableOf(child);
            if (derived != null) {
                PsiElement scope = firstQueryScope(derived);
                if (scope != null) collectScopeAliases(scope, aliases);
            } else if (!isType(child, REFERENCE)) {
                collectDerivedAliases(child, aliases);
            }
        }
    }

    /**
     * Collects the FROM aliases declared inside each CTE. Inputs coming from a CTE are inlined
     * with the alias they carry in the CTE body, so those aliases must be resolvable too.
     */
    private void collectCteAliases(@NotNull PsiElement withClause,
                                   @NotNull Map<String, String> aliases) {
        for (PsiElement def : directChildren(withClause, NAMED_QUERY)) {
            PsiElement innerExpr = firstQueryScope(def);
            if (innerExpr == null) continue;
            collectScopeAliases(innerExpr, aliases);
        }
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
            PsiElement innerExpr = firstQueryScope(def);
            if (nameId == null || innerExpr == null) continue;
            Map<String, List<InputColumn>> inner = new LinkedHashMap<>();
            processScope(innerExpr, cteMap, inner);
            cteMap.put(nameId.getText(), inner);
        }
    }

    private void processQuery(@NotNull PsiElement query,
                              @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                              @NotNull Map<String, List<InputColumn>> result) {
        PsiElement selectClause = directChild(query, SELECT_CLAUSE);
        if (selectClause == null) return;
        Map<String, String> aliases = fromAliasesOf(query);
        Map<String, Map<String, List<InputColumn>>> scopes = new LinkedHashMap<>(cteMap);
        Map<String, List<InputColumn>> pivoted = new LinkedHashMap<>();
        collectFromScopes(query, cteMap, scopes, aliases, pivoted);
        Map<String, UnnestSource> unnests = unnestSourcesOf(query, aliases);
        List<PsiElement> items = selectItems(selectClause);
        for (PsiElement item : items) {
            classifyItem(item, aliases, unnests, scopes, result, "");
        }
        if (!pivoted.isEmpty() && items.stream().anyMatch(item -> isStar(item.getText()))) {
            pivoted.forEach((name, inputs) -> add(result, name, inputs));
        }
    }

    /**
     * Registers every derived table (an inline subquery in the FROM clause) as an additional
     * named scope, so that columns selected from it are inlined down to the columns of the
     * underlying tables exactly as CTE columns are. An unaliased derived table gets a synthetic
     * scope name, which also makes it the single resolvable source of the enclosing query.
     */
    private void collectFromScopes(@NotNull PsiElement query,
                                   @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                                   @NotNull Map<String, Map<String, List<InputColumn>>> scopes,
                                   @NotNull Map<String, String> aliases,
                                   @NotNull Map<String, List<InputColumn>> pivoted) {
        PsiElement fromClause = fromClauseOf(query);
        if (fromClause == null) return;
        collectFromScopes(fromClause, cteMap, scopes, aliases, pivoted, new int[]{0});
    }

    void collectFromScopes(@NotNull PsiElement element,
                                   @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                                   @NotNull Map<String, Map<String, List<InputColumn>>> scopes,
                                   @NotNull Map<String, String> aliases,
                                   @NotNull Map<String, List<InputColumn>> pivoted,
                                   int @NotNull [] counter) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, JOIN_CONDITION)) continue;
            PsiElement pivot = pivotOf(child);
            if (pivot != null) {
                collectPivotScope(child, pivot, cteMap, scopes, aliases, pivoted, counter);
                continue;
            }
            PsiElement derived = derivedTableOf(child);
            if (derived != null) {
                String name = isType(child, AS_EXPRESSION) ? lastIdentifier(child) : null;
                if (name == null) name = DERIVED_SCOPE_PREFIX + counter[0]++;
                scopes.put(name, derivedOutputs(derived, cteMap));
                aliases.putIfAbsent(name, name);
            } else if (!isType(child, REFERENCE)) {
                collectFromScopes(child, cteMap, scopes, aliases, pivoted, counter);
            }
        }
    }

    private @NotNull Map<String, List<InputColumn>> derivedOutputs(@NotNull PsiElement derived,
                                                                   @NotNull Map<String, Map<String, List<InputColumn>>> cteMap) {
        Map<String, List<InputColumn>> inner = new LinkedHashMap<>();
        PsiElement scope = firstQueryScope(derived);
        if (scope != null) processScope(scope, cteMap, inner);
        return inner;
    }

    /**
     * Registers the columns a PIVOT or UNPIVOT operator produces. The operator consumes some of
     * its source columns and creates new ones, so its outputs are registered as a scope named
     * after the pivoted source: a column selected by name resolves through it, while a column the
     * operator passes through is not found there and keeps resolving against the source table.
     * The generated columns are also returned separately, because {@code SELECT *} must expose
     * them even though no name appears in the query.
     */
    private void collectPivotScope(@NotNull PsiElement fromItem,
                                   @NotNull PsiElement pivot,
                                   @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                                   @NotNull Map<String, Map<String, List<InputColumn>>> scopes,
                                   @NotNull Map<String, String> aliases,
                                   @NotNull Map<String, List<InputColumn>> pivoted,
                                   int @NotNull [] counter) {
        PsiElement source = pivotSourceOf(pivot);
        Map<String, List<InputColumn>> sourceOutputs = Map.of();
        String sourceName = null;
        if (source != null && isType(source, REFERENCE)) {
            sourceName = source.getText();
        } else if (source != null) {
            sourceName = DERIVED_SCOPE_PREFIX + counter[0]++;
            sourceOutputs = derivedOutputs(source, cteMap);
            scopes.put(sourceName, sourceOutputs);
            aliases.putIfAbsent(sourceName, sourceName);
        }

        Map<String, List<InputColumn>> outputs = isType(pivot, PIVOTED_QUERY)
                ? pivotOutputs(pivot, sourceOutputs)
                : unpivotOutputs(pivot, sourceOutputs);
        if (outputs.isEmpty()) return;

        pivoted.putAll(outputs);
        if (sourceName != null) scopes.put(sourceName, merged(scopes.get(sourceName), outputs));
        String alias = isType(fromItem, AS_EXPRESSION) ? lastIdentifier(fromItem) : null;
        if (alias != null) {
            scopes.put(alias, merged(scopes.get(sourceName), outputs));
            aliases.putIfAbsent(alias, sourceName != null ? sourceName : alias);
        }
    }

    private @NotNull Map<String, List<InputColumn>> merged(@Nullable Map<String, List<InputColumn>> base,
                                                           @NotNull Map<String, List<InputColumn>> extra) {
        Map<String, List<InputColumn>> result = new LinkedHashMap<>();
        if (base != null) result.putAll(base);
        result.putAll(extra);
        return result;
    }









    /**
     * The parenthesized subquery of a FROM item when that item is a derived table, with or
     * without an alias; {@code null} for a plain table reference.
     */
    private @Nullable PsiElement derivedTableOf(@NotNull PsiElement fromItem) {
        if (isType(fromItem, PARENTHESIZED)) return fromItem;
        if (isType(fromItem, AS_EXPRESSION)) return directChild(fromItem, PARENTHESIZED);
        return null;
    }

    private void classifyItem(@NotNull PsiElement item,
                              @NotNull Map<String, String> aliases,
                              @NotNull Map<String, UnnestSource> unnests,
                              @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                              @NotNull Map<String, List<InputColumn>> result,
                              @NotNull String prefix) {
        if (isType(item, REFERENCE)) {
            String text = item.getText();
            if (isStar(text)) {
                if (expandStar(item, aliases, cteMap, result, prefix)) return;
                add(result, prefix + text, resolve(new InputColumn(qualifier(item), "*", Confidence.STAR, true), aliases, cteMap));
                return;
            }
            String name = lastIdentifier(item);
            if (name == null) return;
            add(result, prefix + name,
                    resolve(columnInput(item, aliases, unnests, Confidence.DIRECT), aliases, cteMap));
            return;
        }
        String outputName = lastIdentifier(item);
        PsiElement inner = firstExpression(item);
        if (outputName == null || inner == null) return;
        if (isType(inner, STRUCT_EXPRESSION)
                && classifyStructFields(inner, aliases, unnests, cteMap, result, prefix + outputName + ".")) {
            return;
        }
        if (isType(inner, REFERENCE)) {
            String text = inner.getText();
            if (isStar(text)) {
                add(result, prefix + outputName, resolve(new InputColumn(qualifier(inner), "*", Confidence.STAR, true), aliases, cteMap));
                return;
            }
            String name = lastIdentifier(inner);
            if (name == null) return;
            Confidence kind = name.equalsIgnoreCase(outputName) ? Confidence.DIRECT : Confidence.RENAME;
            add(result, prefix + outputName,
                    resolve(columnInput(inner, aliases, unnests, kind), aliases, cteMap));
            return;
        }
        result.computeIfAbsent(prefix + outputName, k -> new ArrayList<>());
        for (PsiElement ref : topLevelColumnRefs(inner)) {
            String name = lastIdentifier(ref);
            if (name == null || isStar(ref.getText())) continue;
            add(result, prefix + outputName,
                    resolve(columnInput(ref, aliases, unnests, Confidence.DERIVED), aliases, cteMap));
        }
    }

    /**
     * Expands a star over sources whose columns are already known — a CTE, a derived table or a
     * pivot — into one output column per column of those sources, so that a column computed inside
     * a subquery keeps its own identity and its own inputs instead of collapsing into an opaque
     * star. Returns {@code false} when at least one source of the star is a plain table, whose
     * columns only the schema knows: the star then stays a star and is expanded later against that
     * schema. Nothing is written to {@code result} unless the whole star can be expanded.
     */
    private boolean expandStar(@NotNull PsiElement star,
                               @NotNull Map<String, String> aliases,
                               @NotNull Map<String, Map<String, List<InputColumn>>> scopes,
                               @NotNull Map<String, List<InputColumn>> result,
                               @NotNull String prefix) {
        String qualifier = qualifier(star);
        List<Map<String, List<InputColumn>>> sources = new ArrayList<>();
        if (qualifier != null) {
            Map<String, List<InputColumn>> scope = scopeOf(qualifier, aliases, scopes);
            if (scope == null) return false;
            sources.add(scope);
        } else {
            if (aliases.isEmpty()) return false;
            for (String alias : aliases.keySet()) {
                Map<String, List<InputColumn>> scope = scopeOf(alias, aliases, scopes);
                if (scope == null) return false;
                sources.add(scope);
            }
        }
        for (Map<String, List<InputColumn>> scope : sources) {
            if (scope.isEmpty() || scope.keySet().stream().anyMatch(SqlPsiNavigator::isStar)) return false;
        }
        for (Map<String, List<InputColumn>> scope : sources) {
            scope.forEach((name, inputs) -> add(result, prefix + name, inputs));
        }
        return true;
    }

    /** The known columns of a FROM source, looked up by its own name or by the table token it aliases. */
    private @Nullable Map<String, List<InputColumn>> scopeOf(@NotNull String name,
                                                             @NotNull Map<String, String> aliases,
                                                             @NotNull Map<String, Map<String, List<InputColumn>>> scopes) {
        Map<String, List<InputColumn>> direct = lookup(scopes, name);
        if (direct != null) return direct;
        String alias = matchingAlias(aliases, name);
        String token = alias != null ? aliases.get(alias) : null;
        return token != null ? lookup(scopes, token) : null;
    }

    /**
     * Builds the input column a reference denotes, keeping the dotted path relative to its table
     * source. The leading segments are matched against the FROM aliases, longest first, so a
     * struct path such as {@code t.payload.amount} keeps {@code payload.amount} as the column
     * name instead of collapsing to its last identifier, which no schema declares. A reference
     * rooted on an UNNEST alias is rewritten onto the array column it iterates.
     */
    @NotNull InputColumn columnInput(@NotNull PsiElement reference,
                                             @NotNull Map<String, String> aliases,
                                             @NotNull Map<String, UnnestSource> unnests,
                                             @NotNull Confidence kind) {
        List<String> segments = segmentsOf(reference.getText());
        if (segments.isEmpty()) return new InputColumn(null, reference.getText(), kind, false);

        UnnestSource unnest = lookup(unnests, segments.getFirst());
        if (unnest != null) {
            String rest = join(segments, 1, segments.size());
            String path = rest.isEmpty() ? unnest.arrayPath() : unnest.arrayPath() + "." + rest;
            return new InputColumn(unnest.tableAlias(), path, kind, false);
        }
        for (int i = segments.size() - 1; i >= 1; i--) {
            String alias = matchingAlias(aliases, join(segments, 0, i));
            if (alias != null) {
                return new InputColumn(alias, join(segments, i, segments.size()), kind, false);
            }
        }
        return new InputColumn(null, join(segments, 0, segments.size()), kind, false);
    }

    private @NotNull List<String> segmentsOf(@NotNull String text) {
        List<String> segments = new ArrayList<>();
        for (String part : text.replace("`", "").split("\\.")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) segments.add(trimmed);
        }
        return segments;
    }

    private @NotNull String join(@NotNull List<String> segments, int from, int to) {
        return String.join(".", segments.subList(from, to));
    }

    private @Nullable String matchingAlias(@NotNull Map<String, String> aliases, @NotNull String prefix) {
        if (aliases.containsKey(prefix)) return prefix;
        for (String key : aliases.keySet()) {
            if (key.equalsIgnoreCase(prefix)) return key;
        }
        return null;
    }

    private <T> @Nullable T lookup(@NotNull Map<String, T> map, @NotNull String key) {
        T direct = map.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, T> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    /**
     * Collects the {@code UNNEST(array) AS alias} items of a FROM clause. The alias is not a table
     * of its own: it iterates an array column, so a reference rooted on it must resolve back to
     * that column for the schema to recognise it.
     */
    private @NotNull Map<String, UnnestSource> unnestSourcesOf(@NotNull PsiElement query,
                                                               @NotNull Map<String, String> aliases) {
        Map<String, UnnestSource> unnests = new LinkedHashMap<>();
        PsiElement fromClause = fromClauseOf(query);
        if (fromClause != null) collectUnnestSources(fromClause, aliases, unnests);
        return unnests;
    }

    private void collectUnnestSources(@NotNull PsiElement element,
                                      @NotNull Map<String, String> aliases,
                                      @NotNull Map<String, UnnestSource> unnests) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, JOIN_CONDITION) || isType(child, REFERENCE)) continue;
            if (isType(child, AS_EXPRESSION)) {
                PsiElement call = directChild(child, FUNCTION_CALL_TABLE);
                String alias = lastIdentifier(child);
                if (call != null && alias != null) {
                    UnnestSource source = unnestSourceOf(call, aliases);
                    if (source != null) {
                        unnests.put(alias, source);
                        continue;
                    }
                }
            }
            collectUnnestSources(child, aliases, unnests);
        }
    }

    private @Nullable UnnestSource unnestSourceOf(@NotNull PsiElement callTable,
                                                  @NotNull Map<String, String> aliases) {
        PsiElement call = directChild(callTable, FUNCTION_CALL);
        if (call == null) return null;
        PsiElement callee = directChild(call, REFERENCE);
        if (callee == null || !callee.getText().trim().equalsIgnoreCase(UNNEST)) return null;
        PsiElement arguments = directChild(call, EXPRESSION_LIST);
        if (arguments == null) return null;
        PsiElement argument = directChild(arguments, REFERENCE);
        if (argument == null) return null;

        List<String> segments = segmentsOf(argument.getText());
        if (segments.isEmpty()) return null;
        for (int i = segments.size() - 1; i >= 1; i--) {
            String alias = matchingAlias(aliases, join(segments, 0, i));
            if (alias != null) return new UnnestSource(alias, join(segments, i, segments.size()));
        }
        return new UnnestSource(null, join(segments, 0, segments.size()));
    }

    private record UnnestSource(@Nullable String tableAlias, @NotNull String arrayPath) {
    }

    /**
     * Maps each field of a {@code STRUCT(...)} constructor to its own dotted output path, so that
     * a field keeps the identity it has in the target schema instead of being merged into the
     * struct column itself. Only a parenthesized expression carrying at least one named field is
     * a struct constructor; an ordinary parenthesized expression shares the same PSI type and
     * must keep being handled as a plain expression, so {@code false} is returned for it.
     */
    private boolean classifyStructFields(@NotNull PsiElement structExpression,
                                         @NotNull Map<String, String> aliases,
                                         @NotNull Map<String, UnnestSource> unnests,
                                         @NotNull Map<String, Map<String, List<InputColumn>>> cteMap,
                                         @NotNull Map<String, List<InputColumn>> result,
                                         @NotNull String prefix) {
        if (directChild(structExpression, AS_EXPRESSION) == null) return false;
        List<PsiElement> fields = expressionChildren(structExpression);
        if (fields.isEmpty()) return false;
        for (PsiElement field : fields) {
            classifyItem(field, aliases, unnests, cteMap, result, prefix);
        }
        return true;
    }

    @NotNull List<InputColumn> resolve(@NotNull InputColumn input,
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

    private @Nullable PsiElement fromClauseOf(@NotNull PsiElement query) {
        PsiElement tableExpression = directChild(query, TABLE_EXPRESSION);
        return tableExpression != null ? directChild(tableExpression, FROM_CLAUSE) : null;
    }

    private @NotNull Map<String, String> fromAliasesOf(@NotNull PsiElement query) {
        Map<String, String> aliases = new LinkedHashMap<>();
        PsiElement fromClause = fromClauseOf(query);
        if (fromClause == null) return aliases;
        collectFromSources(fromClause, aliases);
        return aliases;
    }

    void collectFromSources(@NotNull PsiElement element, @NotNull Map<String, String> aliases) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, JOIN_CONDITION)) continue;
            if (derivedTableOf(child) != null) continue;
            PsiElement pivot = pivotOf(child);
            if (pivot != null) {
                collectPivotSource(child, pivot, aliases);
                continue;
            }
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

    /**
     * Registers the table a pivot operator reads, without descending into the pivot clauses: the
     * aggregates, the key and the pivot values are columns and literals, never table sources.
     */
    private void collectPivotSource(@NotNull PsiElement fromItem,
                                    @NotNull PsiElement pivot,
                                    @NotNull Map<String, String> aliases) {
        PsiElement source = pivotSourceOf(pivot);
        if (source == null || !isType(source, REFERENCE)) return;
        String token = source.getText();
        aliases.put(token, token);
        String alias = isType(fromItem, AS_EXPRESSION) ? lastIdentifier(fromItem) : null;
        if (alias != null) aliases.put(alias, token);
    }

}
