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

import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;

import java.util.List;

public class SqlxTemplateExpressionCompletionTest extends BasePlatformTestCase {

    private void configureDefinition(String name, String text) {
        PsiFile f = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(f.getVirtualFile());
    }

    public void testDollarSuggestsTemplateExpressions() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM $<caret>\n");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull("expected a completion popup after '$'", lookups);
        assertTrue("got " + lookups, lookups.contains("${}"));
        assertTrue("got " + lookups, lookups.contains("${ref(\"\")}"));
        assertTrue("got " + lookups, lookups.contains("${self()}"));
    }

    public void testRefTemplateInsertionPlacesCaretBetweenQuotes() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM $<caret>\n");

        myFixture.completeBasic();
        myFixture.getLookup().setCurrentItem(
                myFixture.getLookupElements()[indexOf(myFixture.getLookupElementStrings(), "${ref(\"\")}")]);
        myFixture.finishLookup('\n');

        Editor hostEditor = myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
        String text = hostEditor.getDocument().getText();

        assertTrue("got [" + text + "]", text.contains("SELECT * FROM ${ref(\"\")}"));
        int expectedCaret = text.indexOf("${ref(\"\")}") + "${ref(\"".length();
        assertEquals(expectedCaret, hostEditor.getCaretModel().getOffset());
    }

    public void testNoTemplateCompletionWithoutDollar() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM tab<caret>\n");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertFalse("template expressions must not be proposed without '$', got " + lookups,
                lookups != null && lookups.contains("${ref(\"\")}"));
    }

    public void testDollarSuggestsQualifiedTemplatesInJsDefinition() {
        configureDefinition("mart.js",
                "publish(\"x\").query(ctx => `SELECT * FROM $<caret>`);");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull("expected a completion popup after '$'", lookups);
        assertTrue("got " + lookups, lookups.contains("${}"));
        assertTrue("got " + lookups, lookups.contains("${ctx.ref(\"\")}"));
        assertTrue("got " + lookups, lookups.contains("${ctx.self()}"));
    }

    public void testJsTemplatesUseActualCallbackParameterName() {
        configureDefinition("mart.js",
                "publish(\"x\").query(context => `SELECT * FROM $<caret>`);");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull(lookups);
        assertTrue("got " + lookups, lookups.contains("${context.ref(\"\")}"));
        assertTrue("got " + lookups, lookups.contains("${context.self()}"));
    }

    public void testDollarSuggestsTemplatesInJsQueryWithExistingHole() {
        configureDefinition("mart.js",
                "publish(\"x\").query(ctx => `SELECT * FROM ${ctx.self()} JOIN $<caret>`);");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertNotNull("expected a completion popup after '$'", lookups);
        assertTrue("got " + lookups, lookups.contains("${ctx.ref(\"\")}"));
    }

    public void testJsRefTemplateInsertionPlacesCaretBetweenQuotes() {
        configureDefinition("mart.js",
                "publish(\"x\").query(ctx => `SELECT * FROM $<caret>`);");

        myFixture.completeBasic();
        myFixture.getLookup().setCurrentItem(
                myFixture.getLookupElements()[indexOf(myFixture.getLookupElementStrings(), "${ctx.ref(\"\")}")]);
        myFixture.finishLookup('\n');

        Editor hostEditor = myFixture.getEditor() instanceof EditorWindow window
                ? window.getDelegate()
                : myFixture.getEditor();
        String text = hostEditor.getDocument().getText();

        assertTrue("got [" + text + "]", text.contains("SELECT * FROM ${ctx.ref(\"\")}"));
        int expectedCaret = text.indexOf("${ctx.ref(\"\")}") + "${ctx.ref(\"".length();
        assertEquals(expectedCaret, hostEditor.getCaretModel().getOffset());
    }

    public void testNoTemplateCompletionOutsideQueryTemplateInJs() {
        configureDefinition("mart.js",
                "const name = \"a$<caret>\";");

        myFixture.completeBasic();
        List<String> lookups = myFixture.getLookupElementStrings();

        assertFalse("template expressions must not be proposed outside a query template, got " + lookups,
                lookups != null && lookups.contains("${ctx.ref(\"\")}"));
    }

    private void chooseTemplate(String template) {
        myFixture.completeBasic();
        myFixture.getLookup().setCurrentItem(
                myFixture.getLookupElements()[indexOf(myFixture.getLookupElementStrings(), template)]);
        myFixture.finishLookup('\n');
    }

    private Editor hostEditor() {
        return myFixture.getEditor() instanceof EditorWindow window ? window.getDelegate() : myFixture.getEditor();
    }

    public void testCaretBetweenQuotesAfterEvaluatedExpressionsOfAnotherLength() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\nSELECT ${constants.A} AS a, ${constants.B} AS b FROM $\n");
        DataformExpressionEvaluationServiceImpl evaluation = (DataformExpressionEvaluationServiceImpl)
                DataformExpressionEvaluationService.getInstance(getProject());
        evaluation.putCachedValue(file.getVirtualFile(), "constants.A", "500");
        evaluation.putCachedValue(file.getVirtualFile(), "constants.B", "500");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        hostEditor().getCaretModel().moveToOffset(file.getText().indexOf("$\n") + 1);

        chooseTemplate("${ref(\"\")}");

        String text = hostEditor().getDocument().getText();
        assertEquals(text, text.indexOf("FROM ${ref(\"\")}") + "FROM ${ref(\"".length(),
                hostEditor().getCaretModel().getOffset());
    }

    public void testCaretBetweenQuotesAfterAnEarlierExpression() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"a\")}\nJOIN $<caret>\nON TRUE\n");

        chooseTemplate("${ref(\"\")}");

        String text = hostEditor().getDocument().getText();
        assertEquals(text.indexOf("JOIN ${ref(\"\")}") + "JOIN ${ref(\"".length(),
                hostEditor().getCaretModel().getOffset());
    }

    public void testSelfCaretStaysOnTheLineAfterAnEarlierExpression() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"a\")}\nJOIN $<caret>\nON TRUE\n");

        chooseTemplate("${self()}");

        String text = hostEditor().getDocument().getText();
        assertEquals(text.indexOf("JOIN ${self()}") + "JOIN ${self()}".length(),
                hostEditor().getCaretModel().getOffset());
    }

    public void testCaretBetweenQuotesAfterAnEarlierExpressionInAnotherBlock() {
        configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\npre_operations {\n  DECLARE x DEFAULT ${when(true, \"1\", \"2\")};\n}\n\nSELECT * FROM $<caret>\n");

        chooseTemplate("${ref(\"\")}");

        String text = hostEditor().getDocument().getText();
        assertEquals(text.indexOf("FROM ${ref(\"\")}") + "FROM ${ref(\"".length(),
                hostEditor().getCaretModel().getOffset());
    }

    private static int indexOf(List<String> values, String value) {
        assertNotNull(values);
        int index = values.indexOf(value);
        assertTrue("missing lookup " + value + " in " + values, index >= 0);
        return index;
    }
}
