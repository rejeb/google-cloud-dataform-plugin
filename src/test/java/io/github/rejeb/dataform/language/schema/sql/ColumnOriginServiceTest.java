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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;

public class ColumnOriginServiceTest extends DataformProjectFixture {

    public void testDeclaredColumnOfASelectItem() throws Exception {
        PsiFile silver = open("silver/silver_orders.sqlx");
        ColumnRef declared = ColumnOriginService.getInstance(getProject())
                .declaredColumn(silver, selectItemAt(silver, 23, "order_id"));
        assertNotNull("silver line 23 declares a column", declared);
        assertEquals("order_id", declared.columnName());
        assertEquals("proj.ds.silver_orders", declared.tableFullName());
    }

    public void testDeclaringElementOfAColumnIsInItsOwnFile() throws Exception {
        open("bronze/bronze_orders.sqlx");
        assertEquals("bronze_orders.sqlx line 23 declares the column",
                23, lineOf(ColumnOriginService.getInstance(getProject())
                        .declaringElement(new ColumnRef("proj.ds.bronze_orders", "order_id"))));
    }

    public void testDasColumnIsResolvedFromTheSchema() {
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.silver_orders", "order_id"));
        assertNotNull("the schema knows silver_orders.order_id", column);
        assertEquals("order_id", column.getName());
        assertNotNull("the column must expose its table", column.getTable());
        assertEquals("silver_orders", column.getTable().getName());
    }

    /**
     * An action that selects a star names none of its columns, so each of them is declared by the
     * star. Without this a column of such a table has no declaration at all, and every file reading
     * it loses the row that says where it came from.
     */
    public void testAColumnOfAStarQueryIsDeclaredByTheStar() throws Exception {
        open("bronze/bronze_customers.sqlx");
        PsiElement declaration = ColumnOriginService.getInstance(getProject())
                .declaringElement(new ColumnRef("proj.ds.bronze_customers", "customer_id"));
        assertNotNull("a column of a star query must still have a declaration", declaration);
        assertEquals("*", declaration.getText());
    }

    public void testUnknownColumnResolvesToNothing() {
        ColumnOriginService service = ColumnOriginService.getInstance(getProject());
        assertNull(service.dasColumn(new ColumnRef("proj.ds.silver_orders", "nope")));
        assertNull(service.declaringElement(new ColumnRef("proj.ds.nope", "order_id")));
        assertTrue(service.origins(new ColumnRef("proj.ds.nope", "order_id")).isEmpty());
    }
}
