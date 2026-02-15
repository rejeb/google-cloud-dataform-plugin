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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.util.DataformJsSymbolExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class DataformJsReference extends PsiReferenceBase<PsiElement> {

    private final String identifierName;

    public DataformJsReference(@NotNull PsiElement element,
                               @NotNull String identifierName,
                               @NotNull TextRange rangeInElement) {
        super(element, rangeInElement);
        this.identifierName = identifierName;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        Project project = myElement.getProject();

        if (DumbService.isDumb(project)) {
            return null;
        }

        PsiFile currentFile = myElement.getContainingFile();
        if (currentFile != null) {
            PsiFile contextFile = InjectedLanguageManager.getInstance(project)
                    .getTopLevelFile(myElement);

            if (contextFile instanceof SqlxFile) {
                List<DataformJsSymbolExtractor.JsSymbol> localSymbols =
                        DataformJsSymbolExtractor.extractSymbolsFromSqlxFile(contextFile);

                for (DataformJsSymbolExtractor.JsSymbol symbol : localSymbols) {
                    if (identifierName.equals(symbol.name())) {
                        return symbol.element();
                    }
                }
            }
        }

        return null;
    }

    @Override
    public Object @NonNull [] getVariants() {
        return new Object[0];
    }

}