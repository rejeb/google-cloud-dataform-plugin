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
package io.github.rejeb.dataform.language.unittest;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class SqlxInputCompletionTest extends BasePlatformTestCase {

    private static final String CONFIG = "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        UnitTestGraphFixture.install(getProject(), getTestRootDisposable());
    }

    private List<String> complete(String name, String text) {
        PsiFile file = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();
        return lookups == null ? List.of() : lookups;
    }

    private String hostText() {
        Editor editor = myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
        return editor.getDocument().getText();
    }

    public void testInputKeywordIsOfferedInTestFiles() {
        List<String> lookups = complete("orders_test.sqlx", CONFIG + "SELECT 1 AS id\n\n<caret>\n");

        assertTrue("got " + lookups, lookups.contains("input"));
    }

    public void testInputKeywordIsNotOfferedInTableFiles() {
        List<String> lookups = complete("orders.sqlx", "config { type: \"table\" }\n\nSELECT 1\n\n<caret>\n");

        assertFalse("got " + lookups, lookups.contains("input"));
        assertTrue("got " + lookups, lookups.containsAll(List.of("pre_operations", "post_operations")));
    }

    public void testOperationsKeywordsAreNotOfferedInTestFiles() {
        List<String> lookups = complete("orders_test.sqlx", CONFIG + "SELECT 1 AS id\n\n<caret>\n");

        assertFalse("got " + lookups, lookups.contains("pre_operations"));
        assertFalse("got " + lookups, lookups.contains("post_operations"));
        assertTrue("got " + lookups, lookups.containsAll(List.of("config", "js", "input")));
    }

    public void testChoosingInputInsertsTheBlockWithTheCaretInTheName() {
        List<String> lookups = complete("orders_test.sqlx", CONFIG + "SELECT 1 AS id\n\n<caret>\n");
        int index = lookups.indexOf("input");
        assertTrue("got " + lookups, index >= 0);
        myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[index]);
        myFixture.finishLookup('\n');

        assertTrue("got [" + hostText() + "]", hostText().contains("input \"\" {\n  \n}"));
    }

    public void testLabelOffersTheDependenciesOfTheTestedDatasetFirst() {
        List<String> lookups = complete("orders_test.sqlx",
                CONFIG + "input \"<caret>\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS id\n");

        assertTrue("got " + lookups, lookups.size() >= 2);
        assertEquals("got " + lookups, List.of("customers", "raw_orders"),
                lookups.subList(0, 2).stream().sorted().toList());
        assertTrue("got " + lookups, lookups.contains("stats"));
        assertEquals("each name once, got " + lookups, 1,
                lookups.stream().filter("raw_orders"::equals).count());
    }

    public void testAPrefixCompletesTheLabel() {
        complete("orders_test.sqlx", CONFIG + "input \"cust<caret>\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS id\n");

        assertTrue("got [" + hostText() + "]", hostText().contains("input \"customers\" {"));
    }

    public void testWithoutGraphNothingIsOffered() {
        UnitTestGraphFixture.clear(getProject());

        List<String> lookups = complete("orders_test.sqlx",
                CONFIG + "input \"<caret>\" {\n  SELECT 1 AS id\n}\n\nSELECT 1 AS id\n");

        assertFalse("got " + lookups, lookups.contains("orders"));
    }
}
