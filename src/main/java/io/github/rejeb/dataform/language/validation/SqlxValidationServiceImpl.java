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
package io.github.rejeb.dataform.language.validation;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import io.github.rejeb.dataform.language.config.validation.ConfigBlockValidator;
import io.github.rejeb.dataform.language.diagnostics.compile.CompilationProblemsService;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryDiagnosticsService;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link SqlxValidationService}, running every registered validator. Nothing is reported
 * while indexes are being built: the references the validators resolve need them, and a problem
 * reported then would be a false one. The BigQuery errors of the last dry-runs are among the
 * problems, so they are recomputed when those errors or the extracted schemas change. Dataform
 * JavaScript files get their compilation errors only, and an error supersedes a weak warning at its
 * place.
 */
public final class SqlxValidationServiceImpl implements SqlxValidationService {

    private static final SqlxValidator COMPILATION_PROBLEMS =
            file -> CompilationProblemsService.getInstance(file.getProject()).diagnose(file).located();
    private static final SqlxValidator BIGQUERY_PROBLEMS =
            file -> BigQueryDiagnosticsService.getInstance(file.getProject()).diagnose(file).located();

    private final List<SqlxValidator> validators = List.of(
            new UnresolvedReferenceValidator(),
            new ConfigBlockValidator(),
            BIGQUERY_PROBLEMS,
            COMPILATION_PROBLEMS);
    private final List<SqlxValidator> scriptValidators = List.of(COMPILATION_PROBLEMS);

    @Override
    public @NotNull List<SqlxValidationProblem> validate(@NotNull PsiFile file) {
        if (!file.isValid()) {
            return List.of();
        }
        List<SqlxValidator> applicable = validatorsFor(file);
        if (applicable.isEmpty()) {
            return List.of();
        }
        if (DumbService.isDumb(file.getProject())) {
            return List.of();
        }
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                run(file, applicable), PsiModificationTracker.MODIFICATION_COUNT,
                DumbService.getInstance(file.getProject()).getModificationTracker(),
                DryRunErrorRegistry.getInstance(file.getProject()),
                DataformTableSchemaService.getInstance(file.getProject()),
                CompilationProblemsService.getInstance(file.getProject())));
    }

    private @NotNull List<SqlxValidator> validatorsFor(@NotNull PsiFile file) {
        if (file instanceof SqlxFile) {
            return validators;
        }
        return isDataformScript(file) ? scriptValidators : List.of();
    }

    private static boolean isDataformScript(@NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null || InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
            return false;
        }
        return DataformProjectLayout.isDataformScript(virtualFile);
    }

    private List<SqlxValidationProblem> run(@NotNull PsiFile file, @NotNull List<SqlxValidator> applicable) {
        List<SqlxValidationProblem> problems = new ArrayList<>();
        for (SqlxValidator validator : applicable) {
            problems.addAll(validator.validate(file));
        }
        return withoutSuperseded(problems);
    }

    private static @NotNull List<SqlxValidationProblem> withoutSuperseded(@NotNull List<SqlxValidationProblem> problems) {
        List<TextRange> errors = problems.stream()
                .filter(problem -> problem.severity() == SqlxValidationProblem.Severity.ERROR)
                .map(SqlxValidationProblem::range)
                .toList();
        return problems.stream()
                .filter(problem -> problem.severity() == SqlxValidationProblem.Severity.ERROR
                        || errors.stream().noneMatch(error -> error.intersectsStrict(problem.range())))
                .toList();
    }
}
