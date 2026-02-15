/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.service.WorkflowSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class DataformWorkflowSettingsCompletionContributor extends CompletionContributor {

    public DataformWorkflowSettingsCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {

                        PsiElement position = parameters.getPosition();

                        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject())
                                .getTopLevelFile(position);

                        if (!(topLevelFile instanceof SqlxFile) && !(topLevelFile instanceof JSFile)) {
                            return;
                        }

                        String prefix = extractPrefix(position);

                        WorkflowSettingsService service = WorkflowSettingsService.getInstance(position.getProject());

                        boolean shouldAddDot = prefix != null && !prefix.endsWith(".");
                        Collection<String> properties = service.getPropertiesForPrefix(prefix);

                        for (String property : properties) {
                            LookupElementBuilder element = LookupElementBuilder.create(property)
                                    .withTypeText("workflow_settings.yaml")
                                    .withIcon(com.intellij.icons.AllIcons.Nodes.Variable);
                            if (shouldAddDot) {
                                WorkflowSettingsService.WorkflowSettingsProperty prop = null;
                                if (prefix == null) {

                                    prop = service.getWorkflowProperties().get(property);
                                } else {

                                    String[] parts = prefix.split("\\.");
                                    prop = findNestedProperty(service, parts, property);
                                }

                                if (prop != null && prop.hasChildren()) {
                                    element = element.withInsertHandler((ctx, item) -> {
                                        Editor editor = ctx.getEditor();
                                        int offset = editor.getCaretModel().getOffset();
                                        editor.getDocument().insertString(offset, ".");
                                        editor.getCaretModel().moveToOffset(offset + 1);
                                        AutoPopupController.getInstance(ctx.getProject())
                                                .scheduleAutoPopup(editor);
                                    });
                                }
                            }

                            result.addElement(PrioritizedLookupElement.withPriority(element, Double.MAX_VALUE));
                        }
                    }
                });
    }

    private String extractPrefix(PsiElement position) {
        PsiElement prevLeaf = PsiTreeUtil.prevLeaf(position);
        List<String> ancestors = new LinkedList<>();
        while (prevLeaf != null && !prevLeaf.getText().trim().isEmpty() && !prevLeaf.getText().trim().equals("{")) {
            if (!prevLeaf.getText().equals(".")) {
                ancestors.addFirst(prevLeaf.getText());
            }
            prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf);
        }
        return ancestors.stream().collect(Collectors.joining("."));
    }


    private WorkflowSettingsService.WorkflowSettingsProperty findNestedProperty(
            WorkflowSettingsService service, String[] parentPath, String propertyName) {
        var current = service.getWorkflowProperties();

        for (String part : parentPath) {
            WorkflowSettingsService.WorkflowSettingsProperty prop = current.get(part);
            if (prop == null || !prop.hasChildren()) {
                return null;
            }
            current = prop.children();
        }

        return current.get(propertyName);
    }

}
