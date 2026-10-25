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
import com.intellij.psi.PsiReference;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;

import java.util.List;

public class TestTargetReferenceTest extends ColumnRenameFixture {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        installProject(List.of(new Source("raw", List.of("id"))),
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
    }

    private PsiElement resolveAt(PsiFile file, String marker) {
        int offset = file.getText().indexOf(marker) + 1;
        PsiReference reference = file.findReferenceAt(offset);
        if (reference == null) {
            PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(file, offset);
            JSLiteralExpression literal = PsiTreeUtil.getParentOfType(injected, JSLiteralExpression.class, false);
            for (PsiReference candidate : literal == null ? PsiReference.EMPTY_ARRAY : literal.getReferences()) {
                if (candidate instanceof ActionFileReference) reference = candidate;
            }
        }
        return reference == null ? null : reference.resolve();
    }

    private static String nameOf(PsiElement element) {
        return element instanceof PsiFile file ? file.getName() : String.valueOf(element);
    }

    private PsiFile test(String config, String inputs) {
        return addTest("test_fin", "fin", config + "\n\n" + inputs + "\n\nSELECT 'a' AS full_name\n");
    }

    public void testTheDatasetStringLeadsToTheActionFile() {
        PsiFile test = test("config {\n  type: \"test\",\n  dataset: \"fin\"\n}", "");
        assertEquals("fin.sqlx", nameOf(resolveAt(test, "\"fin\"")));
    }

    public void testTheDatasetObjectNameLeadsToTheActionFile() {
        PsiFile test = test("config {\n  type: \"test\",\n  dataset: { schema: \"d\", name: \"fin\" }\n}", "");
        assertEquals("fin.sqlx", nameOf(resolveAt(test, "\"fin\"")));
    }

    public void testAnInputNameLeadsToTheActionFile() {
        PsiFile test = test("config {\n  type: \"test\",\n  dataset: \"fin\"\n}", "input \"src\" {\n  SELECT 'a' AS full_name\n}");
        assertEquals("src.sqlx", nameOf(resolveAt(test, "\"src\"")));
    }

    public void testAQualifiedInputNameLeadsToTheActionFile() {
        PsiFile test = test("config {\n  type: \"test\",\n  dataset: \"fin\"\n}", "input \"d\", \"src\" {\n  SELECT 'a' AS full_name\n}");
        assertEquals("src.sqlx", nameOf(resolveAt(test, "\"src\"")));
    }

    public void testAnInputOfADeclaredSourceLeadsToItsDeclaration() {
        PsiFile test = test("config {\n  type: \"test\",\n  dataset: \"fin\"\n}", "input \"raw\" {\n  SELECT 1 AS id\n}");
        assertEquals("sources.js", nameOf(resolveAt(test, "\"raw\"")));
    }

    public void testTheDatasetOfATableConfigIsNoReference() {
        PsiFile table = myFixture.addFileToProject("definitions/other.sqlx",
                "config {\n  type: \"table\",\n  dataset: \"fin\"\n}\n\nSELECT 1\n");
        assertNull(resolveAt(table, "\"fin\""));
    }

    public void testFindUsagesOfAnActionListsTheTestsNamingIt() {
        PsiFile test = test("config {\n  type: \"test\",\n  dataset: \"fin\"\n}", "input \"src\" {\n  SELECT 'a' AS full_name\n}");

        List<String> finUsages = ReferencesSearch.search(fileOf("fin"), GlobalSearchScope.projectScope(getProject()))
                .findAll().stream().map(r -> InjectedLanguageManager.getInstance(getProject())
                        .getTopLevelFile(r.getElement()).getName()).toList();
        List<String> srcUsages = ReferencesSearch.search(fileOf("src"), GlobalSearchScope.projectScope(getProject()))
                .findAll().stream().map(r -> InjectedLanguageManager.getInstance(getProject())
                        .getTopLevelFile(r.getElement()).getName()).toList();

        assertTrue("the tested dataset, got " + finUsages, finUsages.contains(test.getName()));
        assertTrue("the input mocking it, got " + srcUsages, srcUsages.contains(test.getName()));
        assertTrue("the ref() of fin is still found, got " + srcUsages, srcUsages.contains("fin.sqlx"));
    }
}
