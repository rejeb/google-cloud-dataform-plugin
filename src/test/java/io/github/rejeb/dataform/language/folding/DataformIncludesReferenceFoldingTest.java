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
package io.github.rejeb.dataform.language.folding;

import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;

import java.util.List;
import java.util.Set;

public class DataformIncludesReferenceFoldingTest extends DataformFoldingTestCase {

    private static final String CONFIG_FILE = """
            config {
              type: "table",
              columns: team_player_stat_columns_descriptions.columns_descriptions
            }

            SELECT 1 AS one
            """;

    public void testConfigBlockIncludesReferenceIsCollected() {
        myFixture.addFileToProject("includes/team_player_stat_columns_descriptions.js",
                "const columns_descriptions = { a: \"one\" };\nmodule.exports = { columns_descriptions };\n");
        PsiFile file = myFixture.addFileToProject("definitions/mart.sqlx", CONFIG_FILE);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.doHighlighting();

        List<DataformExpression> expressions = DataformInjectedExpressions.includesReferences(
                file, Set.of("team_player_stat_columns_descriptions"));

        assertEquals("got " + expressions, 1, expressions.size());
        assertEquals("team_player_stat_columns_descriptions.columns_descriptions",
                expressions.getFirst().source());
    }

    public void testFunctionCalleeIsNotCollected() {
        myFixture.addFileToProject("includes/helpers.js", "module.exports = { build: () => \"x\" };\n");
        PsiFile file = myFixture.addFileToProject("definitions/mart.js",
                "publish(\"mart\").query(ctx => `SELECT ${helpers.build()}`);\n");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

        assertEmpty("a function callee must not fold to its source",
                DataformExpressionCollector.collectIncludesReferenceElements(file, Set.of("helpers")));
    }

    public void testLongValueIsFormattedOverMultipleLinesForDocumentation() {
        String value = "{\n  \"a\": {\n    \"b\": \"c\"\n  }\n}";

        String expanded = DataformFoldingPlaceholder.expanded("        " + value.replace("\n", "\n        "));
        String placeholder = DataformFoldingPlaceholder.of(value, "src");

        assertEquals(value, expanded);
        assertFalse("the fold placeholder must stay on one line", placeholder.contains("\n"));
    }
}
