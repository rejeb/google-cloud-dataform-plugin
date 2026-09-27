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
package io.github.rejeb.dataform.language.unittest.completion;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class TestBlockPsiShapeTest extends BasePlatformTestCase {

    private static final String DUMMY = CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED;

    private PsiElement dummyIn(String sql) {
        PsiFile host = myFixture.configureByText("t_test.sqlx",
                "config {\n  type: \"test\",\n  dataset: \"orders\"\n}\n\n" + sql + "\n");
        int offset = host.getText().indexOf(DUMMY);
        PsiElement leaf = InjectedLanguageManager.getInstance(getProject()).findInjectedElementAt(host, offset);
        assertNotNull("no injected leaf in\n" + host.getText(), leaf);
        return leaf;
    }

    private static PsiElement ancestor(PsiElement element, String simpleName) {
        for (PsiElement e = element; e != null && !(e instanceof PsiFile); e = e.getParent()) {
            if (e.getClass().getSimpleName().equals(simpleName)) return e;
        }
        return null;
    }

    private static String dump(PsiElement element) {
        return DebugUtil.psiToString(element.getContainingFile(), true);
    }

    public void testDummyAfterAsIsTheAliasOfAnAsExpressionDirectlyUnderTheSelectClause() {
        PsiElement leaf = dummyIn("SELECT 1 AS a, 2 AS " + DUMMY);
        PsiElement as = ancestor(leaf, "BigQueryAsExpressionImpl");
        assertNotNull(dump(leaf), as);
        assertEquals(dump(leaf), "SqlSelectClauseImpl", as.getParent().getClass().getSimpleName());
    }

    public void testStructFieldSitsInAParenthesizedExpressionUnderTheItemAsExpression() {
        PsiElement leaf = dummyIn("SELECT STRUCT('x' AS " + DUMMY + ") AS address");
        PsiElement field = ancestor(leaf, "BigQueryAsExpressionImpl");
        assertNotNull(dump(leaf), field);
        PsiElement struct = ancestor(field.getParent(), "BigQueryParenthesizedExpression");
        assertNotNull(dump(leaf), struct);
        PsiElement item = ancestor(struct.getParent(), "BigQueryAsExpressionImpl");
        assertNotNull(dump(leaf), item);
        assertTrue(dump(leaf), item.getText().endsWith("address"));
    }

    public void testArrayOfStructsReachesTheItemAsExpression() {
        PsiElement leaf = dummyIn("SELECT [STRUCT('x' AS " + DUMMY + ")] AS items");
        PsiElement field = ancestor(leaf, "BigQueryAsExpressionImpl");
        PsiElement struct = ancestor(field.getParent(), "BigQueryParenthesizedExpression");
        assertNotNull(dump(leaf), struct);
        PsiElement item = ancestor(struct.getParent(), "BigQueryAsExpressionImpl");
        assertNotNull(dump(leaf), item);
        assertTrue(dump(leaf), item.getText().endsWith("items"));
    }

    public void testTypedArrayOfStructsReachesTheItemAsExpression() {
        PsiElement leaf = dummyIn("SELECT ARRAY<STRUCT<sku STRING>>[STRUCT('x' AS " + DUMMY + ")] AS items");
        PsiElement field = ancestor(leaf, "BigQueryAsExpressionImpl");
        PsiElement struct = ancestor(field.getParent(), "BigQueryParenthesizedExpression");
        assertNotNull(dump(leaf), struct);
        PsiElement item = ancestor(struct.getParent(), "BigQueryAsExpressionImpl");
        assertNotNull(dump(leaf), item);
        assertTrue(dump(leaf), item.getText().endsWith("items"));
    }

    public void testSelectAsStructInsideArrayParsesFlatWithoutAsExpressionForItsFields() {
        PsiElement leaf = dummyIn("SELECT ARRAY(SELECT AS STRUCT 'x' AS " + DUMMY + ") AS items");
        PsiElement reference = leaf.getParent().getParent();
        assertEquals(dump(leaf), "SQL_COLUMN_REFERENCE", reference.getNode().getElementType().toString());
        assertEquals(dump(leaf), "SQL_ARGUMENT_LIST", reference.getParent().getNode().getElementType().toString());
        PsiElement item = ancestor(reference, "BigQueryAsExpressionImpl");
        assertNotNull(dump(leaf), item);
        assertTrue(dump(leaf), item.getText().endsWith("items"));
    }
}
