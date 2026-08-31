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
        answer(StarResolution.ALIAS_IN_CURRENT_FILE);

        renameFinColumn("display_name");

        assertTrue("the file of the caret declares the new name\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("full_name AS display_name"));
        assertTrue("the file producing the column keeps its star",
                fileOf("mid").getText().contains("SELECT *"));
    }
}
