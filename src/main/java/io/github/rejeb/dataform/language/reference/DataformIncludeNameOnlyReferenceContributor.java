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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public class DataformIncludeNameOnlyReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {

        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(JSReferenceExpression.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NonNull [] getReferencesByElement(
                            @NotNull PsiElement element,
                            @NotNull ProcessingContext context
                    ) {
                        if (!(element instanceof JSReferenceExpression)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        JSReferenceExpression refExpr = (JSReferenceExpression) element;

                        if (refExpr.getQualifier() != null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        PsiElement prevLeaf = PsiTreeUtil.prevLeaf(element);
                        while (prevLeaf != null && prevLeaf.getText().trim().isEmpty()) {
                            prevLeaf = PsiTreeUtil.prevLeaf(prevLeaf);
                        }

                        if (prevLeaf != null && ".".equals(prevLeaf.getText())) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        String referencedName = refExpr.getReferenceName();
                        if (referencedName == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        Project project = element.getProject();
                        PsiFile topLevelFile = InjectedLanguageManager.getInstance(project)
                                .getTopLevelFile(element);

                        if (!(topLevelFile instanceof SqlxFile)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        Map<String, ?> exportsByFile = DataformJsFileIndex.getAllExports(project);

                        if (!exportsByFile.containsKey(referencedName)) {

                            return PsiReference.EMPTY_ARRAY;
                        }

                        return new PsiReference[]{
                                new DataformIncludeFileReference(element, referencedName)
                        };
                    }
                }
        );
    }
}
