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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * A table keeps the source file it was built with until the next schema refresh, and the file may
 * be deleted or replaced in the meantime.
 */
public class DataformDasTableSourceFileTest extends BasePlatformTestCase {

    private static final String PATH = "definitions/bronze/bronze_customers.sqlx";
    private static final List<ColumnInfo> COLUMNS =
            List.of(new ColumnInfo("customer_id", "INT64", "NULLABLE", null));

    private DataformDasTable tableOf(VirtualFile source) {
        return new DataformDasTable(getPsiManager(), "proj.ds.bronze_customers", "bronze_customers", COLUMNS, source);
    }

    private void delete(VirtualFile file) throws Exception {
        WriteAction.runAndWait(() -> file.delete(this));
    }

    public void testTheColumnsOfATableWhoseSourceWasDeletedAreStillRead() throws Exception {
        VirtualFile source = myFixture.addFileToProject(PATH, "SELECT 1 AS customer_id\n").getVirtualFile();
        DataformDasTable table = tableOf(source);
        delete(source);

        List<? extends DasObject> columns = table.getDasChildren(ObjectKind.COLUMN).toList();

        assertEquals(1, columns.size());
        assertNotNull(table.getContainingFile());
        assertFalse(table.canNavigate());
    }

    public void testATableWhoseSourceWasReplacedReadsTheNewFile() throws Exception {
        VirtualFile source = myFixture.addFileToProject(PATH, "SELECT 1 AS customer_id\n").getVirtualFile();
        DataformDasTable table = tableOf(source);
        delete(source);
        PsiFile replacement = myFixture.addFileToProject(PATH, "SELECT 2 AS customer_id\n");

        assertEquals(replacement, table.getContainingFile());
        assertTrue(table.canNavigate());
    }

    public void testAColumnOfADeletedFileStaysUsableButCannotNavigate() throws Exception {
        VirtualFile source = myFixture.addFileToProject(PATH, "config { type: \"table\" }\nSELECT 1 AS customer_id\n").getVirtualFile();
        DasObject column = tableOf(source).getDasChildren(ObjectKind.COLUMN).first();
        assertTrue(column instanceof DataformDasColumn);
        DataformDasColumn dasColumn = (DataformDasColumn) column;
        assertTrue(dasColumn.isValid());
        delete(source);

        assertTrue(dasColumn.isValid());
        assertSame(dasColumn, dasColumn.getNavigationElement());
        assertFalse(dasColumn.canNavigate());
    }
}
