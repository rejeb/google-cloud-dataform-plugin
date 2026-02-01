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
package io.github.rejeb.dataform.language.util;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSVarStatement;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DataformJsSymbolExtractor {
    public record JsSymbol(
            String name,
            PsiElement element,
            SymbolType type
    ) {
    }

    public enum SymbolType {
        VARIABLE,
        FUNCTION,
        CONST
    }

    @NotNull
    public static List<JsSymbol> extractSymbols(@NotNull PsiFile file) {
        List<JsSymbol> symbols = new ArrayList<>();
        if (!(file instanceof JSFile)) {
            return symbols;
        }

        extractSymbolsRecursive(file, symbols);
        return symbols;
    }

    private static void extractSymbolsRecursive(PsiElement element, List<JsSymbol> symbols) {
        for (PsiElement child : element.getChildren()) {

            if (child instanceof JSVarStatement varStatement) {
                SymbolType type = getVariableType(varStatement);
                for (JSVariable variable : varStatement.getVariables()) {
                    String name = variable.getName();
                    if (name != null) {
                        symbols.add(new JsSymbol(name, variable, type));
                    }
                }
            } else if (child instanceof JSFunction function) {
                String name = function.getName();
                if (name != null) {
                    symbols.add(new JsSymbol(name, function, SymbolType.FUNCTION));
                }
            }
            extractSymbolsRecursive(child, symbols);
        }
    }

    private static SymbolType getVariableType(JSVarStatement varStatement) {
        String text = varStatement.getText();
        if (text.trim().startsWith("const ")) {
            return SymbolType.CONST;
        } else if (text.trim().startsWith("let ")) {
            return SymbolType.VARIABLE;
        } else {
            return SymbolType.VARIABLE;
        }
    }

    @NotNull
    public static List<JsSymbol> extractSymbolsFromSqlxFile(@NotNull PsiFile file) {
        List<JsSymbol> symbols = new ArrayList<>();

        if (!(file instanceof SqlxFile sqlxFile)) {
            return symbols;
        }
        extractSqlxJsBlocksRecursive(sqlxFile, symbols);
        return symbols;
    }

    private static void extractSqlxJsBlocksRecursive(PsiElement element, List<JsSymbol> symbols) {
        for (PsiElement child : element.getChildren()) {
            if (child instanceof SqlxJsBlock) {
                InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(child.getProject());
                List<Pair<PsiElement, TextRange>> injectedPsi = injectedManager.getInjectedPsiFiles(child);
                if (injectedPsi != null && !injectedPsi.isEmpty()) {
                    for (Pair<PsiElement, TextRange> pair : injectedPsi) {
                        PsiElement injectedElement = pair.getFirst();
                        PsiFile injectedFile = injectedElement.getContainingFile();
                        if (injectedFile instanceof JSFile jsFile) {
                            List<JsSymbol> jsSymbols = extractSymbols(jsFile);
                            symbols.addAll(jsSymbols);
                        }
                    }
                }
            }
            extractSqlxJsBlocksRecursive(child, symbols);
        }
    }
}