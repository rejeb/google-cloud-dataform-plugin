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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import io.github.rejeb.dataform.language.columns.rename.ColumnRenameFixture;
import io.github.rejeb.dataform.language.columns.rename.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.columns.rename.usage.ColumnRenameEdit;

import java.util.List;
import java.util.Set;

public class DownstreamRenamePlannerTest extends ColumnRenameFixture {

    private static final DownstreamRename EMAIL =
            new DownstreamRename("p.d.customers", "email", "customer_email", Set.of("p.d.customers"));

    private void installRenamedCustomers(Action reader, String readerSqlx, Action... more) {
        Action customers = new Action("customers", "SELECT 1 AS customer_id, 'a' AS customer_email",
                List.of(), List.of("customer_id", "customer_email"));
        Action[] actions = new Action[2 + more.length];
        actions[0] = customers;
        actions[1] = reader;
        System.arraycopy(more, 0, actions, 2, more.length);
        installProject(actions);
        addFile("customers", "config { type: \"table\" }\n\nSELECT 1 AS customer_id, 'a' AS customer_email\n");
        addFile(reader.name(), readerSqlx);
    }

    private List<String> editsOf(ColumnRenamePlan plan) {
        return plan.edits().stream().map(this::describe).sorted().toList();
    }

    private String describe(ColumnRenameEdit edit) {
        Document document = FileDocumentManager.getInstance().getDocument(edit.file());
        return edit.file().getName() + "|" + edit.hostRange().substring(document.getText()) + "|" + edit.replacement();
    }

    public void testReadsOfTheOldNameAreRenamedAndTheRenameGoesOnDownstream() {
        installRenamedCustomers(
                new Action("silver", "SELECT customer_id, email FROM `p.d.customers`",
                        List.of("customers"), List.of("customer_id", "email")),
                "config { type: \"table\", uniqueKey: [\"email\"] }\n\nSELECT customer_id, email\nFROM ${ref(\"customers\")}\n",
                new Action("gold", "WITH x AS (SELECT c.email FROM `p.d.customers` AS c) "
                        + "SELECT s.email, x.email AS crm_email FROM `p.d.silver` AS s JOIN x ON LOWER(x.email) = s.email",
                        List.of("customers", "silver"), List.of("email", "crm_email")));
        addFile("gold", "config { type: \"table\" }\n\nWITH x AS (\n  SELECT c.email\n  FROM ${ref(\"customers\")} AS c\n)\n"
                + "SELECT s.email, x.email AS crm_email\nFROM ${ref(\"silver\")} AS s\nJOIN x ON LOWER(x.email) = s.email\n");

        ColumnRenamePlan plan = DownstreamRenamePlanner.plan(getProject(), EMAIL);

        assertNull(plan.refusal());
        assertEquals(List.of(
                "gold.sqlx|c.email|c.customer_email AS email",
                "gold.sqlx|email|customer_email",
                "gold.sqlx|email|customer_email",
                "silver.sqlx|\"email\"|\"customer_email\"",
                "silver.sqlx|email|customer_email"), editsOf(plan));
    }

    public void testAnUnqualifiedReadAmongSeveralTablesIsReportedNotRenamed() {
        installRenamedCustomers(
                new Action("orders", "SELECT 1 AS order_id", List.of(), List.of("order_id")),
                "config { type: \"table\" }\n\nSELECT 1 AS order_id\n",
                new Action("joined", "SELECT email FROM `p.d.customers` CROSS JOIN `p.d.orders`",
                        List.of("customers", "orders"), List.of("email")));
        addFile("joined", "config { type: \"table\" }\n\nSELECT email\nFROM ${ref(\"customers\")}\nCROSS JOIN ${ref(\"orders\")}\n");

        ColumnRenamePlan plan = DownstreamRenamePlanner.plan(getProject(), EMAIL);

        assertEquals(List.of(), editsOf(plan));
        assertEquals(1, plan.warnings().size());
        assertTrue(plan.warnings().getFirst(), plan.warnings().getFirst().contains("joined.sqlx"));
    }

    public void testWithoutTheRenamedColumnInItsFileThePlanIsRefused() {
        installRenamedCustomers(
                new Action("silver", "SELECT email FROM `p.d.customers`", List.of("customers"), List.of("email")),
                "config { type: \"table\" }\n\nSELECT email\nFROM ${ref(\"customers\")}\n");

        ColumnRenamePlan plan = DownstreamRenamePlanner.plan(getProject(),
                new DownstreamRename("p.d.customers", "email", "contact_email", Set.of("p.d.customers")));

        assertNotNull(plan.refusal());
    }
}
