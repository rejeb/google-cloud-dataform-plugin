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
package io.github.rejeb.dataform.language.util;

import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSVarStatement;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxJsBlock;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        for (PsiFile injected : InjectedFiles.inside(sqlxFile, SqlxJsBlock.class)) {
            if (injected instanceof JSFile jsFile) symbols.addAll(extractSymbols(jsFile));
        }
        return symbols;
    }

    /**
     * The element declaring a JavaScript symbol in the JS blocks of a SQLX file.
     *
     * @param file the SQLX file, any other file declaring nothing
     * @param name the name of the symbol
     * @return the declaring element, empty when the file declares no symbol of that name
     */
    @NotNull
    public static Optional<PsiElement> findSymbol(@NotNull PsiFile file, @NotNull String name) {
        return extractSymbolsFromSqlxFile(file).stream()
                .filter(symbol -> name.equals(symbol.name()))
                .map(JsSymbol::element)
                .findFirst();
    }
}
