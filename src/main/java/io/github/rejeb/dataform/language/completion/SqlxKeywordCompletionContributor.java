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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sql.psi.SqlFile;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;

public class SqlxKeywordCompletionContributor extends CompletionContributor {

    private static final String[] SQLX_KEYWORDS = {
            "config",
            "js",
            "pre_operations",
            "post_operations"
    };

    public SqlxKeywordCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {

                        PsiElement position = parameters.getPosition();
                        PsiFile file = position.getContainingFile();

                        if (!(file instanceof SqlxFile)&&!(file instanceof SqlFile)) {
                            return;
                        }

                        // Check if we're at the start of a line (potentially after whitespace)
                        if (!isAtStartOfLine(position)) {
                            return;
                        }

                        // Add SQLX keywords to completion
                        for (String keyword : SQLX_KEYWORDS) {
                            LookupElementBuilder element = LookupElementBuilder.create(keyword)
                                    .withTypeText("SQLX keyword")
                                    .withBoldness(true)
                                    .withInsertHandler((ctx, item) -> {
                                        // Add opening brace after keyword
                                        ctx.getDocument().insertString(ctx.getTailOffset(), " {");
                                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                                    });

                            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0));
                        }
                    }
                });
    }

    private boolean isAtStartOfLine(PsiElement element) {
        PsiElement prev = element.getPrevSibling();

        // Walk backwards through siblings
        while (prev != null) {
            String text = prev.getText();

            // If we find a newline, we're at the start of a line
            if (text.contains("\n")) {
                return true;
            }

            // If we find non-whitespace content, we're not at the start
            if (!text.trim().isEmpty()) {
                return false;
            }

            prev = prev.getPrevSibling();
        }

        // If no previous siblings, we're at the start of the file
        return true;
    }
}
