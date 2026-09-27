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
package io.github.rejeb.dataform.language.refactoring.column;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarResolution;
import io.github.rejeb.dataform.language.refactoring.column.ui.StarResolutionChooser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ColumnRenameTestFilesTest extends ColumnRenameFixture {

    private static final String TEST_FIN = """
            config {
              type: "test",
              dataset: "fin"
            }

            input "src" {
              SELECT 'a' AS full_name
            }

            SELECT 'a' AS full_name
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
    }

    private void installChain() {
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
        addTest("test_fin", "fin", TEST_FIN);
    }

    private void renameAt(String file, String token, String newName) {
        PsiFile host = fileOf(file);
        myFixture.configureFromExistingVirtualFile(host.getVirtualFile());
        int offset = myFixture.getFile().getText().indexOf(token);
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 1);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    private String text(String file) {
        return fileOf(file).getViewProvider().getDocument() == null
                ? fileOf(file).getText()
                : com.intellij.psi.PsiDocumentManager.getInstance(getProject())
                .getDocument(fileOf(file)).getText();
    }

    public void testRenamingAColumnRenamesTheTestAliasesStandingForIt() {
        installChain();

        renameAt("fin", "full_name", "display_name");

        String test = text("test_fin");
        assertTrue("the expected output of the tested dataset\n" + test,
                test.contains("SELECT 'a' AS display_name\n") && test.endsWith("SELECT 'a' AS display_name\n"));
        assertTrue("the input of the upstream table\n" + test,
                test.contains("input \"src\" {\n  SELECT 'a' AS display_name\n}"));
    }

    public void testTheTestsOfAStarTableKeepTheOldNameWhenTheNewNameIsDeclaredAtItsReaders() {
        installProject(
                new Action("mid", "SELECT * FROM `p.d.unknown`", List.of(), List.of("full_name")),
                new Action("mid2", "SELECT full_name FROM `p.d.mid`", List.of("mid"), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.mid2`", List.of("mid2"), List.of("full_name")));
        addFile("mid", "config { type: \"table\" }\n\nSELECT *\nFROM ${ref(\"src\")}\n");
        addFile("mid2", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"mid\")}\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"mid2\")}\n");
        addTest("test_mid", "mid", "config {\n  type: \"test\",\n  dataset: \"mid\"\n}\n\nSELECT 'a' AS full_name\n");
        addTest("test_mid2", "mid2", "config {\n  type: \"test\",\n  dataset: \"mid2\"\n}\n\ninput \"mid\" {\n  SELECT 'a' AS full_name\n}\n\nSELECT 'a' AS full_name\n");
        ServiceContainerUtil.replaceService(getProject(), StarResolutionChooser.class,
                new StarResolutionChooser() {
                    @Override
                    public @NotNull StarResolution choose(@NotNull com.intellij.openapi.project.Project project,
                                                          @NotNull ColumnRenamePlan plan) {
                        return StarResolution.ALIAS_AT_READERS;
                    }
                }, getTestRootDisposable());

        renameAt("fin", "full_name", "display_name");

        assertTrue("the star table keeps the name\n" + text("test_mid"), text("test_mid").contains("AS full_name"));
        assertTrue("its input in the reader's test keeps it too\n" + text("test_mid2"),
                text("test_mid2").contains("input \"mid\" {\n  SELECT 'a' AS full_name\n}"));
        assertTrue("the reader's expected output takes the new name\n" + text("test_mid2"),
                text("test_mid2").endsWith("SELECT 'a' AS display_name\n"));
    }

    public void testARenameStartedOnATestInputAliasRenamesTheColumnItStandsFor() {
        installChain();

        renameAt("test_fin", "full_name", "display_name");

        assertTrue("the upstream declaration\n" + text("src"), text("src").contains("AS display_name"));
        assertTrue("the downstream read\n" + text("fin"), text("fin").contains("display_name"));
        assertTrue("both aliases of the test\n" + text("test_fin"),
                !text("test_fin").contains("full_name"));
    }

    public void testTheRenameHandlerIsAvailableOnATestAlias() {
        installChain();
        PsiFile test = fileOf("test_fin");
        myFixture.configureFromExistingVirtualFile(test.getVirtualFile());
        int offset = myFixture.getFile().getText().indexOf("full_name") + 1;

        assertTrue(io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory
                .at(myFixture.getFile(), offset).isPresent());
    }
}
