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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Offers the evaluated value of the Dataform expression under the caret as quick documentation.
 */
public class DataformExpressionValueDocumentationTargetProvider implements DocumentationTargetProvider {

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        Project project = file.getProject();
        PsiFile hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
        VirtualFile virtualFile = hostFile == null ? null : hostFile.getVirtualFile();
        if (virtualFile == null) {
            return List.of();
        }

        DataformExpressionEvaluationService service = DataformExpressionEvaluationService.getInstance(project);
        int hostOffset = file == hostFile
                ? offset
                : InjectedLanguageManager.getInstance(project).injectedToHost(file, offset);

        for (DataformExpression expression : expressionsOf(file, hostFile, service)) {
            if (!expression.hostRange().containsOffset(hostOffset)) {
                continue;
            }
            String value = service.getCachedValue(virtualFile, expression.source());
            if (value != null) {
                return List.of(new DataformExpressionValueDocumentationTarget(expression.source(), value));
            }
        }
        return List.of();
    }

    @NotNull
    private List<DataformExpression> expressionsOf(@NotNull PsiFile file,
                                                   @NotNull PsiFile hostFile,
                                                   @NotNull DataformExpressionEvaluationService service) {
        List<DataformExpression> expressions =
                new ArrayList<>(DataformExpressionCollector.collectSqlxTemplates(hostFile));
        expressions.addAll(DataformExpressionCollector.collectJsTemplateSubstitutions(hostFile));
        addHostRanges(DataformExpressionCollector.collectIncludesReferenceElements(file, service.includeNames(hostFile.getVirtualFile())),
                file, expressions);
        return expressions;
    }

    private void addHostRanges(@NotNull List<DataformExpressionCollector.FoldablePart> parts,
                               @NotNull PsiFile file,
                               @NotNull List<DataformExpression> target) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
        parts.forEach(part -> target.add(new DataformExpression(
                part.expression().source(),
                part.expression().hostText(),
                manager.injectedToHost(part.element(), part.expression().hostRange()),
                part.expression().kind())));
    }
}
