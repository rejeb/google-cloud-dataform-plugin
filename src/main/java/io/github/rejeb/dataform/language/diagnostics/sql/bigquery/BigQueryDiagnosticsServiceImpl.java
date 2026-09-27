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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.PsiSqlScope;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlErrorContext;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHint;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlHints;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlScope;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.DryRunFailure;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link BigQueryDiagnosticsService}. What it computes depends on the file, the failures
 * recorded and the schemas extracted, so it is cached against all three.
 */
public final class BigQueryDiagnosticsServiceImpl implements BigQueryDiagnosticsService {

    static final String PREFIX = "BigQuery: ";

    private final Project project;

    public BigQueryDiagnosticsServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull BigQueryDiagnostics diagnose(@NotNull PsiFile hostFile) {
        if (!(hostFile instanceof SqlxFile)) return BigQueryDiagnostics.NONE;
        return CachedValuesManager.getCachedValue(hostFile, () -> CachedValueProvider.Result.create(
                compute(hostFile),
                PsiModificationTracker.MODIFICATION_COUNT,
                DryRunErrorRegistry.getInstance(project),
                DataformTableSchemaService.getInstance(project)));
    }

    private @NotNull BigQueryDiagnostics compute(@NotNull PsiFile hostFile) {
        VirtualFile file = hostFile.getOriginalFile().getVirtualFile();
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (file == null || graph == null) return BigQueryDiagnostics.NONE;
        DryRunErrorRegistry registry = DryRunErrorRegistry.getInstance(project);
        List<SqlxValidationProblem> located = new ArrayList<>();
        List<String> unlocated = new ArrayList<>();
        for (CompiledTable table : graph.findTableByFileName(file.getPath())) {
            Target target = table.getTarget();
            DryRunFailure failure = target == null || target.getFullName() == null
                    ? null
                    : registry.getFailure(target.getFullName());
            if (failure == null) continue;
            BigQueryError error = BigQueryErrorParser.parse(failure.message());
            BigQueryErrorPlacer.Placement placement = BigQueryErrorPlacer.place(hostFile, failure, error);
            switch (placement.outcome()) {
                case LOCATED -> located.add(problemOf(hostFile, error, placement.range()));
                case UNLOCATED -> unlocated.add(PREFIX + error.message());
                case STALE -> {
                }
            }
        }
        if (located.isEmpty() && unlocated.isEmpty()) return BigQueryDiagnostics.NONE;
        return new BigQueryDiagnostics(List.copyOf(located), List.copyOf(unlocated));
    }

    private static @NotNull SqlxValidationProblem problemOf(@NotNull PsiFile hostFile,
                                                            @NotNull BigQueryError error,
                                                            @NotNull TextRange range) {
        SqlScope scope = error.kind() == BigQueryErrorKind.DUPLICATE_COLUMN
                ? PsiSqlScope.mainQueryOf(hostFile)
                : PsiSqlScope.at(hostFile, range.getStartOffset());
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(error, range, hostFile.getText(), scope));
        return SqlxValidationProblem.bigQueryError(range, PREFIX + error.message(), hint.text(), hint.fixes());
    }
}
