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

import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;

/**
 * Rename is not implemented. Until it is, renaming a column must refuse rather than rewrite one
 * occurrence and leave the project inconsistent: a column of a Dataform table is read by every
 * downstream action, so a rename that touches a single file produces a project that no longer
 * compiles. When rename is built, this test is the one that changes deliberately.
 */
public class ColumnRenameRefusedTest extends DataformProjectFixture {

    public void testSettingTheNameOfASchemaColumnIsRefused() throws Exception {
        open("bronze/bronze_orders.sqlx");
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));
        assertNotNull(column);
        try {
            column.setName("renamed");
            fail("renaming a schema column must be refused while rename is unimplemented");
        } catch (IncorrectOperationException expected) {
            // expected
        }
    }

    public void testRefusedRenameLeavesTheFileUnchanged() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        String before = bronze.getText();
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));
        assertNotNull(column);
        try {
            column.setName("renamed");
        } catch (IncorrectOperationException ignored) {
            // expected
        }
        assertEquals("a refused rename must not modify the file", before, bronze.getText());
    }

    /**
     * The navigation element is a real, renameable SQL identifier. Renaming through it would
     * rewrite that one occurrence and nothing else, so the column's own declaration stays the
     * only renameable identity and it refuses.
     */
    public void testNavigationElementDoesNotOfferAWayAround() throws Exception {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        String before = bronze.getText();
        DataformDasColumn column = ColumnOriginService.getInstance(getProject())
                .dasColumn(new ColumnRef("proj.ds.bronze_orders", "order_id"));
        assertNotNull(column);
        assertNotSame("the navigation element is real source", column, column.getNavigationElement());
        try {
            column.setName("renamed");
        } catch (IncorrectOperationException ignored) {
            // expected
        }
        assertEquals("no path through the column may rewrite the file", before, bronze.getText());
    }
}
