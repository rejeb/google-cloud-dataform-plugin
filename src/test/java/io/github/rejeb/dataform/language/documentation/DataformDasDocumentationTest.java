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
package io.github.rejeb.dataform.language.documentation;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.List;

public class DataformDasDocumentationTest extends BasePlatformTestCase {

    public void testColumnElementProducesTarget() {
        DataformDasTable table = new DataformDasTable(getPsiManager(), "orders",
                List.of(new ColumnInfo("amount", "NUMERIC", "NULLABLE", "Order total")), null);
        DataformDasColumn column = new DataformDasColumn(getPsiManager(), table,
                new ColumnInfo("amount", "NUMERIC", "NULLABLE", "Order total"), null);

        DocumentationTarget target =
                new DataformDasDocumentationTargetProvider().documentationTarget(column, column);

        assertNotNull(target);
        assertEquals("amount", target.computePresentation().getPresentableText());
    }

    public void testTableElementProducesTarget() {
        DataformDasTable table = new DataformDasTable(getPsiManager(), "orders", List.of(), null);

        DocumentationTarget target =
                new DataformDasDocumentationTargetProvider().documentationTarget(table, table);

        assertNotNull(target);
        assertEquals("orders", target.computePresentation().getPresentableText());
    }

    public void testUnrelatedElementProducesNoTarget() {
        PsiFile file = myFixture.configureByText("test.sqlx", "config { type: \"table\" }\nSELECT 1");
        assertNull(new DataformDasDocumentationTargetProvider().documentationTarget(file, file));
    }

    public void testColumnDocumentationContainsTypeAndDescription() {
        DataformDasTable table = new DataformDasTable(getPsiManager(), "orders", List.of(), null);
        DataformDasColumn column = new DataformDasColumn(getPsiManager(), table,
                new ColumnInfo("amount", "NUMERIC", "REPEATED", "Order total"), null);

        DocumentationTarget target =
                new DataformDasDocumentationTargetProvider().documentationTarget(column, column);

        assertNotNull(target);
        assertNotNull(target.computeDocumentation());
    }

    public void testColumnInfoAccessorIsExposed() {
        ColumnInfo info = new ColumnInfo("amount", "NUMERIC", "NULLABLE", "Order total");
        DataformDasColumn column = new DataformDasColumn(getPsiManager(), null, info, null);
        assertEquals(info, column.getColumnInfo());
    }
}
