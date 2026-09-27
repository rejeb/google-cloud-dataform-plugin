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
package io.github.rejeb.dataform.language.unittest.columns;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.diagnostics.compile.ConfigSchemaFixture;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationServiceImpl;
import io.github.rejeb.dataform.language.evaluation.DataformTemplateSyntax;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.psi.SqlxInputBlock;
import io.github.rejeb.dataform.language.psi.SqlxJsLiteralExpression;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import io.github.rejeb.dataform.language.refactoring.column.ColumnRenameFixture;
import io.github.rejeb.dataform.language.unittest.schema.TestBlockKind;

import java.util.List;
import java.util.Set;

public class TestColumnAliasesTest extends ColumnRenameFixture {

    private static final ColumnRef SRC_NAME = new ColumnRef("p.d.src", "full_name");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigSchemaFixture.install(getProject(), getTestRootDisposable());
        installProject(
                new Action("src", "SELECT 'a' AS full_name, STRUCT('p' AS city) AS address", List.of(),
                        List.of("full_name", "address")),
                new Action("fin", "SELECT full_name FROM `p.d.src`", List.of("src"), List.of("full_name")));
        addFile("src", "config { type: \"table\" }\n\nSELECT 'a' AS full_name\n");
        addFile("fin", "config { type: \"table\" }\n\nSELECT\n    full_name\nFROM ${ref(\"src\")}\n");
    }

    private TestColumnAliases aliases() {
        return TestColumnAliases.getInstance(getProject());
    }

    private static String testText(String inputBody, String expected) {
        return "config {\n  type: \"test\",\n  dataset: \"fin\"\n}\n\ninput \"src\" {\n" + inputBody
                + "\n}\n\n" + expected + "\n";
    }

    public void testAnInputAliasStandsForAColumnOfItsTable() {
        PsiFile test = addTest("test_fin", "fin", testText("SELECT 'a' AS full_name", "SELECT 'a' AS full_name"));
        SqlxSqlBlock input = PsiTreeUtil.findChildOfType(test, SqlxInputBlock.class).content();

        List<TestColumnAlias> found = aliases().in(input);

        assertEquals(List.of(SRC_NAME), found.stream().map(TestColumnAlias::column).toList());
        assertEquals(TestBlockKind.INPUT, found.get(0).kind());
    }

    public void testAnExpectedAliasStandsForAColumnOfTheTestedDataset() {
        PsiFile test = addTest("test_fin", "fin", testText("SELECT 'a' AS full_name", "SELECT 'a' AS full_name"));
        SqlxSqlBlock expected = PsiTreeUtil.getChildrenOfTypeAsList(test, SqlxSqlBlock.class).getLast();

        List<TestColumnAlias> found = aliases().in(expected);

        assertEquals(List.of(new ColumnRef("p.d.fin", "full_name")),
                found.stream().map(TestColumnAlias::column).toList());
        assertEquals(TestBlockKind.EXPECTED, found.get(0).kind());
    }

    public void testStructFieldsStandForTheirDottedColumn() {
        PsiFile test = addTest("test_fin", "fin",
                testText("SELECT STRUCT('p' AS city) AS address", "SELECT 'a' AS full_name"));
        SqlxSqlBlock input = PsiTreeUtil.findChildOfType(test, SqlxInputBlock.class).content();

        assertEquals(Set.of(new ColumnRef("p.d.src", "address.city"), new ColumnRef("p.d.src", "address")),
                Set.copyOf(aliases().in(input).stream().map(TestColumnAlias::column).toList()));
    }

    public void testOfFindsTheAliasesOfCompiledTestsOnly() {
        addTest("test_fin", "fin", testText("SELECT 'a' AS full_name", "SELECT 'a' AS full_name"));
        myFixture.addFileToProject("definitions/tests/not_compiled.sqlx",
                testText("SELECT 'b' AS full_name", "SELECT 'b' AS full_name"));

        List<TestColumnAlias> found = aliases().of(Set.of(SRC_NAME));

        assertEquals(1, found.size());
        assertEquals("test_fin.sqlx", InjectedLanguageManager.getInstance(getProject())
                .getTopLevelFile(found.get(0).identifier()).getName());
    }

    public void testAnAliasWrittenByAnEvaluatedTemplateIsNotInTheFile() {
        PsiFile test = addTest("test_fin", "fin", "config {\n  type: \"test\",\n  dataset: \"fin\"\n}\n\n"
                + "js {\n  const rows = [1];\n}\n\ninput \"src\" {\n"
                + "SELECT * FROM UNNEST([${rows.map(r => `STRUCT('a' AS full_name)`).join(\",\")}])\n}\n\n"
                + "SELECT 'a' AS full_name\n");
        for (SqlxJsLiteralExpression hole : PsiTreeUtil.findChildrenOfType(test, SqlxJsLiteralExpression.class)) {
            ((DataformExpressionEvaluationServiceImpl) DataformExpressionEvaluationService.getInstance(getProject()))
                    .putCachedValue(test.getVirtualFile(), DataformTemplateSyntax.sourceOf(hole.getText()),
                            "STRUCT('a' AS full_name)");
        }
        PsiManager.getInstance(getProject()).dropPsiCaches();

        assertEquals(List.of(), aliases().of(Set.of(SRC_NAME)));
    }
}
