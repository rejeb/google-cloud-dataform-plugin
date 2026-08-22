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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;
import io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret;

/**
 * Go to Declaration on a Dataform column has to be answered by the column window and by nothing
 * else. The platform reaches this action through the keymap before the click is delivered to the
 * editor, so anything that intervened later would navigate first and open the window afterwards.
 */
public class SqlxColumnNavigationActionTest extends DataformProjectFixture {

    public void testTheActionReplacesGoToDeclaration() {
        AnAction action = ActionManager.getInstance().getAction("GotoDeclaration");
        assertNotNull("Go to Declaration must exist", action);
        assertTrue("the column window has to own the gesture, found " + action.getClass().getName(),
                action instanceof SqlxColumnNavigationAction);
    }

    public void testItStillIsAGoToDeclarationAction() {
        assertTrue("everything that is not a Dataform column must keep the platform's behaviour",
                ActionManager.getInstance().getAction("GotoDeclaration")
                        instanceof GotoDeclarationAction);
    }

    public void testAColumnIsRecognisedAtTheCaretOffset() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        int offset = silver.getText().indexOf("order_id ,") + 3;
        assertNotNull("the action decides from the caret offset, as the platform does",
                SqlxColumnAtCaret.columnAt(silver, offset));
    }

    /**
     * The action sits in front of Go to Declaration for every language, so anything that is not a
     * Dataform column has to fall through untouched. A file of another type must not even be
     * inspected for columns.
     */
    public void testAFileOfAnotherTypeIsLeftToThePlatform() {
        com.intellij.psi.PsiFile java = myFixture.addFileToProject("src/Sample.java",
                "class Sample { int field; int read() { return field; } }");
        int offset = java.getText().indexOf("return field") + "return fi".length();
        assertNull("a Java file carries no Dataform column and must reach the platform",
                ColumnWindowTarget.at(java, offset));
    }

    public void testAPlainSqlFileIsLeftToThePlatform() {
        com.intellij.psi.PsiFile sql = myFixture.addFileToProject("scripts/plain.sql",
                "SELECT order_id FROM orders");
        int offset = sql.getText().indexOf("order_id") + 2;
        assertNull("only SQLX files carry Dataform columns", ColumnWindowTarget.at(sql, offset));
    }

    public void testAJsDefinitionFileIsLeftToThePlatform() {
        com.intellij.psi.PsiFile js = myFixture.addFileToProject("definitions/mart.js",
                "publish(\"x\").query(ctx => `SELECT order_id FROM t`);");
        int offset = js.getText().indexOf("order_id") + 2;
        assertNull("a JS definition is not a SQLX file", ColumnWindowTarget.at(js, offset));
    }

    public void testANameThatIsNotAColumnIsLeftToThePlatform() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        int offset = silver.getText().indexOf("config");
        assertNull("a config block is not a column and must fall through",
                SqlxColumnAtCaret.columnAt(silver, offset));
    }
}
