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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class DataformExpressionCollectorTest extends BasePlatformTestCase {

    public void testSqlxTemplatesAreCollectedFromSqlAndOperationsBlocks() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", """
                config { type: "table" }

                pre_operations {
                  DELETE FROM ${self()}
                }

                SELECT * FROM ${ref("users")} WHERE env = '${dataform.projectConfig.vars.env}'
                """);

        List<DataformExpression> expressions = DataformExpressionCollector.collectSqlxTemplates(file);

        assertEquals("got " + expressions, 3, expressions.size());
        assertEquals("self()", expressions.get(0).source());
        assertEquals("ref(\"users\")", expressions.get(1).source());
        assertEquals("dataform.projectConfig.vars.env", expressions.get(2).source());
        assertEquals(DataformExpressionKind.SQLX_TEMPLATE, expressions.getFirst().kind());
    }

    public void testHostRangeCoversTheTemplateDelimiters() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")}\n");

        DataformExpression expression = DataformExpressionCollector.collectSqlxTemplates(file).getFirst();

        assertEquals("${ref(\"users\")}", expression.hostText());
        assertEquals("${ref(\"users\")}",
                file.getText().substring(expression.hostRange().getStartOffset(),
                        expression.hostRange().getEndOffset()));
    }

    public void testConfigBlockContentIsNotCollectedAsTemplate() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx",
                "config { type: \"table\", schema: `${\"a\"}` }\n\nSELECT 1 AS one\n");

        assertEmpty(DataformExpressionCollector.collectSqlxTemplates(file));
    }

    public void testJsTemplateSubstitutionRangeIncludesDelimiters() {
        PsiFile file = myFixture.addFileToProject("definitions/mart.js",
                "publish(\"mart\").query(ctx => `SELECT * FROM ${ctx.ref(\"users\")}`);\n");

        List<DataformExpression> expressions =
                DataformExpressionCollector.collectJsTemplateSubstitutions(file);

        assertEquals("got " + expressions, 1, expressions.size());
        DataformExpression expression = expressions.getFirst();
        assertEquals("ctx.ref(\"users\")", expression.source());
        assertEquals(DataformExpressionKind.JS_TEMPLATE_SUBSTITUTION, expression.kind());
        assertEquals("${ctx.ref(\"users\")}",
                file.getText().substring(expression.hostRange().getStartOffset(),
                        expression.hostRange().getEndOffset()));
    }

    public void testNonDefinitionJsFileIsIgnored() {
        PsiFile file = myFixture.addFileToProject("includes/helpers.js",
                "const x = `value ${1 + 1}`;\n");

        assertEmpty(DataformExpressionCollector.collectJsTemplateSubstitutions(file));
    }
}
