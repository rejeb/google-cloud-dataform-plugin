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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.util.DataformJsSymbolExtractor;
import org.jetbrains.annotations.NotNull;

public class DataformBuiltinFunctionPsiReferenceProvider extends PsiReferenceProvider {

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                           @NotNull ProcessingContext context) {
        String referencedName = element instanceof JSReferenceExpression refExpr
                && refExpr.getReferenceNameElement() != null ? refExpr.getReferenceNameElement().getText() : null;
        if (referencedName == null) {
            return PsiReference.EMPTY_ARRAY;
        }
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
        if (DataformJsSymbolExtractor.findSymbol(topLevelFile, referencedName).isPresent()) {
            return PsiReference.EMPTY_ARRAY;
        }
        DataformBuiltinFunctionReference ref = new DataformBuiltinFunctionReference(element, referencedName);
        return ref.resolve() != null ? new PsiReference[]{ref} : PsiReference.EMPTY_ARRAY;
    }
}
