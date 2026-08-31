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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.List;

/**
 * What the editor resolves a column against is the schema of the last compilation, and compiling a
 * Dataform project takes long enough that a column just renamed would be painted as unknown for the
 * whole run. The rename knows what it wrote, so the schemas follow it at once.
 */
public class ColumnRenameSchemaRefreshTest extends ColumnRenameFixture {

    private void installChain() {
        installProject(
                new Action("src", "SELECT 'a' AS full_name, 1 AS k", List.of(),
                        List.of("full_name", "k")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"),
                        List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name, 1 AS k\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
    }

    private void renameFinColumn(String newName) {
        PsiFile fin = fileOf("fin");
        myFixture.configureFromExistingVirtualFile(fin.getVirtualFile());
        int offset = fin.getText().indexOf("    full_name") + 4;
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 1);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    private List<String> columnNamesOf(String table) {
        DataformDasTable dasTable =
                DataformTableSchemaService.getInstance(getProject()).getAllTables().get(table);
        assertNotNull("the schema of " + table + " is known", dasTable);
        return dasTable.getColumns().stream().map(ColumnInfo::name).toList();
    }

    /**
     * A guess about what a file publishes holds only while that file still says what the rename
     * left in it. Putting the old text back — a rollback, an undo — makes the guess wrong, and the
     * schemas go back to what the last extraction actually read.
     */
    public void testAGuessIsDroppedOnceTheFileNoLongerSaysWhatWasWritten() {
        installChain();
        renameFinColumn("display_name");
        assertEquals("the guess is in place to begin with",
                List.of("display_name"), columnNamesOf("p.d.fin"));

        rollBack("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");

        assertEquals("the file says the old name again, so the guess is gone",
                List.of("full_name"), columnNamesOf("p.d.fin"));
        assertEquals("and so is the one made about what it reads",
                List.of("full_name", "k"), columnNamesOf("p.d.src"));
    }

    /** Puts a file back the way a rollback does: the document is rewritten from outside. */
    private void rollBack(String action, String text) {
        Document document = FileDocumentManager.getInstance()
                .getDocument(fileOf(action).getVirtualFile());
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(text));
    }

    public void testTheSchemasFollowTheRenameWithoutWaitingForACompilation() {
        installChain();
        DataformTableSchemaService schemas = DataformTableSchemaService.getInstance(getProject());
        long before = schemas.getModificationCount();

        renameFinColumn("display_name");

        assertEquals("the action the caret was in publishes the new name",
                List.of("display_name"), columnNamesOf("p.d.fin"));
        assertEquals("so does the action it reads, which the rename reached too",
                List.of("display_name", "k"), columnNamesOf("p.d.src"));
        assertTrue("what caches a schema is told it changed",
                schemas.getModificationCount() > before);
    }
}
