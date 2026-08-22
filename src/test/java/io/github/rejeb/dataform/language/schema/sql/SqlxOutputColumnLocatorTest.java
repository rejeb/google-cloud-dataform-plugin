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
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A column of a Dataform table is declared by the select list of the main query. The fixtures are
 * the layered project the plugin is developed against: a bronze table selecting bare columns over
 * a {@code FROM UNNEST} of structs, a silver table behind pre/post operations, and a gold table
 * whose output columns come from the query following a CTE.
 */
public class SqlxOutputColumnLocatorTest extends BasePlatformTestCase {

    private static final String FIXTURES = "src/test/resources/projects/user_purchase/definitions/";

    private PsiFile open(String path) throws IOException {
        String text = Files.readString(Path.of(FIXTURES + path));
        String name = path.substring(path.lastIndexOf('/') + 1);
        PsiFile file = myFixture.addFileToProject("definitions/" + name, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    /** The one-based line of the declaration found for a column, or -1 when none was found. */
    private int declarationLine(PsiFile file, String column) {
        PsiElement declaration = SqlxOutputColumnLocator.findOutputColumn(file, column);
        if (declaration == null) return -1;
        int hostOffset = InjectedLanguageManager.getInstance(getProject())
                .injectedToHost(declaration, declaration.getTextOffset());
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        return document.getLineNumber(hostOffset) + 1;
    }

    private void assertDeclaredAt(PsiFile file, String column, int expectedLine) {
        assertEquals("'" + column + "' must be declared by the select list of the main query",
                expectedLine, declarationLine(file, column));
    }

    public void testBronzeColumnsResolveToTheSelectListNotTheUnnestedStructs() throws IOException {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        assertDeclaredAt(bronze, "order_id", 23);
        assertDeclaredAt(bronze, "customer_id", 24);
        assertDeclaredAt(bronze, "order_ts", 25);
    }

    public void testBronzeAliasedColumnResolvesToItsAlias() throws IOException {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        assertDeclaredAt(bronze, "order_status", 26);
        assertDeclaredAt(bronze, "order_status_raw", 27);
    }

    public void testBronzeIgnoresNamesThatOnlyExistInTheFromClause() throws IOException {
        PsiFile bronze = open("bronze/bronze_orders.sqlx");
        assertEquals("'raw_status' is an input of the query, not a column of the table",
                -1, declarationLine(bronze, "raw_status"));
    }

    public void testSilverColumnsResolveAcrossOperationBlocks() throws IOException {
        PsiFile silver = open("silver/silver_orders.sqlx");
        assertDeclaredAt(silver, "order_id", 23);
        assertDeclaredAt(silver, "customer_id", 24);
        assertDeclaredAt(silver, "order_ts", 25);
        assertDeclaredAt(silver, "order_status", 26);
    }

    public void testGoldColumnsResolveToTheFinalSelectNotTheCte() throws IOException {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        assertDeclaredAt(gold, "order_id", 31);
        assertDeclaredAt(gold, "customer_id", 30);
        assertDeclaredAt(gold, "lifetime_value", 39);
        assertDeclaredAt(gold, "orders_count", 40);
    }

    public void testGoldIgnoresColumnsThatOnlyExistInTheCte() throws IOException {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        assertEquals("'order_amount' is read by the CTE and is not a column of the gold table",
                -1, declarationLine(gold, "order_amount"));
    }

    /**
     * A query written {@code SELECT *} names no column, so every column it builds is declared by
     * the star: that is the line a reader has to look at to see where the column came from.
     */
    public void testAStarDeclaresEveryColumnOfTheQuery() {
        PsiFile file = myFixture.configureByText("star_table.sqlx",
                "config { type: \"table\" }\nSELECT * FROM `proj.ds.bronze_orders`");
        PsiElement declaration = SqlxOutputColumnLocator.findOutputColumn(file, "order_id");
        assertNotNull("a column of a star query must still have a declaration", declaration);
        assertEquals("*", declaration.getText());
    }

    public void testAQualifiedStarDeclaresThemToo() {
        PsiFile file = myFixture.configureByText("star_alias.sqlx",
                "config { type: \"table\" }\nSELECT bo.* FROM `proj.ds.bronze_orders` bo");
        PsiElement declaration = SqlxOutputColumnLocator.findOutputColumn(file, "order_id");
        assertNotNull("a qualified star declares the columns as much as a bare one", declaration);
        assertEquals("bo.*", declaration.getText());
    }

    public void testANamedItemStillWinsOverAStar() {
        PsiFile file = myFixture.configureByText("star_mixed.sqlx",
                "config { type: \"table\" }\n"
                        + "SELECT *, UPPER(country) AS country_code FROM `proj.ds.bronze_orders`");
        PsiElement declaration = SqlxOutputColumnLocator.findOutputColumn(file, "country_code");
        assertNotNull(declaration);
        assertEquals("a column the query names has a line of its own, star or not",
                "country_code", declaration.getText());
    }

    public void testAStarDoesNotClaimToDeclareANameItself() {
        PsiFile file = myFixture.configureByText("star_name.sqlx",
                "config { type: \"table\" }\nSELECT * FROM `proj.ds.bronze_orders`");
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, file.getText().indexOf("*"));
        assertNotNull(injected);
        assertNull("a star names no column, so it declares none by name",
                SqlxOutputColumnLocator.declaredColumnName(file, selectItemOf(injected)));
    }

    /** The name declared by the select-list item holding a token, through the reverse lookup. */
    private @Nullable String declaredNameAt(PsiFile file, int line, String token) {
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        int lineStart = document.getLineStartOffset(line - 1);
        String lineText = document.getText()
                .substring(lineStart, document.getLineEndOffset(line - 1));
        int column = lineText.indexOf(token);
        assertTrue("token '" + token + "' must be on line " + line, column >= 0);
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(file, lineStart + column + token.length() - 1);
        assertNotNull("the token must be inside the injected SQL", injected);
        return SqlxOutputColumnLocator.declaredColumnName(file, selectItemOf(injected));
    }

    /**
     * The reference a token belongs to, which is the element the resolve extension is handed. The
     * token itself is a leaf inside an identifier inside the reference.
     */
    private static PsiElement selectItemOf(PsiElement token) {
        PsiElement item = token;
        while (item.getParent() != null && isPartOfReference(item.getParent())) {
            item = item.getParent();
        }
        return item;
    }

    private static boolean isPartOfReference(PsiElement element) {
        IElementType type = element.getNode().getElementType();
        return type == SqlCompositeElementTypes.SQL_IDENTIFIER
                || type == SqlCompositeElementTypes.SQL_COLUMN_REFERENCE;
    }

    public void testBareSelectItemDeclaresItsOwnName() throws IOException {
        PsiFile silver = open("silver/silver_orders.sqlx");
        assertEquals("order_id", declaredNameAt(silver, 23, "order_id"));
    }

    public void testQualifiedSelectItemDeclaresItsLastIdentifier() throws IOException {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        assertEquals("order_id", declaredNameAt(gold, 31, "co.order_id"));
    }

    public void testReferenceInsideTheCteDeclaresNothing() throws IOException {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        assertNull("a select item of the CTE is not an output column of the table",
                declaredNameAt(gold, 17, "o.order_id"));
    }

    public void testReferenceNestedInAFunctionDeclaresNothing() throws IOException {
        PsiFile gold = open("gold/gold_customer_ltv.sqlx");
        assertNull("a reference inside COALESCE is not itself a select item",
                declaredNameAt(gold, 39, "co.lifetime_value"));
    }
}
