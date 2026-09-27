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
package io.github.rejeb.dataform.language.reference;

import com.google.gson.Gson;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.testing.ProjectStateInstaller;

/**
 * Ctrl+Click on the name given to {@code ref()} goes to the action it designates: in SQLX templates,
 * and in JavaScript definition files where the call sits in a SQL template string, whose literals
 * expose no contributed reference.
 */
public class DataformRefNavigationTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("definitions/orders.sqlx", "SELECT 1");
        myFixture.addFileToProject("definitions/mart/orders.sqlx", "SELECT 1");
        ProjectStateInstaller.installGraph(getProject(), getTestRootDisposable(), new Gson().fromJson("""
                {"tables": [
                  {"type": "table", "target": {"database": "p", "schema": "d", "name": "orders"},
                   "fileName": "definitions/orders.sqlx"},
                  {"type": "table", "target": {"database": "p", "schema": "mart", "name": "orders"},
                   "fileName": "definitions/mart/orders.sqlx"}
                ]}""", CompiledGraph.class));
    }

    public void testTheNameValueGoesToTheDesignatedTable() {
        myFixture.configureByText("query.js", "ref({schema: \"mart\", name: \"ord<caret>ers\"});");

        assertNavigatesTo("definitions/mart/orders.sqlx");
    }

    public void testANameInASqlTemplateOfADefinitionFileGoesToTheDesignatedTable() {
        configureDefinition("publish(\"x\").query(ctx => `SELECT * FROM ${ctx.ref(\"mart\", \"ord<caret>ers\")}`);");

        assertNavigatesTo("definitions/mart/orders.sqlx");
    }

    public void testAnObjectNameInASqlTemplateOfADefinitionFileGoesToTheDesignatedTable() {
        configureDefinition("publish(\"x\").query(ctx => `SELECT * FROM ${ctx.ref({schema: \"mart\", name: \"ord<caret>ers\"})}`);");

        assertNavigatesTo("definitions/mart/orders.sqlx");
    }

    public void testTheNameKeyIsLeftToJavaScript() {
        myFixture.configureByText("query.js", "ref({schema: \"mart\", na<caret>me: \"orders\"});");
        assertNull(handlerTargets());

        myFixture.configureByText("query.js", "ref({\"schema\": \"mart\", \"na<caret>me\": \"orders\"});");
        assertNull(handlerTargets());
    }

    public void testTheSchemaKeyAndValueAreLeftToJavaScript() {
        myFixture.configureByText("query.js", "ref({sch<caret>ema: \"mart\", name: \"orders\"});");
        assertNull(handlerTargets());

        myFixture.configureByText("query.js", "ref(\"ma<caret>rt\", \"orders\");");
        assertNull(handlerTargets());
    }

    private void configureDefinition(String code) {
        PsiFile file = myFixture.addFileToProject("definitions/defs.js", code.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.getEditor().getCaretModel().moveToOffset(code.indexOf("<caret>"));
    }

    private PsiElement[] handlerTargets() {
        return new DataformRefGotoDeclarationHandler().getGotoDeclarationTargets(
                myFixture.getFile().findElementAt(myFixture.getCaretOffset()), myFixture.getCaretOffset(),
                myFixture.getEditor());
    }

    private void assertNavigatesTo(String path) {
        assertEquals(GotoDeclarationOrUsageHandler2.GTDUOutcome.GTD,
                GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction(
                        myFixture.getEditor(), myFixture.getFile(), myFixture.getCaretOffset()));
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(
                getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
        assertEquals(1, targets.length);
        assertTrue(targets[0] instanceof PsiFile);
        assertTrue(((PsiFile) targets[0]).getVirtualFile().getPath(),
                ((PsiFile) targets[0]).getVirtualFile().getPath().endsWith(path));
    }
}
