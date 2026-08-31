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

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;

/**
 * The rename handler takes the gesture only on a Dataform column.
 *
 * <p>The SQL plugin renames a table alias and the name of a common table expression correctly, and
 * both are file-local. Claiming those would replace a working refactoring with one that searches the
 * whole project for a name that means nothing outside the query.</p>
 */
public class ColumnRenameHandlerPriorityTest extends DataformProjectFixture {

    private static final class Probe extends DataformColumnRenameHandler {
        private boolean availableAt(Editor editor, PsiFile file) {
            return isAvailable(null, editor, file);
        }
    }

    private boolean handlesCaretAt(PsiFile file, String token) {
        int offset = file.getText().indexOf(token);
        assertTrue("the fixture must contain " + token, offset >= 0);
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        return new Probe().availableAt(myFixture.getEditor(), myFixture.getFile());
    }

    public void testTheGestureIsTakenOnAColumnOfTheAction() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");

        assertTrue("a column of the action is renamed by this handler",
                handlesCaretAt(silver, "order_id ,"));
    }

    public void testATableAliasIsLeftToTheSqlPlugin() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");

        assertFalse("a table alias is not a column", handlesCaretAt(gold, "AS o\n"));
    }

    public void testAColumnOfACommonTableExpressionIsLeftToTheSqlPlugin() throws Exception {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");

        assertFalse("a name local to a query is renamed by the SQL plugin, file by file",
                handlesCaretAt(gold, "AS orders_count"));
    }

    public void testAKeywordIsNotRenameable() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");

        assertFalse(handlesCaretAt(silver, "SELECT"));
    }
}
