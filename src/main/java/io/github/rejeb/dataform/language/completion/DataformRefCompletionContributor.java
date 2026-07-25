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

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.reference.DataformRefFunctionReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformRefCompletionContributor extends CompletionContributor {

    public DataformRefCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new Provider());
    }

    private static final class Provider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            JSLiteralExpression literal = refLiteral(parameters.getPosition());
            if (literal == null) {
                return;
            }
            DataformRefFunctionReference reference =
                    new DataformRefFunctionReference(literal, "", literal.getTextRange());
            for (Object variant : reference.getVariants()) {
                if (variant instanceof com.intellij.codeInsight.lookup.LookupElement lookupElement) {
                    result.addElement(lookupElement);
                }
            }
            result.stopHere();
        }

        @Nullable
        private static JSLiteralExpression refLiteral(@NotNull PsiElement position) {
            JSLiteralExpression literal =
                    PsiTreeUtil.getParentOfType(position, JSLiteralExpression.class, false);
            if (literal == null || !literal.isQuotedLiteral()) {
                return null;
            }
            JSCallExpression call = PsiTreeUtil.getParentOfType(literal, JSCallExpression.class);
            if (call == null) {
                return null;
            }
            JSExpression method = call.getMethodExpression();
            if (!(method instanceof JSReferenceExpression reference)) {
                return null;
            }
            String name = reference.getReferenceName();
            return "ref".equals(name) || "resolve".equals(name) ? literal : null;
        }
    }
}
