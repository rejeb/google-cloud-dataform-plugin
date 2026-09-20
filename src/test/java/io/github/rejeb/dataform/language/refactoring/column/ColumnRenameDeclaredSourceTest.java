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
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

import java.util.List;

/**
 * A source declared with {@code declare()} is a table of BigQuery the project does not build, so
 * its columns cannot be renamed. A rename reaching one stops there: the first action reading the
 * source keeps reading the old name and publishes the new one, as {@code old AS new}, wherever the
 * rename started.
 */
public class ColumnRenameDeclaredSourceTest extends ColumnRenameFixture {

    private static final String BRONZE = """
            config { type: "table" }

            SELECT
                event_id,
                event_name
            FROM ${ref("raw_events")}
            WHERE event_id IS NOT NULL
            """;

    private static final String SILVER = """
            config { type: "table" }

            SELECT
                event_id,
                event_name
            FROM ${ref("bronze_events")}
            """;

    private void installChainFromASource() {
        installProject(List.of(new Source("raw_events", List.of("event_id", "event_name"))),
                new Action("bronze_events",
                        "SELECT event_id, event_name FROM `p.d.raw_events` WHERE event_id IS NOT NULL",
                        List.of("raw_events"), List.of("event_id", "event_name")),
                new Action("silver_events",
                        "SELECT event_id, event_name FROM `p.d.bronze_events`",
                        List.of("bronze_events"), List.of("event_id", "event_name")));
        addFile("bronze_events", BRONZE);
        addFile("silver_events", SILVER);
    }

    private void renameSelectItem(String action, String newName) {
        PsiFile file = fileOf(action);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        int offset = file.getText().indexOf("    event_id");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 5);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 5);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    public void testTheFirstActionReadingTheSourceAliasesTheOldName() {
        installChainFromASource();

        renameSelectItem("bronze_events", "event_ref");

        String bronze = fileOf("bronze_events").getText();
        assertTrue("the action reads the source's name and publishes the new one\n" + bronze,
                bronze.contains("    event_id AS event_ref,"));
        assertTrue("another read of the source column keeps the old name\n" + bronze,
                bronze.contains("WHERE event_id IS NOT NULL"));
    }

    public void testTheSourceItselfIsNotTouched() {
        installChainFromASource();
        String before = fileOf("sources.js").getText();

        renameSelectItem("bronze_events", "event_ref");

        assertEquals("a declared source belongs to BigQuery, not to the rename",
                before, fileOf("sources.js").getText());
    }

    public void testTheActionsDownstreamAreRenamed() {
        installChainFromASource();

        renameSelectItem("bronze_events", "event_ref");

        String silver = fileOf("silver_events").getText();
        assertTrue("the reader of the renamed action reads the new name\n" + silver,
                silver.contains("    event_ref,"));
    }

    public void testRenamingFromDownstreamAliasesAtTheSourceBoundaryToo() {
        installChainFromASource();

        renameSelectItem("silver_events", "event_ref");

        String bronze = fileOf("bronze_events").getText();
        assertTrue("the rename walks up to the source and aliases there\n" + bronze,
                bronze.contains("    event_id AS event_ref,"));
        assertTrue("the read of the source column keeps its name\n" + bronze,
                bronze.contains("WHERE event_id IS NOT NULL"));
        assertTrue("the caret's own file is renamed\n" + fileOf("silver_events").getText(),
                fileOf("silver_events").getText().contains("    event_ref,"));
    }
}
