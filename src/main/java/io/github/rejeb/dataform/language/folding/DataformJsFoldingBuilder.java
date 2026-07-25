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

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformWorkflowSettingsValueResolver;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces workflow-settings references and Dataform template substitutions in JavaScript roots by
 * their value. Applies to real definition files as well as to the JavaScript injected in
 * {@code config} and {@code js} blocks.
 *
 * <p>Regions built for injected roots carry no folding group: the platform drops a grouped region
 * of an injected file when the folding pass of the host file runs, which would make the fold
 * disappear on the next daemon pass. Grouped regions are therefore only built for real definition
 * files. Files outside a Dataform project layout are skipped entirely, so ordinary JavaScript
 * projects never pay for the collection work.</p>
 */
public class DataformJsFoldingBuilder extends FoldingBuilderEx {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root,
                                                          @NotNull Document document,
                                                          boolean quick) {
        Project project = root.getProject();
        if (quick || DumbService.isDumb(project) || !DataformToolsSettings.getInstance().isFoldTemplateExpressions()) {
            return DataformFoldingPlaceholder.none();
        }
        PsiFile file = root.getContainingFile();
        if (file == null || isCoveredByHostRoot(project, file)) {
            return DataformFoldingPlaceholder.none();
        }

        boolean injected = InjectedLanguageManager.getInstance(project).isInjectedFragment(file);
        PsiFile hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(root);
        if (hostFile == null) {
            return DataformFoldingPlaceholder.none();
        }
        VirtualFile hostVirtualFile = hostFile.getVirtualFile();
        if (hostVirtualFile == null || !DataformProjectLayout.isInDataformProject(hostVirtualFile)) {
            return DataformFoldingPlaceholder.none();
        }

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        addWorkflowSettingsRegions(project, root, descriptors);
        addIncludesReferenceRegions(project, hostFile, root, descriptors, !injected);
        addTemplateSubstitutionRegions(project, hostFile, root, descriptors, !injected);
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return true;
    }

    private void addWorkflowSettingsRegions(@NotNull Project project,
                                            @NotNull PsiElement root,
                                            @NotNull List<FoldingDescriptor> descriptors) {
        DataformExpressionCollector.collectWorkflowSettingsReferenceElements(root)
                .forEach(part -> {
                    PsiElement element = part.element();
                    DataformExpression expression = part.expression();
                    String value = DataformWorkflowSettingsValueResolver.resolve(element, expression.source());
                    if (value == null || renderedOverMultipleLines(project, element, expression, value)) {
                        return;
                    }
                    addDescriptor(descriptors, element, expression, value, false);
                });
    }

    /**
     * Tells whether the multi-line renderer owns the expression, in which case no one-line placeholder
     * must be emitted for it. The decision is taken in host coordinates, as the renderer does.
     */
    private boolean renderedOverMultipleLines(@NotNull Project project,
                                              @NotNull PsiElement element,
                                              @NotNull DataformExpression expression,
                                              @NotNull String value) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
        PsiFile hostFile = manager.getTopLevelFile(element);
        if (hostFile == null) {
            return false;
        }
        Document hostDocument = PsiDocumentManager.getInstance(project).getDocument(hostFile);
        if (hostDocument == null) {
            return false;
        }
        TextRange hostRange = manager.isInjectedFragment(element.getContainingFile())
                ? manager.injectedToHost(element, expression.hostRange())
                : expression.hostRange();
        return DataformMultilineFoldPolicy.qualifies(hostDocument, hostRange, value);
    }

    private void addIncludesReferenceRegions(@NotNull Project project,
                                             @NotNull PsiFile hostFile,
                                             @NotNull PsiElement root,
                                             @NotNull List<FoldingDescriptor> descriptors,
                                             boolean grouped) {
        DataformExpressionEvaluationService service = DataformExpressionEvaluationService.getInstance(project);
        List<DataformExpressionCollector.FoldablePart> expressions =
                DataformExpressionCollector.collectIncludesReferenceElements(root, service.includeNames(hostFile.getVirtualFile()));
        addEvaluatedRegions(service, hostFile, expressions, descriptors, grouped);
    }

    private void addTemplateSubstitutionRegions(@NotNull Project project,
                                                @NotNull PsiFile hostFile,
                                                @NotNull PsiElement root,
                                                @NotNull List<FoldingDescriptor> descriptors,
                                                boolean grouped) {
        DataformExpressionEvaluationService service = DataformExpressionEvaluationService.getInstance(project);
        List<DataformExpressionCollector.FoldablePart> expressions =
                DataformExpressionCollector.collectJsTemplateSubstitutionElements(root);
        addEvaluatedRegions(service, hostFile, expressions, descriptors, grouped);
    }

    private void addEvaluatedRegions(@NotNull DataformExpressionEvaluationService service,
                                     @NotNull PsiFile hostFile,
                                     @NotNull List<DataformExpressionCollector.FoldablePart> expressions,
                                     @NotNull List<FoldingDescriptor> descriptors,
                                     boolean grouped) {
        VirtualFile virtualFile = hostFile.getVirtualFile();
        if (expressions.isEmpty() || virtualFile == null) {
            return;
        }
        service.requestEvaluation(hostFile);
        expressions.forEach(part -> {
            PsiElement element = part.element();
            DataformExpression expression = part.expression();
            String value = service.getCachedValue(virtualFile, expression.source());
            if (value != null && renderedOverMultipleLines(hostFile.getProject(), element, expression, value)) {
                return;
            }
            addDescriptor(descriptors, element, expression, value, grouped);
        });
    }

    private void addDescriptor(@NotNull List<FoldingDescriptor> descriptors,
                               @NotNull PsiElement element,
                               @NotNull DataformExpression expression,
                               @Nullable String value,
                               boolean grouped) {
        FoldingDescriptor descriptor = DataformFoldDescriptors.of(element, expression, value, grouped);
        if (descriptor != null) {
            descriptors.add(descriptor);
        }
    }

    private boolean isCoveredByHostRoot(@NotNull Project project, @NotNull PsiFile file) {
        PsiElement host = InjectedLanguageManager.getInstance(project).getInjectionHost(file);
        return host instanceof SqlxJsLiteralExpression || host instanceof JSStringTemplateExpression;
    }
}
