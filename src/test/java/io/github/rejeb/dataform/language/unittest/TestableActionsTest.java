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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;

import java.util.List;

public class TestableActionsTest extends BasePlatformTestCase {

    private CompiledGraph graph() {
        return DataformCompilationService.getInstance(getProject()).getCompiledGraph();
    }

    private List<String> testableIn(String path) {
        PsiFile file = myFixture.addFileToProject(path, "");
        return TestableActions.in(graph(), file.getVirtualFile()).stream()
                .map(table -> table.getTarget().getName()).toList();
    }

    public void testTablesAndViewsAreTestable() {
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        assertEquals(List.of("orders"), testableIn("definitions/orders.sqlx"));
        assertEquals(List.of("customers"), testableIn("definitions/customers.sqlx"));
    }

    public void testIncrementalTablesOperationsAndDeclarationsAreNot() {
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        assertEquals(List.of(), testableIn("definitions/events.sqlx"));
        assertEquals(List.of(), testableIn("definitions/cleanup.sqlx"));
        assertEquals(List.of(), testableIn("definitions/sources.js"));
    }

    public void testAJsFileKeepsItsTestableActionsInGraphOrder() {
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
        assertEquals(List.of("js_table", "js_view"), testableIn("definitions/multi.js"));
    }

    public void testWindowsFileNamesAreMatched() {
        UnitTestGraphFixture.installWithBackslashes(getProject(), getTestRootDisposable());
        assertEquals(List.of("js_table", "js_view"), testableIn("definitions/multi.js"));
    }

    public void testIsTestableRejectsNull() {
        assertFalse(TestableActions.isTestable((CompiledTable) null));
    }
}
