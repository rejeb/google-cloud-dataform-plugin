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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.usages.ColumnWindowTarget;

/**
 * Which queries declare the columns of the table a file builds.
 *
 * <p>A Dataform table is built by one query, but that query is not always held by the statement
 * itself: a set operation holds one per branch, parentheses hold one, and a {@code WITH} holds one
 * after its CTEs. A file whose query is written any of those ways builds its columns exactly as a
 * plain {@code SELECT} does, and a reader of that file has to be told so.</p>
 *
 * <p>What goes wrong when it is not: the file stops declaring its own columns, so the column window
 * opened on one of them becomes the upstream column's window, and — because the declaring element
 * can no longer be found — every file downstream of this one loses its Declaration row while its
 * Usages rows stay. The symptom shows up in the downstream file, which may be a plain
 * {@code SELECT} with nothing unusual about it at all.</p>
 */
public class SqlxOutputColumnShapeTest extends DataformProjectFixture {

    private static final String CONFIG = "config {\n"
            + "  type: \"incremental\",\n"
            + "  schema: dataform.projectConfig.vars.silver_dataset,\n"
            + "  uniqueKey: [\"order_id\"]\n"
            + "}\n\n";

    private static final String READS_BRONZE =
            "SELECT order_id, customer_id FROM ${ref(\"bronze_orders\")} bo\n";

    /** The action whose shape is under test, written over the fixture's own silver action. */
    private PsiFile silverOrders(String body) {
        PsiFile file = myFixture.addFileToProject("definitions/silver_orders.sqlx", CONFIG + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private void assertDeclaresOrderId(String shape, String body, String mainSelect) {
        PsiFile file = silverOrders(body);
        PsiElement declaring = SqlxOutputColumnLocator.findOutputColumn(file, "order_id");
        assertNotNull(shape + " builds order_id, so the file must declare it", declaring);
        assertEquals(shape + " must declare the column the select list names",
                "order_id", declaring.getText());

        ColumnRef declared = ColumnOriginService.getInstance(getProject())
                .declaredColumn(file, referenceAt(file, selectedOrderId(file, mainSelect)));
        assertNotNull(shape + ": the select-list item must name an output column of the table",
                declared);
        assertEquals("proj.ds.silver_orders", declared.tableFullName());
        assertEquals("order_id", declared.columnName());
    }

    /**
     * The {@code order_id} named by a given select list. The query a table is built from is named
     * rather than guessed: a CTE has a select list of its own, and it declares nothing of the table.
     */
    private int selectedOrderId(PsiFile file, String select) {
        int at = file.getText().indexOf(select);
        assertTrue("the body must hold '" + select + "'", at >= 0);
        int column = file.getText().indexOf("order_id", at);
        assertTrue("that select must name order_id", column >= 0);
        return column + 1;
    }

    private PsiElement referenceAt(PsiFile file, int offset) {
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, offset);
        assertNotNull("the offset must be inside the injected SQL", injected);
        PsiElement reference = SqlxColumnAtCaret.referenceOf(injected);
        assertNotNull("the offset must sit on a column reference", reference);
        return reference;
    }

    public void testAPlainSelectDeclaresItsColumns() {
        assertDeclaresOrderId("a plain select", READS_BRONZE, READS_BRONZE.strip());
    }

    public void testAUnionDeclaresTheColumnsOfItsFirstBranch() {
        assertDeclaresOrderId("a UNION", READS_BRONZE + "UNION ALL\n" + READS_BRONZE,
                READS_BRONZE.strip());
    }

    public void testAnExceptDeclaresItsColumns() {
        assertDeclaresOrderId("an EXCEPT", READS_BRONZE + "EXCEPT DISTINCT\n" + READS_BRONZE,
                READS_BRONZE.strip());
    }

    public void testAnIntersectDeclaresItsColumns() {
        assertDeclaresOrderId("an INTERSECT",
                READS_BRONZE + "INTERSECT DISTINCT\n" + READS_BRONZE, READS_BRONZE.strip());
    }

    public void testACteFollowedByAUnionDeclaresItsColumns() {
        assertDeclaresOrderId("a CTE whose main query is a UNION",
                "WITH base AS (\n  " + READS_BRONZE + ")\n"
                        + "SELECT order_id, customer_id FROM base\n"
                        + "UNION ALL\n" + READS_BRONZE,
                "SELECT order_id, customer_id FROM base");
    }

    public void testAParenthesizedQueryDeclaresItsColumns() {
        assertDeclaresOrderId("a parenthesized query",
                "(SELECT order_id, customer_id FROM ${ref(\"bronze_orders\")} bo)\n",
                "(SELECT order_id");
    }

    /** A CTE's own select list builds the CTE, not the table, so it declares nothing of it. */
    public void testACteSelectListDeclaresNothingOfTheTable() {
        PsiFile file = silverOrders("WITH base AS (\n  " + READS_BRONZE + ")\n"
                + "SELECT order_id, customer_id FROM base\n");
        assertNull("a column of a CTE is an input of the query, never a column of the table",
                ColumnOriginService.getInstance(getProject()).declaredColumn(file,
                        referenceAt(file, selectedOrderId(file, READS_BRONZE.strip()))));
    }

    /**
     * The reported symptom, from the side it is seen: a plain file downstream of an action written
     * as a set operation must still find where the column it reads is declared.
     */
    public void testADownstreamFileFindsTheColumnOfAUnionAction() throws Exception {
        open("bronze/bronze_orders.sqlx");
        silverOrders(READS_BRONZE + "UNION ALL\n" + READS_BRONZE);
        PsiFile downstream = open("gold/gold_customer_purchase_summary.sqlx");

        int offset = downstream.getText().indexOf("order_id");
        assertTrue("the downstream file must read order_id", offset >= 0);
        ColumnWindowTarget target = ColumnWindowTarget.at(downstream, offset + 1);
        assertNotNull("the caret must land on a column", target);
        assertEquals("the column the downstream file reads is declared by the action it reads,"
                        + " however that action's query is written, got " + target.declarations(),
                1, target.declarations().size());
    }
}
