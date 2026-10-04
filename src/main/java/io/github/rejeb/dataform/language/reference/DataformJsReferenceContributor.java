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
package io.github.rejeb.dataform.language.reference;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.index.DataformJsFileIndex;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.util.DataformJsSymbolExtractor;
import org.jetbrains.annotations.NotNull;

public class DataformJsReferenceContributor extends PsiReferenceContributor {

    private static final PsiElementPattern.Capture<JSReferenceExpression> JS_REFERENCE =
            PlatformPatterns.psiElement(JSReferenceExpression.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(JS_REFERENCE, new DataformBuiltinFunctionPsiReferenceProvider());
        registrar.registerReferenceProvider(JS_REFERENCE, new IncludeNameOnlyProvider());
        registrar.registerReferenceProvider(JS_REFERENCE, new DataformWorkflowSettingsReferenceProvider());
        registrar.registerReferenceProvider(JS_REFERENCE, new JsSymbolProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);
    }

    private static final class IncludeNameOnlyProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            JSReferenceExpression refExpr = (JSReferenceExpression) element;
            String referencedName = refExpr.getReferenceName();
            if (refExpr.getQualifier() != null || referencedName == null || isAfterDot(element)) {
                return PsiReference.EMPTY_ARRAY;
            }
            PsiFile topLevelFile = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
            if (!(topLevelFile instanceof SqlxFile)
                    || DataformJsSymbolExtractor.findSymbol(topLevelFile, referencedName).isPresent()
                    || !DataformJsFileIndex.getAllExports(element.getProject()).containsKey(referencedName)) {
                return PsiReference.EMPTY_ARRAY;
            }
            DataformIncludeFileReference ref = new DataformIncludeFileReference(element, referencedName);
            return ref.resolve() != null ? new PsiReference[]{ref} : PsiReference.EMPTY_ARRAY;
        }

        private static boolean isAfterDot(@NotNull PsiElement element) {
            PsiElement previous = PsiTreeUtil.prevVisibleLeaf(element);
            return previous != null && ".".equals(previous.getText());
        }
    }

    private static final class JsSymbolProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                               @NotNull ProcessingContext context) {
            DataformJsReference ref = new DataformJsReference(element, element.getText(),
                    element.getTextRange().shiftRight(-element.getTextOffset()));
            return ref.resolve() != null ? new PsiReference[]{ref} : PsiReference.EMPTY_ARRAY;
        }
    }
}
