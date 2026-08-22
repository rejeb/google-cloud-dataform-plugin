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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Navigating a schema column must land on the select-list item declaring it, not on the first
 * mention of the name anywhere in the file.
 */
public class DataformDasColumnNavigationTest extends BasePlatformTestCase {

    private static final String FIXTURES = "src/test/resources/projects/user_purchase/definitions/";

    private PsiFile open(String path) throws IOException {
        String text = Files.readString(Path.of(FIXTURES + path));
        String name = path.substring(path.lastIndexOf('/') + 1);
        PsiFile file = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    /** The one-based line the caret lands on after navigating to a column of the file. */
    private int navigatedLine(PsiFile file, String column) {
        new DataformDasColumn(getPsiManager(), null,
                new ColumnInfo(column, "STRING", "NULLABLE", null), file).navigate(true);
        Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        assertNotNull("navigation must open an editor", editor);
        return editor.getCaretModel().getLogicalPosition().line + 1;
    }

    public void testBronzeOrderIdLandsOnTheSelectListNotTheUnnestedStruct() throws IOException {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        assertEquals("order_id must land on the select list, not on the STRUCT alias of the UNNEST",
                23, navigatedLine(bronze, "order_id"));
    }

    public void testSilverOrderIdLandsOnTheSelectListDespiteOperationBlocks() throws IOException {
        PsiFile silver = open("silver/silver_orders.sqlx");
        assertEquals("order_id must land on the select list of the main query",
                23, navigatedLine(silver, "order_id"));
    }

    public void testGoldOrderIdLandsOnTheFinalSelectNotTheCte() throws IOException {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        assertEquals("order_id must land on the final select list, not on the CTE",
                31, navigatedLine(gold, "order_id"));
    }

    public void testNavigationElementIsTheSelectListElement() throws IOException {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        DataformDasColumn column = new DataformDasColumn(getPsiManager(), null,
                new ColumnInfo("order_id", "STRING", "NULLABLE", null), bronze);
        PsiElement navigation = column.getNavigationElement();
        assertNotSame("the navigation element must be real source, not the synthetic column",
                column, navigation);
        assertEquals("order_id", navigation.getText());
    }

    public void testNavigationElementFallsBackToItselfWhenNotDeclared() throws IOException {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        DataformDasColumn column = new DataformDasColumn(getPsiManager(), null,
                new ColumnInfo("not_a_column", "STRING", "NULLABLE", null), bronze);
        assertSame("an undeclared column has no source to navigate to",
                column, column.getNavigationElement());
    }
}
