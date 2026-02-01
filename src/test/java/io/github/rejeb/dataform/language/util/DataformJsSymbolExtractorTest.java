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

import com.intellij.lang.javascript.JavaScriptFileType;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.util.DataformJsSymbolExtractor.JsSymbol;
import io.github.rejeb.dataform.language.util.DataformJsSymbolExtractor.SymbolType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataformJsSymbolExtractorTest extends BasePlatformTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    public void testExtractVariableFromJsFile() {
        PsiFile file = myFixture.configureByText(JavaScriptFileType.INSTANCE, """
                let myVariable = 10;
                const myConst = 20;
                var oldVar = 30;
                """);

        List<JsSymbol> symbols = DataformJsSymbolExtractor.extractSymbols(file);

        assertEquals(3, symbols.size());

        JsSymbol var1 = symbols.stream()
                .filter(s -> "myVariable".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(var1);
        assertEquals(SymbolType.VARIABLE, var1.type());

        JsSymbol const1 = symbols.stream()
                .filter(s -> "myConst".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(const1);
        assertEquals(SymbolType.CONST, const1.type());

        JsSymbol oldVar = symbols.stream()
                .filter(s -> "oldVar".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(oldVar);
        assertEquals(SymbolType.VARIABLE, oldVar.type());
    }

    public void testExtractFunctionFromJsFile() {
        PsiFile file = myFixture.configureByText(JavaScriptFileType.INSTANCE, """
                function myFunction() {
                    return 42;
                }

                function anotherFunction(param) {
                    return param * 2;
                }
                """);

        List<JsSymbol> symbols = DataformJsSymbolExtractor.extractSymbols(file);

        assertEquals(2, symbols.size());

        JsSymbol func1 = symbols.stream()
                .filter(s -> "myFunction".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(func1);
        assertEquals(SymbolType.FUNCTION, func1.type());

        JsSymbol func2 = symbols.stream()
                .filter(s -> "anotherFunction".equals(s.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(func2);
        assertEquals(SymbolType.FUNCTION, func2.type());
    }

    public void testExtractMixedSymbols() {
        PsiFile file = myFixture.configureByText(JavaScriptFileType.INSTANCE, """
                const API_KEY = "secret";
                let counter = 0;

                function increment() {
                    counter++;
                }

                function getApiKey() {
                    return API_KEY;
                }
                """);

        List<JsSymbol> symbols = DataformJsSymbolExtractor.extractSymbols(file);

        assertEquals(4, symbols.size());

        long constCount = symbols.stream()
                .filter(s -> s.type() == SymbolType.CONST)
                .count();
        assertEquals(1, constCount);

        long varCount = symbols.stream()
                .filter(s -> s.type() == SymbolType.VARIABLE)
                .count();
        assertEquals(1, varCount);

        long funcCount = symbols.stream()
                .filter(s -> s.type() == SymbolType.FUNCTION)
                .count();
        assertEquals(2, funcCount);
    }

    public void testExtractFromEmptyFile() {
        PsiFile file = myFixture.configureByText(JavaScriptFileType.INSTANCE, "");

        List<JsSymbol> symbols = DataformJsSymbolExtractor.extractSymbols(file);

        assertTrue(symbols.isEmpty());
    }

    public void testExtractFromNonJsFile() {
        PsiFile file = myFixture.configureByText("test.txt", "This is not a JS file");

        List<JsSymbol> symbols = DataformJsSymbolExtractor.extractSymbols(file);

        assertTrue(symbols.isEmpty());
    }

    public void testExtractNestedSymbols() {
        PsiFile file = myFixture.configureByText(JavaScriptFileType.INSTANCE, """
                function outer() {
                    let innerVar = 5;

                    function inner() {
                        return innerVar;
                    }

                    return inner();
                }
                """);

        List<JsSymbol> symbols = DataformJsSymbolExtractor.extractSymbols(file);

        assertTrue(symbols.size() >= 2);

        boolean hasOuter = symbols.stream()
                .anyMatch(s -> "outer".equals(s.name()));
        assertTrue(hasOuter);

        boolean hasInner = symbols.stream()
                .anyMatch(s -> "inner".equals(s.name()));
        assertTrue(hasInner);
    }
}
