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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class CompilationDiagnosticServiceImplTest extends BasePlatformTestCase {

    public void testNoCompiledGraphYieldsNoDiagnostics() {
        PsiFile file = myFixture.configureByText("a.sqlx", "select 1\n");
        assertEmpty(CompilationDiagnosticService.getInstance(getProject())
                .getDiagnostics(file.getVirtualFile()));
    }

    public void testInvalidateIsSafeWithoutGraph() {
        CompilationDiagnosticService service =
                CompilationDiagnosticService.getInstance(getProject());
        service.invalidate();
        PsiFile file = myFixture.configureByText("b.sqlx", "select 1\n");
        assertEmpty(service.getDiagnostics(file.getVirtualFile()));
    }

    public void testDiagnosticsAreEmptyForFileOutsideDataformProject() {
        PsiFile file = myFixture.configureByText("notes.txt", "hello\n");
        assertEmpty(CompilationDiagnosticService.getInstance(getProject())
                .getDiagnostics(file.getVirtualFile()));
    }
}
