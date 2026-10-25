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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.DryRunFailure;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists the failed dry-runs of the actions of a compiled graph with the place of each error in the
 * source file, for the Build tool window.
 */
public final class DryRunBuildProblems {

    private DryRunBuildProblems() {
    }

    /**
     * The failed dry-runs of the actions of the graph, leaving out the actions declared in the given
     * files, whose compilation errors are reported instead. Must be called inside a read action.
     *
     * @param project           the project the graph was compiled from
     * @param graph             the compiled graph the dry-runs were made from
     * @param excludedFileNames the project-relative files whose actions are left out
     * @return the problems, in the order of the actions in the graph
     */
    public static @NotNull List<DryRunBuildProblem> collect(@NotNull Project project,
                                                            @NotNull CompiledGraph graph,
                                                            @NotNull Set<String> excludedFileNames) {
        DryRunErrorRegistry registry = DryRunErrorRegistry.getInstance(project);
        List<DryRunBuildProblem> problems = new ArrayList<>();
        graph.getTables().forEach(t -> add(project, registry, t.getTarget(), t.getFileName(), excludedFileNames, problems));
        graph.getOperations().forEach(o -> add(project, registry, o.getTarget(), o.getFileName(), excludedFileNames, problems));
        graph.getDeclarations().forEach(d -> add(project, registry, d.getTarget(), d.getFileName(), excludedFileNames, problems));
        return problems;
    }

    private static void add(@NotNull Project project,
                            @NotNull DryRunErrorRegistry registry,
                            @Nullable Target target,
                            @Nullable String fileName,
                            @NotNull Set<String> excludedFileNames,
                            @NotNull List<DryRunBuildProblem> problems) {
        if (target == null || target.getFullName() == null) return;
        if (isExcluded(fileName, excludedFileNames)) return;
        DryRunFailure failure = registry.getFailure(target.getFullName());
        if (failure == null) return;
        BigQueryError error = BigQueryErrorParser.parse(failure.message());
        VirtualFile file = DataformPaths.findInProject(project, fileName);
        LineColumn position = positionOf(DataformPaths.findPsiFileInProject(project, fileName), failure, error);
        problems.add(new DryRunBuildProblem(target.getFullName(), file, position.line, position.column,
                error.message(), failure.message(), error.subject()));
    }

    private static boolean isExcluded(@Nullable String fileName, @NotNull Set<String> excludedFileNames) {
        if (fileName == null) return false;
        return excludedFileNames.stream()
                .anyMatch(f -> DataformPaths.pointsTo(fileName, f) || DataformPaths.pointsTo(f, fileName));
    }

    private static @NotNull LineColumn positionOf(@Nullable PsiFile psiFile,
                                                  @NotNull DryRunFailure failure,
                                                  @NotNull BigQueryError error) {
        if (!(psiFile instanceof SqlxFile)) return LineColumn.of(0, 0);
        BigQueryErrorPlacer.Placement placement = BigQueryErrorPlacer.place(psiFile, failure, error);
        if (placement.range() == null) return LineColumn.of(0, 0);
        return StringUtil.offsetToLineColumn(psiFile.getText(), placement.range().getStartOffset());
    }
}
