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
 * A Dataform project builds SQL in JavaScript, so a column name lives in include helpers and in the
 * {@code js} blocks of the actions. Renaming the column reaches those too.
 */
public class ColumnRenameJavaScriptTest extends ColumnRenameFixture {

    private static final String ACTION_TEXT = """
            config { type: "table" }

            js {
              const keys = ["order_id"];
            }

            SELECT
                order_id
            FROM ${ref("raw")}
            """;

    private void installProjectWithJavaScript() {
        installProject(
                new Action("raw", "SELECT 1 AS order_id", List.of(), List.of("order_id")),
                new Action("action", "SELECT order_id FROM `p.d.raw`", List.of("raw"),
                        List.of("order_id")));
        myFixture.addFileToProject("includes/keys.js",
                "const orderKey = \"order_id\";\nmodule.exports = { orderKey };\n");
        addFile("raw", "config { type: \"table\" }\n\nSELECT 1 AS order_id\n");
        addFile("action", ACTION_TEXT);
    }

    private void renameActionColumn(String newName) {
        PsiFile action = fileOf("action");
        myFixture.configureFromExistingVirtualFile(action.getVirtualFile());
        int offset = action.getText().indexOf("    order_id");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 5);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 5);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    public void testTheColumnNameIsRenamedInTheIncludesAndInTheJsBlock() {
        installProjectWithJavaScript();

        renameActionColumn("order_ref");

        PsiFile includes = myFixture.getPsiManager().findFile(
                myFixture.findFileInTempDir("includes/keys.js"));
        assertNotNull(includes);
        assertTrue("an include naming the column is renamed\n" + includes.getText(),
                includes.getText().contains("\"order_ref\""));
        assertTrue("the js block of the action is renamed\n" + fileOf("action").getText(),
                fileOf("action").getText().contains("[\"order_ref\"]"));
        assertTrue("the query is renamed too\n" + fileOf("action").getText(),
                fileOf("action").getText().contains("    order_ref"));
    }
}
