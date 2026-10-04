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
package io.github.rejeb.dataform.language.folding;

import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the {@code ${...}} template expressions of a SQLX file by their evaluated value.
 */
public class SqlxTemplateFoldingBuilder extends DataformValueFoldingBuilder {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root,
                                                          @NotNull Document document,
                                                          boolean quick) {
        Project project = root.getProject();
        if (isDisabled(project, quick)) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }
        PsiFile file = root.getContainingFile();
        VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
        if (virtualFile == null) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        List<DataformExpressionCollector.FoldablePart> expressions =
                DataformExpressionCollector.collectSqlxTemplateElements(root);
        if (expressions.isEmpty()) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        DataformExpressionEvaluationService service = DataformExpressionEvaluationService.getInstance(project);

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        expressions.forEach(part -> {
            PsiElement element = part.element();
            DataformExpression expression = part.expression();
            String value = service.getCachedValue(virtualFile, expression.source());
            if (value != null && DataformMultilineFoldPolicy.qualifies(document, expression.hostRange(), value)) {
                return;
            }
            addDescriptor(descriptors, element, expression, value, true);
        });
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }
}
