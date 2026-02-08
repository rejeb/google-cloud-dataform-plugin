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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;

public class SafeJsonSchemaCompletionContributor extends CompletionContributor {

    public SafeJsonSchemaCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                    }
                });
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject())
                .getTopLevelFile(position);

        if (!(topLevelFile instanceof SqlxFile) || parameters.getOriginalFile() == topLevelFile) {
            super.fillCompletionVariants(parameters, result);
            return;
        }

        result.runRemainingContributors(parameters, completionResult -> {
            LookupElement element = completionResult.getLookupElement();
            
            // Wrapper l'InsertHandler pour gérer les documents injectés
            LookupElement safeElement = LookupElementDecorator.withInsertHandler(
                    element,
                    new SafeInsertHandler()
            );
            
            result.passResult(completionResult.withLookupElement(safeElement));
        });

        super.fillCompletionVariants(parameters, result);
    }

    private static class SafeInsertHandler implements InsertHandler<LookupElement> {

        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
            try {
                Editor editor = context.getEditor();
                Document document = editor.getDocument();
                
                if (!document.isWritable()) {
                    return;
                }

                int startOffset = context.getStartOffset();
                int tailOffset = context.getTailOffset();
                int docLength = document.getTextLength();

                if (startOffset < 0 || startOffset > docLength || tailOffset < 0 || tailOffset > docLength) {
                    return;
                }

                String textToInsert = item.getLookupString();
                document.replaceString(startOffset, tailOffset, textToInsert);
                editor.getCaretModel().moveToOffset(startOffset + textToInsert.length());

            } catch (IllegalStateException | IndexOutOfBoundsException e) {
                try {
                    Editor editor = context.getEditor();
                    Document document = editor.getDocument();
                    int offset = editor.getCaretModel().getOffset();
                    document.insertString(offset, item.getLookupString());
                    editor.getCaretModel().moveToOffset(offset + item.getLookupString().length());
                } catch (Exception ignored) {
                }
            }
        }
    }
}
