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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

public class DataformExpressionEvaluationServiceTest extends BasePlatformTestCase {

    public void testInvalidateDropsCachedValuesAndBumpsModificationCount() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", "SELECT 1");
        DataformExpressionEvaluationServiceImpl service = (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
        service.putCachedValue(file.getVirtualFile(), "ref(\"x\")", "p.d.x");
        long before = service.getModificationCount();

        service.invalidate(file.getVirtualFile());

        assertNull(service.getCachedValue(file.getVirtualFile(), "ref(\"x\")"));
        assertTrue(service.getModificationCount() > before);
    }

    public void testInvalidateAllDropsEveryCachedValue() {
        PsiFile file = myFixture.addFileToProject("definitions/other.sqlx", "SELECT 1");
        DataformExpressionEvaluationServiceImpl service = (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
        service.putCachedValue(file.getVirtualFile(), "ref(\"y\")", "p.d.y");

        service.invalidateAll();

        assertNull(service.getCachedValue(file.getVirtualFile(), "ref(\"y\")"));
    }
    private DataformExpressionEvaluationServiceImpl service() {
        return (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            service().resetEvaluator();
        } finally {
            super.tearDown();
        }
    }

    private PsiFile openSqlx(String body) {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n" + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    /** Records what each pass asks Node for, and answers every source with its own text. */
    private List<List<String>> recordEvaluations() {
        List<List<String>> passes = new ArrayList<>();
        service().setEvaluator((file, sources) -> {
            passes.add(List.copyOf(sources));
            List<DataformEvaluationResult> results = new ArrayList<>();
            for (String source : sources) {
                results.add(DataformEvaluationResult.resolved(source, "value of " + source));
            }
            return results;
        });
        return passes;
    }

    private void edit(PsiFile file) {
        WriteCommandAction.runWriteCommandAction(getProject(),
                () -> myFixture.getEditor().getDocument().insertString(
                        myFixture.getEditor().getDocument().getTextLength(), " -- edited"));
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    /** The value shown for a hole must not vanish under the person editing the file. */
    public void testAnEditKeepsTheCachedValues() {
        PsiFile file = openSqlx("SELECT ${helpers.a()} FROM t");
        service().putCachedValue(file.getVirtualFile(), "helpers.a()", "1");

        edit(file);

        assertEquals("1", service().getCachedValue(file.getVirtualFile(), "helpers.a()"));
    }

    /**
     * A pass is asked for when the file is opened or comes back into focus, and what it depends on
     * may have moved anywhere meanwhile, so every pass evaluates every expression again.
     */
    public void testEveryPassEvaluatesEveryExpressionEvenWhenTheFileDidNotChange() {
        PsiFile file = openSqlx("SELECT ${helpers.a()}, ${helpers.b()} FROM t");
        List<List<String>> passes = recordEvaluations();

        service().runPassNow(file.getVirtualFile());
        service().runPassNow(file.getVirtualFile());

        assertEquals(List.of(List.of("helpers.a()", "helpers.b()"),
                List.of("helpers.a()", "helpers.b()")), passes);
        assertEquals("value of helpers.a()",
                service().getCachedValue(file.getVirtualFile(), "helpers.a()"));
    }

    /**
     * A hole's text may stand still while what it depends on moved — a {@code js} block of the same
     * file — so a pass after an edit evaluates every hole again, not only the ones without a value.
     */
    public void testAPassAfterAnEditEvaluatesEveryExpressionAgain() {
        PsiFile file = openSqlx("SELECT ${helpers.a()}, ${helpers.b()} FROM t");
        List<List<String>> passes = recordEvaluations();
        service().runPassNow(file.getVirtualFile());

        edit(file);
        service().runPassNow(file.getVirtualFile());

        assertEquals(2, passes.size());
        assertEquals(List.of("helpers.a()", "helpers.b()"), passes.get(1));
    }

    /** An include changed under the file: the values stay in front of whoever is editing. */
    public void testAnEnvironmentChangeKeepsTheValues() {
        PsiFile file = openSqlx("SELECT ${helpers.a()} FROM t");
        recordEvaluations();
        service().runPassNow(file.getVirtualFile());

        service().markEnvironmentChanged();

        assertEquals("value of helpers.a()",
                service().getCachedValue(file.getVirtualFile(), "helpers.a()"));
    }
}
