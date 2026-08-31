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

import java.util.List;

/**
 * A column name is not unique in a Dataform project. {@code customer_id} names a column of the
 * orders chain and another of the customers chain, and renaming one must leave the other alone —
 * including where the other is written by JavaScript rather than by SQL.
 */
public class ColumnRenameUnrelatedChainTest extends ColumnRenameFixture {

    private static final String CUSTOMERS = """
            config {
                type: "table",
                columns: {
                    customer_id: "Source customer identifier",
                    full_name: "Raw full name"
                }
            }

            js {
                const sampleCustomers = [[1, "Alice"], [2, "Bram"]];
            }

            SELECT
            *
            FROM UNNEST([
                ${sampleCustomers.map(c => `STRUCT(${c[0]} AS customer_id, '${c[1]}' AS full_name)`).join(",")}
            ])
            """;

    private PsiFile installTwoChains() {
        installProject(
                new Action("bronze_customers", "SELECT 1 AS customer_id, 'a' AS full_name",
                        List.of(), List.of("customer_id", "full_name")),
                new Action("bronze_orders", "SELECT 1 AS customer_id, 2 AS order_id",
                        List.of(), List.of("customer_id", "order_id")),
                new Action("silver_orders", "SELECT customer_id, order_id FROM `p.d.bronze_orders`",
                        List.of("bronze_orders"), List.of("customer_id", "order_id")));
        addFile("bronze_customers", CUSTOMERS);
        addFile("bronze_orders",
                "config { type: \"table\" }\n\nSELECT 1 AS customer_id, 2 AS order_id\n");
        return addFile("silver_orders",
                "config { type: \"table\" }\n\nSELECT\n    customer_id,\n    order_id\n"
                        + "FROM ${ref(\"bronze_orders\")}\n");
    }

    private void renameSilverCustomerId(PsiFile silver, String newName) {
        myFixture.configureFromExistingVirtualFile(silver.getVirtualFile());
        int offset = silver.getText().indexOf("    customer_id");
        myFixture.getEditor().getCaretModel().moveToOffset(offset + 5);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(myFixture.getFile(), offset + 5);
        assertNotNull("the caret must sit inside the injected SQL", injected);
        BaseRefactoringProcessor.runWithDisabledPreview(() ->
                CodeInsightTestUtil.doInlineRename(new DataformColumnRenameHandler(), newName,
                        myFixture.getEditor(), injected));
    }

    public void testTheChainOfTheCaretIsRenamed() {
        PsiFile silver = installTwoChains();

        renameSilverCustomerId(silver, "buyer_id");

        assertTrue("the column of the caret is renamed\n" + silver.getText(),
                silver.getText().contains("    buyer_id"));
        assertTrue("and the action it comes from too\n" + fileOf("bronze_orders").getText(),
                fileOf("bronze_orders").getText().contains("AS buyer_id"));
    }

    public void testTheOtherChainIsNotTouchedAtAll() {
        PsiFile silver = installTwoChains();
        String before = fileOf("bronze_customers").getText();

        renameSilverCustomerId(silver, "buyer_id");

        assertEquals("an action of another chain that happens to have a column of the same name "
                + "must be left exactly as it was", before, fileOf("bronze_customers").getText());
    }
}
