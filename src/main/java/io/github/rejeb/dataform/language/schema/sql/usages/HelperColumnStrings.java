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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The columns a renamed expression hands to JavaScript as strings.
 *
 * <p>{@code ${helper("goals")} AS top_scorers} builds a column out of {@code goals}, but the SQL the
 * IDE analyses never says so: the template is injected as filler text, and the expression before
 * the alias reads no column at all. The strings inside the template do name what the column is
 * built from, so they are read here and matched against the columns of the tables the action
 * reads, the way the usage search matches them from the other end.</p>
 *
 * <p>Only a string that is exactly a column name of a table the action reads is taken. A string
 * merely containing one is SQL text a person has to read.</p>
 */
final class HelperColumnStrings {

    private HelperColumnStrings() {
    }

    /**
     * The schema columns named as strings by the templates sitting between the start of a renamed
     * expression and its alias, in the order the file writes them. Empty when the expression holds
     * no template, or when the file builds no action the compiled graph knows.
     */
    static @NotNull List<DataformDasColumn> columnsHandedTo(@NotNull PsiElement renamedExpression,
                                                            @NotNull PsiElement aliasIdentifier) {
        Project project = renamedExpression.getProject();
        InjectedLanguageManager injections = InjectedLanguageManager.getInstance(project);
        PsiFile hostFile = injections.getTopLevelFile(renamedExpression.getContainingFile());
        if (hostFile == null) return List.of();
        int from = injections.injectedToHost(renamedExpression,
                renamedExpression.getTextRange().getStartOffset());
        int to = injections.injectedToHost(aliasIdentifier,
                aliasIdentifier.getTextRange().getStartOffset());
        if (from < 0 || to <= from) return List.of();

        List<String> names = namesIn(templatesBetween(hostFile, from, to));
        if (names.isEmpty()) return List.of();
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        List<DataformDasColumn> columns = new ArrayList<>();
        for (String table : tablesReadBy(project, hostFile)) {
            for (String name : names) {
                DataformDasColumn column = origins.dasColumn(new ColumnRef(table, name));
                if (column != null) columns.add(column);
            }
        }
        return columns;
    }

    private static @NotNull List<SqlxJsLiteralExpression> templatesBetween(@NotNull PsiFile hostFile,
                                                                           int from, int to) {
        PsiElement at = hostFile.findElementAt(to);
        SqlxSqlBlock block = PsiTreeUtil.getParentOfType(at, SqlxSqlBlock.class, false);
        if (block == null) return List.of();
        List<SqlxJsLiteralExpression> templates = new ArrayList<>();
        TextRange window = new TextRange(from, to);
        for (SqlxJsLiteralExpression template :
                PsiTreeUtil.findChildrenOfType(block, SqlxJsLiteralExpression.class)) {
            if (window.contains(template.getTextRange())) templates.add(template);
        }
        return templates;
    }

    private static @NotNull List<String> namesIn(@NotNull List<SqlxJsLiteralExpression> templates) {
        Set<String> names = new LinkedHashSet<>();
        for (PsiFile javaScript : InjectedFiles.of(templates)) {
            for (JSLiteralExpression literal :
                    PsiTreeUtil.findChildrenOfType(javaScript, JSLiteralExpression.class)) {
                String value = literal.isQuotedLiteral() ? literal.getStringValue() : null;
                if (value != null && !value.isBlank()) names.add(value);
            }
        }
        return new ArrayList<>(names);
    }

    /** The full names of the tables the action built by a file reads, from the compiled graph. */
    private static @NotNull Set<String> tablesReadBy(@NotNull Project project,
                                                     @NotNull PsiFile hostFile) {
        VirtualFile file = hostFile.getVirtualFile();
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (file == null || graph == null) return Set.of();
        String path = file.getPath();
        Set<String> tables = new LinkedHashSet<>();
        graph.findTableByFileName(path).forEach(t -> add(tables, t.getDependencyTargets()));
        graph.findOperationByFileName(path).forEach(o -> add(tables, o.getDependencyTargets()));
        graph.findAssertionByFileName(path).forEach(a -> add(tables, a.getDependencyTargets()));
        return tables;
    }

    private static void add(@NotNull Set<String> tables, @NotNull List<Target> dependencies) {
        for (Target dependency : dependencies) {
            if (dependency != null && dependency.getFullName() != null) {
                tables.add(dependency.getFullName());
            }
        }
    }
}
