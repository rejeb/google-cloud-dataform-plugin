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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.diagnostics.compile.CompilationProblemsService;
import io.github.rejeb.dataform.language.diagnostics.compile.CompiledGraphErrors;

import java.util.List;

public class CompilationProblemsServiceRawErrorsTest extends BasePlatformTestCase {

    public void testNoCompiledGraphYieldsNoDiagnostics() {
        PsiFile file = myFixture.configureByText("a.sqlx", "select 1\n");
        assertEmpty(CompilationProblemsService.getInstance(getProject())
                .getDiagnostics(file.getVirtualFile()));
    }

    public void testInvalidateIsSafeWithoutGraph() {
        CompilationProblemsService service =
                CompilationProblemsService.getInstance(getProject());
        service.invalidate();
        PsiFile file = myFixture.configureByText("b.sqlx", "select 1\n");
        assertEmpty(service.getDiagnostics(file.getVirtualFile()));
    }

    public void testDiagnosticsAreEmptyForFileOutsideDataformProject() {
        PsiFile file = myFixture.configureByText("notes.txt", "hello\n");
        assertEmpty(CompilationProblemsService.getInstance(getProject())
                .getDiagnostics(file.getVirtualFile()));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            CompiledGraphErrors.clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    private static final String INCLUDE_STACK = "ReferenceError: nope is not defined\n"
            + "    at Object.broken (/tmp/copy/includes/helpers.js:2:3)\n"
            + "    at Object.sqlContextable (/tmp/copy/definitions/uses_helper.sqlx:21:18)";

    public void testTheDiagnosticsOfAFileCarryTheirStackAndReportedFile() {
        PsiFile file = myFixture.addFileToProject("definitions/uses_helper.sqlx", "SELECT ${helpers.broken()} AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("uses_helper"), CompiledGraphErrors.error(
                "definitions/uses_helper.sqlx", "proj.ds.uses_helper", "nope is not defined", INCLUDE_STACK));

        CompilationDiagnostic diagnostic = CompilationProblemsService.getInstance(getProject())
                .getDiagnostics(file.getVirtualFile()).getFirst();

        assertEquals(INCLUDE_STACK, diagnostic.stack());
        assertEquals("definitions/uses_helper.sqlx", diagnostic.reportedFileName());
    }

    public void testAnErrorRaisedInAnIncludeIsFoundFromTheInclude() {
        PsiFile include = myFixture.addFileToProject("includes/helpers.js", "function broken() {\n  return nope + 1;\n}\n");
        myFixture.addFileToProject("definitions/uses_helper.sqlx", "SELECT ${helpers.broken()} AS c\n");
        CompiledGraphErrors.install(getProject(), List.of("uses_helper"), CompiledGraphErrors.error(
                "definitions/uses_helper.sqlx", "proj.ds.uses_helper", "nope is not defined", INCLUDE_STACK));

        List<CompilationDiagnostic> raised = CompilationProblemsService.getInstance(getProject())
                .getDiagnosticsRaisedIn(include.getVirtualFile());

        assertEquals(1, raised.size());
        assertEquals("definitions/uses_helper.sqlx", raised.getFirst().reportedFileName());
        assertEmpty(CompilationProblemsService.getInstance(getProject()).getDiagnostics(include.getVirtualFile()));
    }

    public void testInvalidatingCountsAsAChange() {
        CompilationProblemsService service = CompilationProblemsService.getInstance(getProject());
        long before = service.getModificationCount();

        service.invalidate();

        assertTrue(service.getModificationCount() > before);
    }

    public void testANewCompiledGraphCountsAsAChangeWithoutInvalidating() {
        CompilationProblemsService service = CompilationProblemsService.getInstance(getProject());
        DataformCompilationService compilation = DataformCompilationService.getInstance(getProject());
        long before = service.getModificationCount();
        DataformCompilationService.State restored = new DataformCompilationService.State();
        restored.compiledGraphJson = "{}";
        try {
            compilation.loadState(restored);

            assertTrue(service.getModificationCount() > before);
        } finally {
            compilation.loadState(new DataformCompilationService.State());
        }
    }
}
