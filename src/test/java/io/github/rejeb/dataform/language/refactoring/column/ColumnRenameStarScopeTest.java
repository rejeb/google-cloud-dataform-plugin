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
import com.intellij.openapi.project.Project;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Where a star has to be expanded, and what expanding it may touch.
 *
 * <p>A star reproduces whatever its source names, so it only has to be expanded where the new name
 * has to start existing — never in an action that merely carries the column further. And expanding
 * writes {@code old AS new}, which means the source of that very query has to keep the old name.</p>
 */
public class ColumnRenameStarScopeTest extends ColumnRenameFixture {

    private final AtomicBoolean asked = new AtomicBoolean(false);

    private void answer(StarResolution resolution) {
        ServiceContainerUtil.replaceService(getProject(), StarResolutionChooser.class,
                new StarResolutionChooser() {
                    @Override
                    public @NotNull StarResolution choose(@NotNull Project project,
                                                          @NotNull ColumnRenamePlan plan) {
                        asked.set(true);
                        return resolution;
                    }
                }, getTestRootDisposable());
    }

    private void renameColumnOf(String action, String occurrence, String newName) {
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                startRenameOf(action, occurrence, newName));
    }

    /**
     * The same rename, for a plan the user has to review: a place found by matching text puts the
     * window up, and the window applies every place of the plan.
     */
    private void renameReviewedColumnOf(String action, String occurrence, String newName) {
        startRenameOf(action, occurrence, newName);
    }

    private void startRenameOf(String action, String occurrence, String newName) {
        PsiFile file = fileOf(action);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        int offset = file.getText().indexOf(occurrence) + occurrence.indexOf("full_name");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 1);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                myFixture.getEditor(), injected);
    }

    private static final String SOURCE_BUILT_IN_JS = """
            config {
                type: "table",
                columns: {
                    full_name: "Raw name"
                }
            }

            js {
                const rows = [["Alice"], ["Bram"]];
            }

            SELECT
            *
            FROM UNNEST([
                ${rows.map(r => `STRUCT('${r[0]}' AS full_name)`).join(", ")}
            ])
            """;

    /**
     * The action reading a column keeps its own star: the column it publishes is the one the action
     * above it publishes, and that one is already being renamed.
     */
    public void testTheStarOfAReadingActionIsLeftAlone() {
        installProject(
                new Action("src", "SELECT 'a' AS full_name", List.of(), List.of("full_name")),
                new Action("mid", "SELECT full_name FROM `p.d.src`", List.of("src"),
                        List.of("full_name")),
                new Action("fin", "SELECT * FROM (SELECT full_name FROM `p.d.mid`)",
                        List.of("mid"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("mid", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT *\nFROM (SELECT full_name FROM ${ref(\"mid\")})\n");
        answer(StarResolution.EXPAND);

        renameColumnOf("mid", "    full_name", "display_name");

        assertTrue("the action producing the column is renamed\n" + fileOf("mid").getText(),
                fileOf("mid").getText().contains("display_name"));
        assertTrue("the action reading it keeps its star\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("SELECT *"));
        assertTrue("what it reads of the renamed action is named anew\n" + fileOf("fin").getText(),
                fileOf("fin").getText().contains("SELECT display_name FROM"));
        assertFalse("a star that carries the rename is not a decision to make",
                asked.get());
    }

    /**
     * Expanding a star writes {@code old AS new}, so what the query reads has to go on being called
     * by its old name — including the SQL a js block of the same file builds.
     */
    public void testExpandingAStarLeavesWhatTheQueryReadsUnderItsOldName() {
        installProject(
                new Action("src", "SELECT * FROM UNNEST([STRUCT('a' AS full_name)])", List.of(),
                        List.of("full_name")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"),
                        List.of("full_name")));
        addFile("src", SOURCE_BUILT_IN_JS);
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
        answer(StarResolution.EXPAND);

        renameReviewedColumnOf("fin", "    full_name", "display_name");

        String source = fileOf("src").getText();
        assertTrue("the star becomes the column list of the action\n" + source,
                source.contains("full_name AS display_name"));
        assertTrue("what the query reads keeps the name it is read under\n" + source,
                source.contains("AS full_name)"));
        assertFalse("the source of the query is not renamed\n" + source,
                source.contains("AS display_name)"));
    }
}
