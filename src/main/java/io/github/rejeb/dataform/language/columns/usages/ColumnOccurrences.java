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
package io.github.rejeb.dataform.language.columns.usages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.ExecutableAction;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The places of a project a column occurs in, shared by the features that look for them: the column
 * window, Find Usages and the rename. Each feature decides what it does with an occurrence; how the
 * occurrences are found is written once.
 */
public final class ColumnOccurrences {

    private ColumnOccurrences() {
    }

    /**
     * Feeds every reference of the project to a column to {@code reads}, searching the candidate
     * files side by side, so the processor must be thread-safe. Runs under the read action of its caller.
     *
     * @param project the project
     * @param column  the schema column, or the alias declaring one
     * @param reads   the processor of the referencing elements, stopping the search when it answers {@code false}
     * @return whether the search ran to the end
     */
    public static boolean forEachReference(@NotNull Project project, @NotNull PsiElement column,
                                           @NotNull Processor<PsiElement> reads) {
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        return ReferencesSearch.search(new ReferencesSearch.SearchParameters(column, scope, false))
                .allowParallelProcessing()
                .forEach((PsiReference found) -> reads.process(found.getElement()));
    }

    /**
     * The files of the actions declaring or reading one of the given tables, which are the only ones
     * whose JavaScript can name a column of those tables: a column name is not unique in a project,
     * and the same string in an action of another chain names another column.
     *
     * @param project the project
     * @param tables  the full names of the tables
     * @return the files, each listed once, empty before the first compilation
     */
    public static @NotNull List<PsiFile> filesDeclaringOrReading(@NotNull Project project, @NotNull Set<String> tables) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (graph == null || tables.isEmpty()) return List.of();
        Set<PsiFile> files = new LinkedHashSet<>();
        Stream.of(graph.getTables(), graph.getOperations(), graph.getAssertions())
                .<ExecutableAction>flatMap(List::stream)
                .forEach(action -> addIf(project, files, action, tables));
        return new ArrayList<>(files);
    }

    private static void addIf(@NotNull Project project, @NotNull Set<PsiFile> files,
                              @NotNull ExecutableAction action, @NotNull Set<String> tables) {
        Target target = action.getTarget();
        boolean concerned = target != null && tables.contains(target.getFullName())
                || action.getDependencyTargets().stream().anyMatch(dependency -> dependency != null && tables.contains(dependency.getFullName()));
        if (!concerned) return;
        PsiFile file = DataformPaths.findPsiFileInProject(project, action.getFileName());
        if (file != null) files.add(file);
    }
}
