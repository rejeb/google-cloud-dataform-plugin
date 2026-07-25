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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;

import java.util.Arrays;
import java.util.List;

public class DataformMultilineFoldTest extends DataformFoldingTestCase {

    private static final String MULTILINE_VALUE = """
            STRUCT(
              MAX(goalsScored) AS goalsScored,
              ARRAY_AGG(x ORDER BY goalsScored DESC LIMIT 1)[OFFSET(0)] AS players
            )""";

    private static final String WHOLE_LINE_EXPRESSION = """
            config { type: "table" }

            SELECT
                ${stats.build(
                  "goalsScored"
                )}
                AS topScorers
            """;

    public void testWholeLineExpressionUsesAMultilineCustomRegion() {
        PsiFile file = configureDefinition("mart.sqlx", WHOLE_LINE_EXPRESSION);
        DataformExpression expression = DataformExpressionCollector.collectSqlxTemplates(file).getFirst();
        seed(file, expression.source(), MULTILINE_VALUE);

        List<CustomFoldRegion> custom = customRegions();

        assertEquals("expected one multi-line region, got " + custom, 1, custom.size());
        CustomFoldRegion region = custom.getFirst();
        assertEquals("the region must span the lines of the expression",
                4, region.getHeightInPixels() / myFixture.getEditor().getLineHeight());
        assertTrue("a custom region is always collapsed", !region.isExpanded());
        assertEmpty("the one-line placeholder must not compete with the multi-line region", dataformRegions());
    }

    public void testMultilineRegionSurvivesFurtherHighlightingPasses() {
        PsiFile file = configureDefinition("mart.sqlx", WHOLE_LINE_EXPRESSION);
        DataformExpression expression = DataformExpressionCollector.collectSqlxTemplates(file).getFirst();
        seed(file, expression.source(), MULTILINE_VALUE);

        assertEquals(1, customRegions().size());
        myFixture.doHighlighting();
        assertEquals("the folding pass must not drop the custom region", 1, customRegions().size());
        myFixture.doHighlighting();
        assertEquals(1, customRegions().size());
    }

    public void testShowSourceRemovesTheRegionAndDoesNotRecreateIt() {
        PsiFile file = configureDefinition("mart.sqlx", WHOLE_LINE_EXPRESSION);
        DataformExpression expression = DataformExpressionCollector.collectSqlxTemplates(file).getFirst();
        seed(file, expression.source(), MULTILINE_VALUE);

        CustomFoldRegion region = customRegions().getFirst();
        region.getRenderer().calcGutterIconRenderer(region).getClickAction()
                .actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent());

        assertEmpty("the gutter action must bring the source back", customRegions());
    }

    public void testInlineExpressionKeepsTheCodeSharingItsLine() {
        PsiFile file = configureDefinition("mart.sqlx",
                "config { type: \"table\" }\n\nSELECT * FROM ${ref(\"users\")} AS u\n");
        seed(file, "ref(\"users\")", MULTILINE_VALUE);

        myFixture.doHighlighting();
        List<DataformMultilineFoldManager.MultilineValue> values = DataformMultilineValues.of(
                getProject(), file.getVirtualFile(), myFixture.getEditor().getDocument());

        assertEquals("got " + values, 1, values.size());
        assertEquals("SELECT * FROM ", values.getFirst().prefix());
        assertEquals(" AS u", values.getFirst().suffix());
        assertEquals(1, customRegions().size());
        assertEmpty("the one-line placeholder must not compete", dataformRegions());
    }

    public void testShortValueKeepsTheOneLinePlaceholder() {
        PsiFile file = configureDefinition("mart.sqlx", WHOLE_LINE_EXPRESSION);
        DataformExpression expression = DataformExpressionCollector.collectSqlxTemplates(file).getFirst();
        seed(file, expression.source(), "short_value");

        assertEmpty(customRegions());
        assertEquals("got " + dataformRegions(), 1, dataformRegions().size());
    }

    public void testPolicyRequiresTheExpressionToOwnItsLines() {
        PsiFile file = configureDefinition("mart.sqlx", WHOLE_LINE_EXPRESSION);
        Document document = myFixture.getEditor().getDocument();
        TextRange range = DataformExpressionCollector.collectSqlxTemplates(file).getFirst().hostRange();

        assertTrue(DataformMultilineFoldPolicy.qualifies(document, range, MULTILINE_VALUE));
        assertFalse("a short value stays on one line",
                DataformMultilineFoldPolicy.qualifies(document, range, "short"));
    }

    private List<CustomFoldRegion> customRegions() {
        myFixture.doHighlighting();
        DataformMultilineFoldManager.apply(myFixture.getEditor(),
                DataformMultilineValues.of(getProject(),
                        myFixture.getFile().getVirtualFile(),
                        myFixture.getEditor().getDocument()));
        return Arrays.stream(myFixture.getEditor().getFoldingModel().getAllFoldRegions())
                .filter(region -> region instanceof CustomFoldRegion)
                .map(CustomFoldRegion.class::cast)
                .toList();
    }
}
