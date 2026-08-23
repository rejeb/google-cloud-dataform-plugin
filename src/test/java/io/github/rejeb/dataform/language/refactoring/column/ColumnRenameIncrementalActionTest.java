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
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlanner;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory;
import io.github.rejeb.dataform.language.refactoring.column.usage.ColumnRenameEdit;

import java.util.List;

/**
 * An incremental action names its columns in three places at once: the partitioning expression of
 * the config, the helper the incremental branch hands the column to, and the query itself.
 *
 * <p>The helper is handed the name as a string inside a template that also holds SQL, so the same
 * characters can be reached both as a literal and as a piece of the text around it. Writing both
 * mangles the file, and a place the rename is sure of must not need the user to ask for it.</p>
 */
public class ColumnRenameIncrementalActionTest extends ColumnRenameFixture {

    private static final String SILVER = """
            config {
                type: "incremental",
                uniqueKey: ["order_id"],
                bigquery: {
                    partitionBy: "TIMESTAMP_TRUNC(order_ts, DAY)"
                },
            }

            pre_operations {
            ${when(incremental(), `
                DELETE FROM ${self()}
                WHERE ${schema_helpers.incrementalWhereClause("order_ts", 3)}
              `)}
            }

            SELECT
                order_id,
                order_ts
            FROM ${ref("bronze_orders")}
            """;

    private PsiFile installAndOpen() {
        installProject(
                new Action("bronze_orders", "SELECT 1 AS order_id, CURRENT_TIMESTAMP() AS order_ts",
                        List.of(), List.of("order_id", "order_ts")),
                new Action("silver_orders", "SELECT order_id, order_ts FROM `p.d.bronze_orders`",
                        List.of("bronze_orders"), List.of("order_id", "order_ts")));
        addFile("bronze_orders",
                "config { type: \"table\" }\n\nSELECT 1 AS order_id, CURRENT_TIMESTAMP() AS order_ts\n");
        return addFile("silver_orders", SILVER);
    }

    private void renameSelectItem(PsiFile silver, String newName) {
        myFixture.configureFromExistingVirtualFile(silver.getVirtualFile());
        int offset = silver.getText().indexOf("    order_ts\n");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 5);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 5);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    public void testTheStringHandedToTheHelperIsRewrittenOnce() {
        PsiFile silver = installAndOpen();

        renameSelectItem(silver, "order_datetime");

        assertTrue("the helper is handed the new name\n" + silver.getText(),
                silver.getText().contains("incrementalWhereClause(\"order_datetime\", 3)"));
        assertFalse("and it is written once, not once per way of reaching it\n" + silver.getText(),
                silver.getText().contains("order_datetime\"etime"));
    }

    public void testThePartitioningExpressionAndTheQueryAreRewritten() {
        PsiFile silver = installAndOpen();

        renameSelectItem(silver, "order_datetime");

        assertTrue("the partitioning expression names the column\n" + silver.getText(),
                silver.getText().contains("TIMESTAMP_TRUNC(order_datetime, DAY)"));
        assertTrue("the query declares it under the new name\n" + silver.getText(),
                silver.getText().contains("    order_datetime\n"));
        assertFalse("nothing keeps the old name\n" + silver.getText(),
                silver.getText().contains("order_ts"));
    }

    public void testNoPlaceOfThePlanOverlapsAnother() {
        PsiFile silver = installAndOpen();
        int offset = silver.getText().indexOf("    order_ts\n");
        ColumnRenamePlan plan = ColumnRenamePlanner.getInstance(getProject())
                .plan(ColumnRenameSubjectFactory.at(silver, offset + 5).orElseThrow(), "order_datetime");

        for (ColumnRenameEdit edit : plan.edits()) {
            long overlapping = plan.edits().stream()
                    .filter(other -> other != edit && other.file().equals(edit.file())
                            && other.hostRange().intersects(edit.hostRange()))
                    .count();
            assertEquals("writing " + edit.kind() + " at " + edit.hostRange()
                    + " would write over another place", 0, overlapping);
        }
    }

    public void testEveryPlaceTheRenameIsSureOfIsSelectedInTheWindow() {
        PsiFile silver = installAndOpen();
        int offset = silver.getText().indexOf("    order_ts\n");
        ColumnRenamePlan plan = ColumnRenamePlanner.getInstance(getProject())
                .plan(ColumnRenameSubjectFactory.at(silver, offset + 5).orElseThrow(), "order_datetime");

        for (ColumnRenameEdit edit : plan.edits()) {
            assertFalse(edit.kind() + " is a place the rename is sure of, so the window must keep it "
                            + "selected rather than list it as a guess",
                    edit.risk() == ColumnRenameEdit.Risk.CERTAIN && edit.isNonCode());
        }
    }
}
