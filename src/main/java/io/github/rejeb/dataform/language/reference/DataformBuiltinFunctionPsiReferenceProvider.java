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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import io.github.rejeb.dataform.language.util.DataformJsSymbolExtractor;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

public class DataformBuiltinFunctionPsiReferenceProvider extends PsiReferenceProvider {

    @NotNull
    @Override
    public PsiReference @NonNull [] getReferencesByElement(
            @NotNull PsiElement element,
            @NotNull ProcessingContext context
    ) {

        String referencedName = getReferenceName(element).orElse(null);

        if (referencedName == null) {
            return PsiReference.EMPTY_ARRAY;
        }
        Project project = element.getProject();
        PsiFile topLevelFile = InjectedLanguageManager.getInstance(project)
                .getTopLevelFile(element);
        List<DataformJsSymbolExtractor.JsSymbol> localSymbols =
                DataformJsSymbolExtractor.extractSymbolsFromSqlxFile(topLevelFile);
        for (DataformJsSymbolExtractor.JsSymbol symbol : localSymbols) {
            if (referencedName.equals(symbol.name())) {
                return PsiReference.EMPTY_ARRAY;
            }
        }

        DataformBuiltinFunctionReference ref = new DataformBuiltinFunctionReference(element, referencedName);
        if (ref.resolve() != null) {
            return new PsiReference[]{
                    ref
            };
        } else {
            return PsiReference.EMPTY_ARRAY;
        }

    }

    private Optional<String> getReferenceName(PsiElement element) {
        return switch (element) {
            case JSReferenceExpression refExpr -> getJsReferenceName(refExpr);
            default -> Optional.empty();
        };
    }

    private Optional<String> getJsReferenceName(JSReferenceExpression refExpr) {
        return Optional
                .ofNullable(refExpr.getReferenceNameElement())
                .map(PsiElement::getText);
    }

}
