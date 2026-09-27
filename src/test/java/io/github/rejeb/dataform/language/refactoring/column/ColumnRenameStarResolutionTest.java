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
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;
import io.github.rejeb.dataform.language.evaluation.DataformTemplateSyntax;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarResolution;
import io.github.rejeb.dataform.language.refactoring.column.ui.StarResolutionChooser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A column produced by a star has no name to rewrite where it is built, so the user is asked what
 * to do with it and the answer decides what the rename writes.
 */
public class ColumnRenameStarResolutionTest extends ColumnRenameFixture {

    private static final String MID_TEXT = """
            config { type: "table" }

            SELECT *
            FROM ${ref("src")}
            """;

    private static final String FIN_TEXT = """
            config { type: "table" }

            SELECT
                full_name
            FROM ${ref("mid")}
            """;

    private void installChainWithAStar() {
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("mid", "SELECT * FROM `p.d.unknown`", List.of(), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.mid`", List.of("mid"),
                        List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("mid", MID_TEXT);
        addFile("fin", FIN_TEXT);
    }

    private void answer(StarResolution resolution) {
        ServiceContainerUtil.replaceService(getProject(), StarResolutionChooser.class,
                new StarResolutionChooser() {
                    @Override
                    public @NotNull StarResolution choose(@NotNull com.intellij.openapi.project.Project project,
                                                          @NotNull ColumnRenamePlan plan) {
                        return resolution;
                    }
                }, getTestRootDisposable());
    }

    private void renameFinColumn(String newName) {
        PsiFile fin = fileOf("fin");
        myFixture.configureFromExistingVirtualFile(fin.getVirtualFile());
        int offset = fin.getText().indexOf("full_name");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 1);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    public void testTheStarOfTheProducingActionIsReported() {
        installChainWithAStar();
        answer(StarResolution.CANCEL);

        renameFinColumn("display_name");

        assertTrue("cancelling writes nothing at all\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("full_name"));
        assertFalse("cancelling writes nothing at all",
                fileOf("fin").getText().contains("display_name"));
        assertTrue("the file producing the column is untouched",
                fileOf("mid").getText().contains("SELECT *"));
    }

    public void testExpandingTheStarGivesTheColumnADeclarationToRename() {
        installChainWithAStar();
        answer(StarResolution.EXPAND);

        renameFinColumn("display_name");

        assertTrue("the star becomes the column list of the action\n" + fileOf("mid").getText(),
                fileOf("mid").getText().contains("full_name AS display_name"));
        assertFalse("the star is gone", fileOf("mid").getText().contains("SELECT *"));
        assertTrue("the action reading it is renamed too\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("display_name"));
    }

    public void testDeclaringTheNewNameLocallyLeavesTheSourcesAlone() {
        installChainWithAStar();
        answer(StarResolution.ALIAS_AT_READERS);

        renameFinColumn("display_name");

        assertTrue("the file of the caret declares the new name\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("full_name AS display_name"));
        assertTrue("the file producing the column keeps its star",
                fileOf("mid").getText().contains("SELECT *"));
    }

    private static final String TEMPLATED_SRC_TEXT = """
            config { type: "table" }

            js {
                const rows = [[1, "a"], [2, "b"]];
            }

            SELECT
            *
            FROM UNNEST([
                ${rows.map(r => `STRUCT(${r[0]} AS customer_id, '${r[1]}' AS full_name)`).join(",\\n  ")}
            ])
            """;

    public void testAStarOverStructsWrittenByATemplateIsReportedOnceTheTemplateIsEvaluated() {
        assertStarOverTemplateIsReported(true);
    }

    public void testAStarOverStructsWrittenByATemplateIsReported() {
        assertStarOverTemplateIsReported(false);
    }

    private void assertStarOverTemplateIsReported(boolean evaluated) {
        installProject(
                new Action("src", "SELECT * FROM UNNEST([STRUCT(1 AS customer_id, 'a' AS full_name)])",
                        List.of(), List.of("customer_id", "full_name")),
                new Action("fin", "SELECT customer_id, full_name FROM `p.d.src`", List.of("src"),
                        List.of("customer_id", "full_name")));
        addFile("src", TEMPLATED_SRC_TEXT);
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    customer_id,\n    full_name\nFROM ${ref(\"src\")}\n");
        if (evaluated) {
            PsiFile src = fileOf("src");
            for (SqlxJsLiteralExpression hole : PsiTreeUtil.findChildrenOfType(src, SqlxJsLiteralExpression.class)) {
                ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService.getInstance(getProject()))
                        .putCachedValue(src.getVirtualFile(), DataformTemplateSyntax.sourceOf(hole.getText()),
                                "STRUCT(1 AS customer_id, 'a' AS full_name),\n  STRUCT(2 AS customer_id, 'b' AS full_name)");
            }
            PsiManager.getInstance(getProject()).dropPsiCaches();
        }
        myFixture.addFileToProject("definitions/tests/test_fin.sqlx",
                "config {\n  type: \"test\",\n  dataset: \"fin\"\n}\n\ninput \"src\" {\nSELECT\n    1 AS customer_id,\n    'a' AS full_name\n}\n\nSELECT\n    1 AS customer_id,\n    'a' AS full_name\n");
        boolean[] asked = {false};
        ServiceContainerUtil.replaceService(getProject(), StarResolutionChooser.class,
                new StarResolutionChooser() {
                    @Override
                    public @NotNull StarResolution choose(@NotNull com.intellij.openapi.project.Project project,
                                                          @NotNull ColumnRenamePlan plan) {
                        asked[0] = true;
                        return StarResolution.CANCEL;
                    }
                }, getTestRootDisposable());

        PsiFile fin = fileOf("fin");
        myFixture.configureFromExistingVirtualFile(fin.getVirtualFile());
        int offset = fin.getText().indexOf("customer_id");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 1);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), "client_id",
                        myFixture.getEditor(), injected));

        assertTrue("the star of src cannot be written, so the user must be asked\n"
                + fileOf("src").getText() + "\n" + fileOf("fin").getText(), asked[0]);
    }

    public void testTheAliasIsDeclaredWhereTheColumnIsReadFromTheStarNotInTheCaretFile() {
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("mid", "SELECT * FROM `p.d.unknown`", List.of(), List.of("full_name")),
                new Action("mid2", "SELECT full_name FROM `p.d.mid`", List.of("mid"), List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.mid2`", List.of("mid2"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("mid", MID_TEXT);
        addFile("mid2", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"mid\")}\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"mid2\")}\n");
        ColumnRenamePlan[] asked = {null};
        ServiceContainerUtil.replaceService(getProject(), StarResolutionChooser.class,
                new StarResolutionChooser() {
                    @Override
                    public @NotNull StarResolution choose(@NotNull com.intellij.openapi.project.Project project,
                                                          @NotNull ColumnRenamePlan plan) {
                        asked[0] = plan;
                        return StarResolution.ALIAS_AT_READERS;
                    }
                }, getTestRootDisposable());

        renameFinColumn("display_name");

        assertNotNull("the star must be reported", asked[0]);
        assertEquals("the alias is offered in the file reading the star",
                List.of("mid2.sqlx"), asked[0].starBoundaries().get(0).readerFiles().stream()
                        .map(com.intellij.openapi.vfs.VirtualFile::getName).toList());
        assertTrue("the reader of the star declares the new name\n" + fileOf("mid2").getText(),
                fileOf("mid2").getText().contains("full_name AS display_name"));
        assertTrue("the caret file reads the new name\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("display_name") && !fileOf("fin").getText().contains("full_name AS"));
        assertTrue("the star is kept", fileOf("mid").getText().contains("SELECT *"));
    }

    public void testUndoingAnExpandingRenameGivesTheReadsTheirColumnBack() {
        installChainWithAStar();
        answer(StarResolution.EXPAND);
        myFixture.configureFromExistingVirtualFile(fileOf("fin").getVirtualFile());
        String before = errorsOf(myFixture.doHighlighting());
        renameFinColumn("display_name");
        assertTrue(fileOf("mid").getText().contains("full_name AS display_name"));
        myFixture.configureFromExistingVirtualFile(fileOf("fin").getVirtualFile());
        myFixture.doHighlighting();
        com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK, getTestRootDisposable());

        com.intellij.openapi.command.undo.UndoManager.getInstance(getProject()).undo(
                com.intellij.openapi.fileEditor.TextEditor.class.cast(
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject())
                                .getSelectedEditor(fileOf("fin").getVirtualFile())));
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        PsiFile fin = fileOf("fin");
        assertTrue("the undo restores the files\n" + fin.getText(), fin.getText().contains("    full_name\n"));
        assertTrue("the star is back\n" + fileOf("mid").getText(), fileOf("mid").getText().contains("SELECT *"));
        java.util.List<String> midColumns = io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService
                .getInstance(getProject()).getAllTables().get("p.d.mid").getColumns().stream()
                .map(io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo::name).toList();
        assertEquals("the schema of mid names the column the file publishes again", java.util.List.of("full_name"), midColumns);
        int offset = fin.getText().indexOf("full_name");
        PsiElement injected = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(fin, offset + 1);
        PsiElement reference = io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret.referenceOf(injected);
        assertNotNull(reference);
        assertNotNull("the read of fin resolves again", reference.getReference().resolve());
        myFixture.configureFromExistingVirtualFile(fin.getVirtualFile());
        assertEquals("the editor shows what it showed before the rename", before,
                errorsOf(myFixture.doHighlighting()));
    }

    private static String errorsOf(java.util.List<com.intellij.codeInsight.daemon.impl.HighlightInfo> infos) {
        return infos.stream()
                .filter(info -> info.getSeverity().compareTo(com.intellij.lang.annotation.HighlightSeverity.WARNING) >= 0)
                .map(info -> info.getSeverity() + " " + info.getText() + " " + info.getDescription())
                .sorted().toList().toString();
    }
}
