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
package io.github.rejeb.dataform.language.refactoring.column.target;

import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.DataformProjectFixture;

import java.util.Optional;

/**
 * Which column a rename gesture is about, decided from where the caret is.
 */
public class ColumnRenameSubjectFactoryTest extends DataformProjectFixture {

    private Optional<ColumnRenameSubject> subjectAt(PsiFile file, String token) {
        int offset = file.getText().indexOf(token);
        assertTrue("the fixture must contain " + token, offset >= 0);
        return ColumnRenameSubjectFactory.at(file, offset + 1);
    }

    public void testAnAliasOfTheMainSelectListNamesTheColumnItDeclares() throws Exception {
        PsiFile file = open("bronze/bronze_orders.sqlx");

        ColumnRenameSubject subject = subjectAt(file, "order_status_raw").orElseThrow();

        assertEquals("order_status_raw", subject.column().columnName());
        assertEquals("proj.ds.bronze_orders", subject.column().tableFullName());
        assertEquals(ColumnRenameSubject.Kind.SELECT_ALIAS, subject.kind());
        assertTrue("the file declares the column", subject.declaresColumn());
    }

    public void testABareSelectItemNamesTheColumnItDeclares() throws Exception {
        PsiFile file = open("silver/silver_orders.sqlx");

        ColumnRenameSubject subject = subjectAt(file, "order_id ,").orElseThrow();

        assertEquals("order_id", subject.column().columnName());
        assertEquals("proj.ds.silver_orders", subject.column().tableFullName());
        assertEquals(ColumnRenameSubject.Kind.SELECT_ITEM, subject.kind());
    }

    public void testAReadNamesTheColumnItResolvesTo() throws Exception {
        open("silver/silver_orders.sqlx");
        PsiFile file = open("gold/gold_customer_ltv.sqlx");

        ColumnRenameSubject subject = subjectAt(file, "order_status = 'PAID'")
                .orElseThrow(() -> new AssertionError("a read of a Dataform column is renameable"));

        assertEquals("order_status", subject.column().columnName());
        assertEquals(ColumnRenameSubject.Kind.READ, subject.kind());
        assertFalse("a read declares nothing", subject.declaresColumn());
    }

    public void testATableAliasIsNotAColumn() throws Exception {
        PsiFile file = open("gold/gold_customer_ltv.sqlx");

        assertTrue("a table alias is left to the SQL plugin",
                subjectAt(file, "AS o\n").isEmpty());
    }

    public void testAnAliasOfACommonTableExpressionIsNotAColumnOfTheTable() throws Exception {
        PsiFile file = open("gold/gold_customer_ltv.sqlx");

        assertTrue("a name local to a common table expression is not a column of the action",
                subjectAt(file, "AS orders_count").isEmpty());
    }

    public void testAnOffsetOutsideAColumnMeansNothing() throws Exception {
        PsiFile file = open("silver/silver_orders.sqlx");

        assertTrue(subjectAt(file, "SELECT").isEmpty());
    }
}
