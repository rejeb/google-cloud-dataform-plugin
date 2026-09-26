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
package io.github.rejeb.dataform.language.diagnostics.compile;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class PsiCompilationScopeTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            CompiledGraphErrors.clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    public void testTheNamesAFileCanReadAreItsDeclarationsItsIncludesAndDataformFunctions() {
        myFixture.addFileToProject("includes/helpers.js", "module.exports = {};\n");
        PsiFile file = myFixture.addFileToProject("definitions/scope.sqlx",
                "config { type: \"table\" }\n\njs {\n  const base = 1;\n  function twice(x) { return 2 * x; }\n}\n\nSELECT ${base} AS c\n");

        CompilationScope scope = PsiCompilationScope.at(file, 0);

        assertTrue(scope.jsNames().containsAll(List.of("base", "twice", "helpers", "ref", "self")));
    }

    public void testTheActionsAreThoseOfTheCompiledGraph() {
        PsiFile file = myFixture.addFileToProject("definitions/scope.sqlx", "SELECT 1\n");
        CompiledGraphErrors.install(getProject(), List.of("orders", "customers"));

        assertEquals(List.of("orders", "customers"), List.copyOf(PsiCompilationScope.at(file, 0).actionNames()));
    }

    public void testTheConfigKeysAreThoseOfTheObjectThePlaceIsIn() throws Exception {
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        String text = "config {\n  type: \"table\",\n  descripton: \"x\"\n}\n\nSELECT 1 AS c\n";
        PsiFile file = myFixture.addFileToProject("definitions/scope.sqlx", text);

        CompilationScope scope = PsiCompilationScope.at(file, text.indexOf("descripton"));

        assertTrue(scope.configKeys().toString(), scope.configKeys().contains("description"));
    }
}
