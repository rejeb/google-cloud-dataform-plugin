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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.RenameHandler;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;

/**
 * The rename action asks a handler whether it takes the gesture through the data context, not
 * through the caret. A handler that answers no is skipped and the platform opens its dialog, which
 * is what the user sees instead of the template.
 */
public class ColumnRenameHandlerAvailabilityTest extends DataformProjectFixture {

    private DataContext contextAt(PsiFile file, String token) {
        int offset = file.getText().indexOf(token);
        assertTrue("the fixture must contain " + token, offset >= 0);
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        return DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
    }

    public void testTheHandlerTakesTheGestureOnAColumn() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");

        DataContext context = contextAt(silver, "order_id ,");

        assertTrue("the in-place rename must be offered on a Dataform column",
                new DataformColumnRenameHandler().isAvailableOnDataContext(context));
    }

    public void testTheHandlerTakesTheGestureOnAnAlias() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");

        DataContext context = contextAt(bronze, "order_status_raw");

        assertTrue("the in-place rename must be offered on a select alias",
                new DataformColumnRenameHandler().isAvailableOnDataContext(context));
    }

    public void testTheGestureStartsTheTemplateOnTheElementTheActionHandsOver() throws Exception {
        open("bronze/bronze_orders.sqlx");
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        DataContext context = contextAt(gold, "order_status = 'PAID'");
        PsiElement handedOver = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.silver_orders", "order_status"));
        assertNotNull("the caret resolves to the column of the action declaring it", handedOver);
        assertFalse("and that column does not belong to the file being edited",
                "gold_customer_ltv.sqlx".equals(handedOver.getContainingFile().getName()));

        InplaceRefactoring started = new DataformColumnRenameHandler()
                .doRename(handedOver, myFixture.getEditor(), context);

        assertTrue("the template must start on the file being edited, whatever element the action "
                        + "hands over; the column it resolves to belongs to the file that declares it",
                started instanceof DataformColumnInplaceRenamer);
        started.stopIntroduce(myFixture.getEditor());
    }

    public void testTheHandlerIsTheOneTheActionPicks() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        DataContext context = contextAt(silver, "order_id ,");

        long claiming = RenameHandler.EP_NAME.getExtensionList().stream()
                .filter(handler -> handler.isAvailableOnDataContext(context))
                .count();

        assertEquals("exactly one handler must claim the gesture, or the platform asks the user "
                + "which one to run", 1, claiming);
    }
}
