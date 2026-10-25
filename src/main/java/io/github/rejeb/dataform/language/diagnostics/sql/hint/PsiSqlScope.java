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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.sql.psi.SqlReferenceExpression;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.documentation.bigquery.BigQueryFunctionDoc;
import io.github.rejeb.dataform.language.documentation.bigquery.BigQueryFunctionDocService;
import io.github.rejeb.dataform.language.highlight.SqlxQuerySources;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.columns.origin.SqlxOutputColumnLocator;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The scope of a query of a SQLX file, read from the SQL injected into it: each source of its
 * FROM clause with the columns the plugin knows of, a Dataform table through its extracted schema
 * and a common table expression through the columns its query names.
 */
public final class PsiSqlScope implements SqlScope {

    private final Project project;
    private final @Nullable PsiElement query;
    private final int sqlStart;
    private final Map<String, List<String>> columnsBySource;

    private PsiSqlScope(@NotNull Project project, @Nullable PsiElement query, int sqlStart) {
        this.project = project;
        this.query = query;
        this.sqlStart = sqlStart;
        this.columnsBySource = query == null ? Map.of() : CachedValuesManager.getCachedValue(query,
                () -> CachedValueProvider.Result.create(sourcesOf(query), PsiModificationTracker.MODIFICATION_COUNT,
                        DataformTableSchemaService.getInstance(project)));
    }

    /**
     * The scope of the query holding a host offset of a SQLX file, or of its main query when no
     * SQL is injected there.
     */
    public static @NotNull SqlScope at(@NotNull PsiFile hostFile, int hostOffset) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(hostFile.getProject());
        PsiElement injected = manager.findInjectedElementAt(hostFile, hostOffset);
        if (injected == null && hostOffset > 0) injected = manager.findInjectedElementAt(hostFile, hostOffset - 1);
        return injected == null ? mainQueryOf(hostFile) : of(injected);
    }

    /**
     * The scope of the query holding an element of the SQL injected into a SQLX file.
     */
    public static @NotNull SqlScope of(@NotNull PsiElement injected) {
        PsiElement query = PsiTreeUtil.findFirstParent(injected, false,
                parent -> SqlPsiParts.isType(parent, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION));
        return new PsiSqlScope(injected.getProject(), query, hostStartOf(injected));
    }

    /**
     * The scope of the query producing the rows of the table a SQLX file builds.
     */
    public static @NotNull SqlScope mainQueryOf(@NotNull PsiFile hostFile) {
        PsiElement query = SqlxOutputColumnLocator.mainQuery(hostFile);
        return new PsiSqlScope(hostFile.getProject(), query, query == null ? 0 : hostStartOf(query));
    }

    private static int hostStartOf(@NotNull PsiElement injected) {
        return Math.max(0, InjectedLanguageManager.getInstance(injected.getProject()).injectedToHost(injected, 0));
    }

    @Override
    public @NotNull Map<String, List<String>> columnsBySource() {
        return columnsBySource;
    }

    @Override
    public @NotNull Collection<String> functions() {
        return BigQueryFunctionDocService.getInstance().getAll().stream().map(BigQueryFunctionDoc::name).toList();
    }

    @Override
    public @NotNull Collection<String> actionNames() {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        Stream.of(graph.getTables().stream().map(t -> t.getTarget()),
                        graph.getOperations().stream().map(o -> o.getTarget()),
                        graph.getDeclarations().stream().map(d -> d.getTarget()))
                .flatMap(targets -> targets)
                .filter(target -> target != null && target.getName() != null)
                .map(Target::getName)
                .forEach(names::add);
        return List.copyOf(names);
    }

    @Override
    public int groupByEnd() {
        if (query == null) return -1;
        PsiElement table = SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_TABLE_EXPRESSION);
        PsiElement groupBy = table == null
                ? null
                : SqlPsiParts.childOfType(table, SqlCompositeElementTypes.SQL_GROUP_BY_CLAUSE);
        if (groupBy == null) return -1;
        PsiElement last = PsiTreeUtil.getDeepestVisibleLast(groupBy);
        if (last == null || SqlxQuerySources.isGenerated(last)) return -1;
        return InjectedLanguageManager.getInstance(project)
                .injectedToHost(groupBy, groupBy.getTextRange().getEndOffset());
    }

    @Override
    public int sqlStart() {
        return sqlStart;
    }

    @Override
    public @NotNull List<SelectItem> selectItemsNamed(@NotNull String name) {
        if (query == null) return List.of();
        PsiElement clause = SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
        if (clause == null) return List.of();
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
        List<SelectItem> items = new ArrayList<>();
        for (PsiElement item : clause.getChildren()) {
            if (item.getNode() == null) continue;
            PsiElement output = SqlxOutputColumnLocator.outputNameOf(item);
            if (output == null || !SqlPsiParts.unquoted(output.getText()).equalsIgnoreCase(name)) continue;
            TextRange alias = SqlPsiParts.isType(item, SqlCompositeElementTypes.SQL_AS_EXPRESSION)
                    ? manager.injectedToHost(output, output.getTextRange())
                    : null;
            items.add(new SelectItem(manager.injectedToHost(item, item.getTextRange()), alias));
        }
        return items;
    }

    private static @NotNull Map<String, List<String>> sourcesOf(@NotNull PsiElement query) {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        PsiElement table = SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_TABLE_EXPRESSION);
        PsiElement from = table == null ? null : SqlPsiParts.childOfType(table, SqlCompositeElementTypes.SQL_FROM_CLAUSE);
        if (from == null) return sources;
        for (SqlReferenceExpression reference : PsiTreeUtil.findChildrenOfType(from, SqlReferenceExpression.class)) {
            if (reference.getReferenceElementType() != SqlCompositeElementTypes.SQL_TABLE_REFERENCE) continue;
            if (enclosingQuery(reference) != query) continue;
            String name = sourceName(reference);
            if (name != null) sources.put(name, columnsOf(reference.resolve()));
        }
        for (PsiElement alias : SqlPsiParts.childrenOfTypeDeep(from, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) {
            if (enclosingQuery(alias) != query) continue;
            PsiElement derived = SqlxOutputColumnLocator.rowProducingQuery(alias);
            PsiElement name = SqlPsiParts.lastIdentifier(alias);
            if (derived != null && name != null) {
                sources.put(SqlPsiParts.unquoted(name.getText()), outputNames(derived));
            }
        }
        return sources;
    }

    private static @Nullable PsiElement enclosingQuery(@NotNull PsiElement element) {
        return PsiTreeUtil.findFirstParent(element, true,
                parent -> SqlPsiParts.isType(parent, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION));
    }

    private static @Nullable String sourceName(@NotNull SqlReferenceExpression reference) {
        PsiElement parent = reference.getParent();
        if (SqlPsiParts.isType(parent, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) {
            PsiElement alias = SqlPsiParts.lastIdentifier(parent);
            if (alias != null && !PsiTreeUtil.isAncestor(reference, alias, false)) {
                return SqlPsiParts.unquoted(alias.getText());
            }
        }
        String name = reference.getName();
        return name == null ? null : SqlPsiParts.unquoted(name);
    }

    private static @NotNull List<String> columnsOf(@Nullable PsiElement target) {
        if (target instanceof DataformDasTable table) {
            return table.getColumns().stream().map(ColumnInfo::name).toList();
        }
        if (target != null && SqlPsiParts.isType(target, SqlCompositeElementTypes.SQL_NAMED_QUERY_DEFINITION)) {
            PsiElement query = SqlxOutputColumnLocator.rowProducingQuery(target);
            return query == null ? List.of() : outputNames(query);
        }
        return List.of();
    }

    private static @NotNull List<String> outputNames(@NotNull PsiElement query) {
        PsiElement clause = SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
        if (clause == null) return List.of();
        List<String> names = new ArrayList<>();
        for (PsiElement item : clause.getChildren()) {
            if (item.getNode() == null) continue;
            PsiElement output = SqlxOutputColumnLocator.outputNameOf(item);
            if (output != null && !SqlPsiParts.isStar(item)) names.add(SqlPsiParts.unquoted(output.getText()));
        }
        return names;
    }
}
