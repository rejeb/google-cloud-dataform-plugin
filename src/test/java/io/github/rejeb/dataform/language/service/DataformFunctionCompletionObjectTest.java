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
package io.github.rejeb.dataform.language.service;

import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Optional;

public class DataformFunctionCompletionObjectTest extends BasePlatformTestCase {

    public void testNamedFunctionCarriesItsSignature() {
        DataformFunctionCompletionObject object = objectOf("function fullName(first, last) { return first + last; }");
        assertEquals("fullName", object.name());
        assertTrue(object.signature(), object.signature().startsWith("(first, last)"));
        assertEquals("", object.description());
    }

    public void testJsDocIsCleanedIntoADescription() {
        DataformFunctionCompletionObject object = objectOf("""
                /**
                 * Builds the full name.
                 * @param first the first name
                 * @returns the joined name
                 */
                function fullName(first, last) { return first + last; }
                """);
        assertEquals("Builds the full name.", object.description());
    }

    public void testLineCommentIsUsedAsDescription() {
        DataformFunctionCompletionObject object = objectOf("""
                // Adds one.
                function inc(x) { return x + 1; }
                """);
        assertEquals("// Adds one.", object.description());
    }

    public void testOnlyTheCommentDirectlyAboveCounts() {
        DataformFunctionCompletionObject object = objectOf("""
                // Unrelated.
                const y = 1;
                function inc(x) { return x + 1; }
                """);
        assertEquals("", object.description());
    }

    public void testFunctionWithoutParametersHasEmptyParentheses() {
        assertTrue(objectOf("function now() { return 1; }").signature().startsWith("()"));
    }

    public void testTypedParametersShowTheirTypes() {
        PsiFile file = myFixture.configureByText("helpers.ts",
                "function pad(value: string, width?: number): string { return value; }");
        JSFunction function = PsiTreeUtil.findChildOfType(file, JSFunction.class);
        assertNotNull(function);
        DataformFunctionCompletionObject object =
                DataformFunctionCompletionObject.fromJSFunction(function).orElseThrow();
        assertEquals("(value: string, width: number?): string", object.signature());
    }

    public void testAnonymousFunctionIsSkipped() {
        PsiFile file = myFixture.configureByText("helpers.js", "run(function(a) { return a; });");
        JSFunction function = PsiTreeUtil.findChildOfType(file, JSFunction.class);
        assertNotNull(function);
        Optional<DataformFunctionCompletionObject> object = DataformFunctionCompletionObject.fromJSFunction(function);
        assertTrue(object.isEmpty());
    }

    private DataformFunctionCompletionObject objectOf(String source) {
        PsiFile file = myFixture.configureByText("helpers.js", source);
        JSFunction function = PsiTreeUtil.findChildOfType(file, JSFunction.class);
        assertNotNull(function);
        return DataformFunctionCompletionObject.fromJSFunction(function).orElseThrow();
    }
}
