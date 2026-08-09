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
package io.github.rejeb.dataform.language.validation;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class UnresolvedReferenceValidatorTest extends BasePlatformTestCase {

    public void testUnresolvedRefIsReportedAtTheLiteralRange() {
        PsiFile file = myFixture.configureByText("a.sqlx", """
                config { type: "table" }

                SELECT * FROM ${ref("does_not_exist")}
                """);
        List<SqlxValidationProblem> problems =
                new UnresolvedReferenceValidator().validate(file);

        assertSize(1, problems);
        SqlxValidationProblem problem = problems.get(0);
        assertEquals(SqlxValidationProblem.Kind.UNRESOLVED_REFERENCE, problem.kind());
        assertTrue(problem.message().contains("does_not_exist"));

        String covered = file.getText().substring(
                problem.range().getStartOffset(), problem.range().getEndOffset());
        assertEquals("does_not_exist", covered);
    }

    public void testProblemLandsOnTheCorrectLineWhenAnEarlierLineHasAnExpression() {
        PsiFile file = myFixture.configureByText("gold_daily_sales.sqlx", """
                config { type: "table" }

                SELECT
                    '${constants.CURRENCY_DEFAULT}' AS currency,
                    o.order_status
                FROM ${ref("silver_orders")} AS o
                INNER JOIN ${ref("gold_customer_purchase_summary")} AS s
                    ON o.order_id = s.order_id
                """);
        List<SqlxValidationProblem> problems =
                new UnresolvedReferenceValidator().validate(file);

        int joinLineStart = file.getText().indexOf("INNER JOIN");
        boolean anyOnJoinLine = problems.stream()
                .anyMatch(p -> p.range().getStartOffset() > joinLineStart);
        assertTrue("expected a problem on the INNER JOIN line, got " + problems, anyOnJoinLine);
    }

    public void testFileWithNoReferencesYieldsNoProblems() {
        PsiFile file = myFixture.configureByText("b.sqlx", "SELECT 1\n");
        assertEmpty(new UnresolvedReferenceValidator().validate(file));
    }

    public void testNonSqlxFileYieldsNoProblems() {
        PsiFile file = myFixture.configureByText("notes.txt", "ref(\"x\")\n");
        assertEmpty(new UnresolvedReferenceValidator().validate(file));
    }
}
