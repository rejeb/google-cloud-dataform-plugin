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
package io.github.rejeb.dataform.language.reference;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class DataformRefReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(JSLiteralExpression.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NonNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        if (!(element instanceof JSLiteralExpression literal)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        if (!literal.isQuotedLiteral()) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        JSCallExpression callExpr = getJSCallExpression(literal);
                        if (callExpr == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        JSExpression methodExpr = callExpr.getMethodExpression();

                        if (!(methodExpr instanceof JSReferenceExpression)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        String functionName = ((JSReferenceExpression) methodExpr).getReferenceName();

                        if (!"ref".equals(functionName) && !"resolve".equals(functionName)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        String tableName = String.valueOf(literal.getValue());
                        TextRange range = new TextRange(1, element.getTextLength() - 1);

                        return new PsiReference[]{
                                new DataformRefFunctionReference(element, tableName, range)
                        };
                    }
                }
        );
    }

    private JSCallExpression getJSCallExpression(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent != null && !(parent instanceof JSCallExpression)) {
            return getJSCallExpression(parent);
        }
        return (JSCallExpression) parent;
    }
}

