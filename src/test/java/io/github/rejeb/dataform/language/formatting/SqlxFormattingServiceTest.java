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
package io.github.rejeb.dataform.language.formatting;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Reformatting a SQLX file through the platform must land in the editor's document: the
 * formatting task works on a copy and hands the text back, so the service has to let the
 * platform apply it.
 */
public class SqlxFormattingServiceTest extends BasePlatformTestCase {

    private PsiFile sqlx(String text) {
        PsiFile file = myFixture.addFileToProject("definitions/action.sqlx", text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private void reformat(PsiFile file) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(file);
        });
    }

    public void testReformattingTheFileUpdatesTheEditorDocument() {
        PsiFile file = sqlx("config{\n  type: \"table\"\n}\n\nSELECT 1 AS one FROM t\n");
        reformat(file);
        assertTrue("the structural rule 'config {' must reach the document: "
                        + myFixture.getEditor().getDocument().getText(),
                myFixture.getEditor().getDocument().getText().startsWith("config {"));
    }
}
