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
package io.github.rejeb.dataform.language.injection;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;

/**
 * What a template hole becomes in the SQL the IDE analyses: the value Node evaluated it to when
 * one is known, and otherwise filler that keeps the query parsable.
 */
public class SqlxSqlBlockInjectorTest extends BasePlatformTestCase {

    private PsiFile sqlx(String body) {
        PsiFile file = myFixture.addFileToProject("definitions/action.sqlx",
                "config { type: \"table\" }\n" + body);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return myFixture.getFile();
    }

    private void seed(PsiFile file, String source, String value) {
        ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService
                .getInstance(getProject())).putCachedValue(file.getVirtualFile(), source, value);
    }

    private String injectedSql(PsiFile file) {
        SqlxSqlBlock block = PsiTreeUtil.findChildOfType(file, SqlxSqlBlock.class);
        assertNotNull("the file must hold a SQL block", block);
        return InjectedFiles.of(java.util.List.of(block)).getFirst().getText();
    }

    public void testAnEvaluatedHoleIsInjectedAsItsValue() {
        PsiFile file = sqlx("SELECT ${helpers.top_value(\"order_id\")} AS top_order FROM t");
        seed(file, "helpers.top_value(\"order_id\")", "MAX(order_id)");
        assertEquals("SELECT MAX(order_id) AS top_order FROM t", injectedSql(file));
    }

    public void testAHoleWithoutAValueIsStillFillerInsideAnExpression() {
        PsiFile file = sqlx("SELECT ${helpers.top_value(\"order_id\")} AS top_order FROM t");
        assertEquals("SELECT NULL AS top_order FROM t", injectedSql(file));
    }

    public void testAHoleOpeningTheQueryIsInjectedAsNothingUntilItHasAValue() {
        PsiFile file = sqlx("${when(incremental(), \"-- incremental\")}\nSELECT 1 AS one FROM t");
        assertEquals("\nSELECT 1 AS one FROM t", injectedSql(file));
        SqlxSqlBlock block = PsiTreeUtil.findChildOfType(file, SqlxSqlBlock.class);
        PsiFile sql = InjectedFiles.of(java.util.List.of(block)).getFirst();
        assertEmpty("a query opened by a hole parses cleanly",
                PsiTreeUtil.findChildrenOfType(sql, PsiErrorElement.class));
    }

    public void testAWholeQueryHoleIsInjectedAsTheQueryItEvaluatesTo() {
        PsiFile file = sqlx("${helpers.build_query()}");
        seed(file, "helpers.build_query()", "SELECT 1 AS one FROM t");
        assertEquals("SELECT 1 AS one FROM t", injectedSql(file));
    }

    public void testARefHoleKeepsResolvingThroughTheCompiledGraph() {
        PsiFile file = sqlx("SELECT * FROM ${ref(\"orders\")}");
        assertEquals("SELECT * FROM NULL", injectedSql(file));
    }

    /**
     * Node answers after the file was first injected, and the platform keeps the injection it
     * already built. The refresher is what makes the new value reach the SQL.
     */
    public void testAValueArrivingAfterTheFirstInjectionReachesTheSqlOnceRefreshed() {
        PsiFile file = sqlx("SELECT ${helpers.top_value(\"order_id\")} AS top_order FROM t");
        assertEquals("SELECT NULL AS top_order FROM t", injectedSql(file));
        seed(file, "helpers.top_value(\"order_id\")", "MAX(order_id)");
        SqlxInjectionRefresher.refresh(getProject(), file.getVirtualFile());
        assertEquals("SELECT MAX(order_id) AS top_order FROM t", injectedSql(file));
    }
}
