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

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link SqlxValidationService}, running every registered validator. Nothing is reported
 * while indexes are being built: the references the validators resolve need them, and a problem
 * reported then would be a false one.
 */
public final class SqlxValidationServiceImpl implements SqlxValidationService {

    private final List<SqlxValidator> validators = List.of(
            new UnresolvedReferenceValidator(),
            new ConfigBlockValidator());

    public SqlxValidationServiceImpl(@NotNull Project project) {
    }

    @Override
    public @NotNull List<SqlxValidationProblem> validate(@NotNull PsiFile file) {
        if (!(file instanceof SqlxFile)) {
            return List.of();
        }
        if (DumbService.isDumb(file.getProject())) {
            return List.of();
        }
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                run(file), PsiModificationTracker.MODIFICATION_COUNT,
                DumbService.getInstance(file.getProject()).getModificationTracker()));
    }

    private List<SqlxValidationProblem> run(@NotNull PsiFile file) {
        List<SqlxValidationProblem> problems = new ArrayList<>();
        for (SqlxValidator validator : validators) {
            problems.addAll(validator.validate(file));
        }
        return List.copyOf(problems);
    }
}
