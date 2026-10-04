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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionKind;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

/**
 * Offers the evaluated value of the Dataform expression under the caret as quick documentation.
 */
public class DataformExpressionValueDocumentationTargetProvider implements DocumentationTargetProvider {

    private static final Key<CachedValue<List<DataformExpression>>> HOST_EXPRESSIONS =
            Key.create("dataform.documentation.hostExpressions");

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

        for (DataformExpression expression : expressionsOf(hostFile, service)) {
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
    private static List<DataformExpression> expressionsOf(@NotNull PsiFile hostFile,
                                                          @NotNull DataformExpressionEvaluationService service) {
        return CachedValuesManager.getManager(hostFile.getProject()).getCachedValue(hostFile, HOST_EXPRESSIONS,
                () -> CachedValueProvider.Result.create(DataformExpressionCollector.inHostFile(hostFile,
                        service.includeNames(hostFile.getVirtualFile()), EnumSet.of(DataformExpressionKind.SQLX_TEMPLATE,
                                DataformExpressionKind.JS_TEMPLATE_SUBSTITUTION, DataformExpressionKind.INCLUDES_REFERENCE)),
                        hostFile, service, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS),
                false);
    }
}
