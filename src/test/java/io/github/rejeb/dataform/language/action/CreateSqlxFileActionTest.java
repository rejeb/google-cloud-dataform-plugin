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
package io.github.rejeb.dataform.language.action;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.SqlxFileType;

public class CreateSqlxFileActionTest extends BasePlatformTestCase {

    public void testEveryKindIsBackedByASqlxInternalTemplate() {
        FileTemplateManager manager = FileTemplateManager.getInstance(getProject());

        for (SqlxFileTemplate kind : SqlxFileTemplate.values()) {
            FileTemplate template = manager.findInternalTemplate(kind.getTemplateName());

            assertNotNull("Missing internal template " + kind.getTemplateName(), template);
            assertEquals(SqlxFileType.EXTENSION, template.getExtension());
            assertTrue(template.getText().contains("config {"));
        }
    }

    public void testCreateTableFileFromTemplate() {
        PsiFile created = createFromTemplate(SqlxFileTemplate.TABLE, "daily_orders");

        assertEquals("daily_orders.sqlx", created.getName());
        assertEquals(SqlxFileType.INSTANCE, created.getFileType());
        assertTrue(created.getText().contains("type: \"table\""));
    }

    public void testDeclarationTemplateUsesFileNameAsTableName() {
        PsiFile created = createFromTemplate(SqlxFileTemplate.DECLARATION, "raw_customers");

        assertTrue(created.getText().contains("name: \"raw_customers\""));
    }

    public void testDataformTemplateExpressionsAreNotEvaluatedByVelocity() {
        PsiFile assertion = createFromTemplate(SqlxFileTemplate.ASSERTION, "orders_not_null");
        PsiFile incremental = createFromTemplate(SqlxFileTemplate.INCREMENTAL, "orders_incremental");

        assertTrue(assertion.getText().contains("${ref(\"my_table\")}"));
        assertTrue(incremental.getText().contains("${when(incremental()"));
        assertTrue(incremental.getText().contains("${self()}"));
    }

    private PsiFile createFromTemplate(SqlxFileTemplate kind, String fileName) {
        PsiFile anchor = myFixture.addFileToProject("definitions/" + fileName + ".txt", "");
        PsiDirectory directory = anchor.getContainingDirectory();
        FileTemplate template = FileTemplateManager.getInstance(getProject())
                .getInternalTemplate(kind.getTemplateName());

        PsiFile created = CreateFileFromTemplateAction
                .createFileFromTemplate(fileName, template, directory, null, false);

        assertNotNull(created);
        return created;
    }
}
