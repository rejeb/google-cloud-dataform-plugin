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
package io.github.rejeb.dataform.language.unittest.navigation;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.refactoring.column.ColumnRenameFixture;

import java.util.List;

public class TestAliasGotoDeclarationTest extends ColumnRenameFixture {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
        addTest("test_fin", "fin", "config {\n  type: \"test\",\n  dataset: \"fin\"\n}\n\n"
                + "input \"src\" {\n  SELECT 'a' AS full_name\n}\n\nSELECT 'a' AS full_name\n");
    }

    private PsiElement[] targetsAt(int occurrence) {
        PsiFile test = fileOf("test_fin");
        int offset = -1;
        for (int i = 0; i <= occurrence; i++) offset = test.getText().indexOf("full_name", offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(test, offset + 1);
        return new TestAliasGotoDeclarationHandler().getGotoDeclarationTargets(injected, offset + 1, null);
    }

    private static String fileNameOf(PsiElement element) {
        return InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element).getName();
    }

    public void testAnInputAliasLeadsToTheDeclarationOfItsTable() {
        PsiElement[] targets = targetsAt(0);

        assertNotNull(targets);
        assertEquals("src.sqlx", fileNameOf(targets[0]));
    }

    public void testAnExpectedAliasLeadsToTheDeclarationOfTheTestedDataset() {
        PsiElement[] targets = targetsAt(1);

        assertNotNull(targets);
        assertEquals("fin.sqlx", fileNameOf(targets[0]));
    }

    public void testOutsideATestNothingIsAnswered() {
        PsiFile src = fileOf("src");
        int offset = src.getText().indexOf("full_name");
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(src, offset + 1);

        assertNull(new TestAliasGotoDeclarationHandler().getGotoDeclarationTargets(injected, offset + 1, null));
    }
}
