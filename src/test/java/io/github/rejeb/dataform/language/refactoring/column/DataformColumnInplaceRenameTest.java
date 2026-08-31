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
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;

/**
 * Renaming a column from the editor renames it wherever it flows, in every file of the project.
 */
public class DataformColumnInplaceRenameTest extends DataformProjectFixture {

    private void renameAt(PsiFile file, String token, String newName) {
        int offset = file.getText().indexOf(token);
        assertTrue("the fixture must contain " + token, offset >= 0);
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 1);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset + 1);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    public void testRenamingASelectItemRenamesItUpstreamAndDownstream() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        PsiFile summary = open("gold/gold_customer_purchase_summary.sqlx");
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        PsiFile silver = open("silver/silver_orders.sqlx");

        renameAt(silver, "order_id ,", "order_ref");

        assertTrue("the column is declared under its new name where the caret was\n" + silver.getText(),
                silver.getText().contains("order_ref ,"));
        assertFalse("no declaration keeps the old name\n" + silver.getText(),
                silver.getText().contains("order_id ,"));
        assertTrue("the action producing it upstream is renamed too\n" + bronze.getText(),
                bronze.getText().contains("order_ref,"));
        assertTrue("the actions reading it downstream are renamed too\n" + gold.getText(),
                gold.getText().contains("o.order_ref"));
        assertTrue("every action of the chain is renamed\n" + summary.getText(),
                summary.getText().contains("order_ref,"));
    }

    public void testRenamingRewritesTheConfigThatNamesTheColumn() throws Exception {
        open("bronze/bronze_orders.sqlx");
        open("gold/gold_customer_purchase_summary.sqlx");
        open("gold/gold_customer_ltv.sqlx");
        PsiFile silver = open("silver/silver_orders.sqlx");

        renameAt(silver, "order_id ,", "order_ref");

        assertTrue("the unique key of the config names the column\n" + silver.getText(),
                silver.getText().contains("uniqueKey: [\"order_ref\"]"));
    }

    public void testRenamingAnAliasRenamesTheAliasAndItsReaders() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");

        renameAt(bronze, "order_status_raw", "raw_order_status");

        assertTrue("the alias declares the new name\n" + bronze.getText(),
                bronze.getText().contains("AS raw_order_status"));
        assertTrue("the expression the alias renames is untouched\n" + bronze.getText(),
                bronze.getText().contains("raw_status AS raw_order_status"));
    }

    public void testTheExpressionBehindAnAliasKeepsItsName() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");

        renameAt(bronze, "order_status_raw", "raw_order_status");

        assertTrue("a column the query renames is another column and is left alone\n" + bronze.getText(),
                bronze.getText().contains("NormalizeStatus(raw_status) AS order_status"));
    }
}
