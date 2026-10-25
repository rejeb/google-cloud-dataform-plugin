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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.util.MappedText;

import java.util.List;
import java.util.Set;

import static io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryFailures.*;

public class DryRunBuildProblemsTest extends DataformProjectFixture {

    @Override
    protected void tearDown() throws Exception {
        try {
            clear(getProject());
        } finally {
            super.tearDown();
        }
    }

    private List<DryRunBuildProblem> collect(Set<String> excludedFileNames) {
        return ReadAction.nonBlocking(() -> DryRunBuildProblems.collect(getProject(),
                DataformCompilationService.getInstance(getProject()).getCompiledGraph(),
                excludedFileNames)).executeSynchronously();
    }

    public void testAFailedDryRunIsReportedOnTheLineAndColumnOfItsToken() {
        PsiFile file = myFixture.addFileToProject(PATH, SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd; Did you mean order_id?" + at(sent, "order_idd"), sent);

        List<DryRunBuildProblem> problems = collect(Set.of());

        assertEquals(1, problems.size());
        DryRunBuildProblem problem = problems.getFirst();
        assertEquals(ACTION, problem.actionFullName());
        assertEquals(file.getVirtualFile(), problem.file());
        assertEquals(3, problem.line());
        assertEquals(2, problem.column());
        assertEquals("Unrecognized name: order_idd", problem.message());
        assertTrue(problem.details().startsWith("Unrecognized name: order_idd; Did you mean order_id?"));
    }

    public void testAFailureThatCannotBePlacedStillNamesItsFile() {
        PsiFile file = myFixture.addFileToProject(PATH, SQLX);
        report(getProject(), "Access Denied: Project proj", sent(getProject(), COMPILED));

        DryRunBuildProblem problem = collect(Set.of()).getFirst();

        assertEquals(file.getVirtualFile(), problem.file());
        assertEquals(0, problem.line());
        assertEquals(0, problem.column());
    }

    public void testActionsOfFilesThatFailedToCompileAreLeftOut() {
        myFixture.addFileToProject(PATH, SQLX);
        MappedText sent = sent(getProject(), COMPILED);
        report(getProject(), "Unrecognized name: order_idd" + at(sent, "order_idd"), sent);

        assertEquals(List.of(), collect(Set.of(PATH)));
    }

    public void testNoFailureNoProblem() {
        myFixture.addFileToProject(PATH, SQLX);

        assertEquals(List.of(), collect(Set.of()));
    }
}
