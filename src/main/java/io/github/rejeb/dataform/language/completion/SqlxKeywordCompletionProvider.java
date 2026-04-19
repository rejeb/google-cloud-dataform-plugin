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
package io.github.rejeb.dataform.language.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sql.psi.SqlFile;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;

public class SqlxKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
    private static final String[] SQLX_KEYWORDS = {
            "config",
            "js",
            "pre_operations",
            "post_operations"
    };

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        PsiElement position = parameters.getPosition();
        PsiFile file = position.getContainingFile();
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(position.getProject()).getTopLevelFile(position);
        if (!(topLevelFile instanceof SqlxFile) || !(file instanceof SqlFile)) {
            return;
        }

        if (!isAtStartOfLine(position)) {
            return;
        }

        for (String keyword : SQLX_KEYWORDS) {
            LookupElementBuilder element = LookupElementBuilder.create(keyword)
                    .withTypeText("SQLX keyword")
                    .withBoldness(true)
                    .withInsertHandler((ctx, item) -> {
                        ctx.getDocument().insertString(ctx.getTailOffset(), " { \n }");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset());
                    });

            result.addElement(element);
        }
    }


    private boolean isAtStartOfLine(PsiElement element) {
        PsiElement prev = element.getPrevSibling();

        while (prev != null) {
            String text = prev.getText();

            if (text.contains("\n")) {
                return true;
            }

            if (!text.trim().isEmpty()) {
                return false;
            }

            prev = prev.getPrevSibling();
        }

        return true;
    }

}
