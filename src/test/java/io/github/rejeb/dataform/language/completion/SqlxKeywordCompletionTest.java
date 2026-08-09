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
package io.github.rejeb.dataform.language.completion;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class SqlxKeywordCompletionTest extends BasePlatformTestCase {

    private void configureDefinition(String name, String text) {
        PsiFile f = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(f.getVirtualFile());
    }

    private String hostText() {
        Editor hostEditor = myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
        return hostEditor.getDocument().getText();
    }

    private void chooseLookup(String lookupString) {
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("expected a completion popup", lookups);
        int index = lookups.indexOf(lookupString);
        assertTrue("no [" + lookupString + "] in " + lookups, index >= 0);
        myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[index]);
        myFixture.finishLookup('\n');
    }

    public void testKeywordsAreProposedOnAnEmptyLine() {
        configureDefinition("mart.sqlx", "config { type: \"table\" }\n\nSELECT 1\n\n<caret>\n");

        myFixture.completeBasic();

        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull("expected a completion popup", lookups);
        assertTrue("got " + lookups, lookups.contains("pre_operations"));
    }

    public void testChoosingAKeywordInsertsItsBlock() {
        configureDefinition("mart.sqlx", "config { type: \"table\" }\n\nSELECT 1\n\n<caret>\n");

        myFixture.completeBasic();
        chooseLookup("pre_operations");

        assertTrue("keyword must be inserted with its block, got [" + hostText() + "]",
                hostText().contains("pre_operations {"));
    }

    public void testChoosingConfigInsertsItsBlock() {
        configureDefinition("mart.sqlx", "SELECT 1\n\n<caret>\n");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        assertNotNull(lookups);
        assertEquals("config must be proposed once, got " + lookups,
                1, lookups.stream().filter("config"::equals).count());
        chooseLookup("config");

        assertTrue("config must be inserted with its block, got [" + hostText() + "]",
                hostText().contains("config {"));
    }

    public void testConfigIsProposedOnceInAFileThatAlreadyHasAConfigBlock() {
        configureDefinition("mart.sqlx", "config { type: \"table\" }\n\nSELECT 1\n\n<caret>\n");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull(lookups);
        assertEquals("config must be proposed once, got " + lookups,
                1, lookups.stream().filter("config"::equals).count());
    }

    public void testChoosingConfigInsertsItsBlockWhenTheWordAlreadyOccursInTheFile() {
        configureDefinition("mart.sqlx", "config { type: \"table\" }\n\nSELECT 1\n\n<caret>\n");

        myFixture.completeBasic();
        chooseLookup("config");

        assertTrue("config must be inserted with its block, got [" + hostText() + "]",
                hostText().lastIndexOf("config {") > hostText().indexOf("SELECT 1"));
    }

    public void testConfigInsertsItsBlockInAnEmptyFile() {
        configureDefinition("mart.sqlx", "<caret>");

        myFixture.completeBasic();
        chooseLookup("config");

        assertTrue("config must be inserted with its block, got [" + hostText() + "]",
                hostText().contains("config {"));
    }

    public void testConfigInsertsItsBlockOnAPartiallyTypedWordInAnEmptyFile() {
        configureDefinition("mart.sqlx", "con<caret>");

        myFixture.completeBasic();
        if (myFixture.getLookup() != null) {
            chooseLookup("config");
        }

        assertTrue("config must be inserted with its block, got [" + hostText() + "]",
                hostText().contains("config {"));
    }

    public void testConfigInsertsItsBlockAboveAnExistingConfigBlock() {
        configureDefinition("mart.sqlx", "<caret>\nconfig { type: \"table\" }\n\nSELECT 1\n");

        myFixture.completeBasic();
        chooseLookup("config");

        assertTrue("config must be inserted with its block, got [" + hostText() + "]",
                hostText().startsWith("config {"));
    }

    public void testTheCaretLandsInsideTheInsertedBlock() {
        configureDefinition("mart.sqlx", "SELECT 1\n\n<caret>\n");

        myFixture.completeBasic();
        chooseLookup("config");

        Editor hostEditor = myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
        String text = hostEditor.getDocument().getText();
        int openBrace = text.indexOf('{', text.indexOf("config"));
        int closeBrace = text.indexOf('}', openBrace);
        int caret = hostEditor.getCaretModel().getOffset();

        assertTrue("caret must sit inside the block, got [" + text + "] caret at " + caret,
                caret > openBrace && caret < closeBrace);
    }

    public void testAKeywordAlreadyFollowedByABlockIsNotGivenASecondOne() {
        configureDefinition("mart.sqlx", "SELECT 1\n\n<caret> { type: \"table\" }\n");

        myFixture.completeBasic();
        chooseLookup("config");

        assertEquals("the existing block must be reused, got [" + hostText() + "]",
                1, hostText().chars().filter(c -> c == '{').count());
    }

    public void testAnAutoInsertedKeywordAlsoGetsItsBlock() {
        configureDefinition("mart.sqlx", "config { type: \"table\" }\n\nSELECT 1\n\npre_oper<caret>\n");

        myFixture.completeBasic();

        assertTrue("keyword must be inserted with its block, got [" + hostText() + "]",
                hostText().contains("pre_operations {"));
    }
}
