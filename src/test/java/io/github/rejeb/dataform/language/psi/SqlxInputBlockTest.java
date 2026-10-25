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
package io.github.rejeb.dataform.language.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.dialects.bigquery.BigQueryDialect;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.columns.origin.SqlxOutputColumnLocator;

import java.util.List;

public class SqlxInputBlockTest extends BasePlatformTestCase {

    private static final String TEST_FILE = """
            config {
              type: "test",
              dataset: "orders"
            }

            input "raw", "src" {
              SELECT 1 AS id
            }

            SELECT 2 AS id
            """;

    private PsiFile sqlx(String text) {
        return myFixture.addFileToProject("definitions/orders_test.sqlx", text);
    }

    public void testAnInputBlockExposesItsLabelAndBody() {
        SqlxInputBlock input = PsiTreeUtil.findChildOfType(sqlx(TEST_FILE), SqlxInputBlock.class);

        assertNotNull(input);
        assertEquals(List.of("raw", "src"), input.labelParts());
        assertNotNull(input.content());
        assertEquals("SELECT 1 AS id", input.content().getText());
    }

    public void testTheBodyIsInjectedAsBigQuery() {
        SqlxInputBlock input = PsiTreeUtil.findChildOfType(sqlx(TEST_FILE), SqlxInputBlock.class);
        PsiFile sql = InjectedFiles.of(List.of(input.content())).getFirst();

        assertEquals(BigQueryDialect.INSTANCE, sql.getLanguage());
        assertEquals("SELECT 1 AS id", sql.getText());
    }

    public void testTheExpectedOutputStaysTheMainQuery() {
        PsiElement main = SqlxOutputColumnLocator.mainQuery(sqlx(TEST_FILE));

        assertNotNull(main);
        assertTrue(main.getText(), main.getText().contains("2 AS id"));
    }

    public void testAnInputWithoutBodyKeepsTheQuery() {
        PsiFile file = sqlx("input \"src\"\nSELECT 1\n");

        assertNotNull(PsiTreeUtil.findChildOfType(file, SqlxInputBlock.class));
        assertTrue(PsiTreeUtil.findChildrenOfType(file, SqlxSqlBlock.class).stream()
                .anyMatch(block -> block.getNode().getElementType() == SharedTokenTypes.SQL_CONTENT
                        && block.getText().startsWith("SELECT 1")));
    }

    public void testAnEditOfTheInputBodyKeepsItAnInputBody() {
        PsiFile file = sqlx(TEST_FILE);
        SqlxSqlBlock body = PsiTreeUtil.findChildOfType(file, SqlxInputBlock.class).content();

        SqlxSqlBlock edited = WriteCommandAction.writeCommandAction(getProject()).compute(() ->
                ElementManipulators.handleContentChange(body, "SELECT 3 AS id"));

        assertEquals(SharedTokenTypes.INPUT_CONTENT, edited.getNode().getElementType());
        assertEquals("SELECT 3 AS id", edited.getText());
        assertInstanceOf(edited.getParent(), SqlxInputBlock.class);
    }

    public void testAnEditOfPreOperationsKeepsThemPreOperations() {
        PsiFile file = sqlx("pre_operations {\n  DECLARE x INT64\n}\n\nSELECT 1\n");
        SqlxSqlBlock body = PsiTreeUtil.findChildrenOfType(file, SqlxSqlBlock.class).stream()
                .filter(block -> block.getNode().getElementType() == SharedTokenTypes.PRE_OPERATIONS_CONTENT)
                .findFirst().orElseThrow();

        SqlxSqlBlock edited = WriteCommandAction.writeCommandAction(getProject()).compute(() ->
                ElementManipulators.handleContentChange(body, "DECLARE y INT64"));

        assertEquals(SharedTokenTypes.PRE_OPERATIONS_CONTENT, edited.getNode().getElementType());
    }
}
