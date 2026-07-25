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

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionEvaluationService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DataformIncludesFoldPathTest extends DataformFoldingTestCase {

    private static final String SQLX = """
            config {
              type: "table",
              columns: descriptions.columns_descriptions,
              bigquery: { clusterBy: ["teamName"] }
            }

            SELECT 1 AS one
            """;

    private PsiFile configure() {
        myFixture.addFileToProject("includes/descriptions.js",
                "const columns_descriptions = { a: \"one\" };\nmodule.exports = { columns_descriptions };\n");
        return configureDefinition("mart.sqlx", SQLX);
    }

    public void testInjectedReferencesAreFoundWithoutPriorHighlighting() {
        PsiFile file = configure();

        List<DataformExpression> cold = DataformExpressionCollector.collectInjectedIncludesReferences(
                file, Set.of("descriptions"));

        assertEquals("injections must be enumerable without a highlighting pass, got " + cold, 1, cold.size());
    }

    public void testIncludeNamesAreAvailableToTheFoldingBuilder() {
        PsiFile file = configure();
        Set<String> names = DataformExpressionEvaluationService.getInstance(getProject())
                .includeNames(file.getVirtualFile());

        assertTrue("the folding builder cannot collect includes references without the names, got " + names,
                names.contains("descriptions"));
    }

    public void testConfigBlockIncludesReferenceFolds() {
        PsiFile file = configure();
        seed(file, "descriptions.columns_descriptions", "{\"a\": \"one\"}");

        assertEquals("expected the config value to fold", 1,
                regionsWithPlaceholder("{\"a\": \"one\"}").size());
    }

    public void testLongConfigValueIsPaintedOverMultipleLinesKeepingTheLineAround() {
        PsiFile file = configure();
        String value = """
                {
                  "goals": "Total number of goals scored by the top scorers.",
                  "players": "List of top-scoring players with their clubs and positions."
                }""";
        seed(file, "descriptions.columns_descriptions", value);

        myFixture.doHighlighting();
        List<DataformMultilineFoldManager.MultilineValue> values = DataformMultilineValues.of(
                getProject(), file.getVirtualFile(), myFixture.getEditor().getDocument());

        assertEquals("the config reference must be rendered over several lines, got " + values,
                1, values.size());
        DataformMultilineFoldManager.MultilineValue rendered = values.getFirst();
        assertEquals("  columns: ", rendered.prefix());
        assertEquals(",", rendered.suffix());
        assertEquals(4, rendered.lines().size());

        DataformMultilineFoldManager.apply(myFixture.getEditor(), values);
        long custom = Arrays.stream(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                .filter(region -> region instanceof CustomFoldRegion)
                .count();
        assertEquals("expected a custom fold region on the config line", 1, custom);
        assertEmpty("the one-line placeholder must not compete", dataformRegions());
    }
}
