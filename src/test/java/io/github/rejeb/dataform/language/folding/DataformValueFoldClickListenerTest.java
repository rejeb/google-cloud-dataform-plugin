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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.evaluation.DataformExpression;
import io.github.rejeb.dataform.language.evaluation.DataformExpressionCollector;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public class DataformValueFoldClickListenerTest extends DataformFoldingTestCase {

    private static final String MULTILINE_VALUE = """
            STRUCT(
              MAX(goalsScored) AS goalsScored,
              ARRAY_AGG(x ORDER BY goalsScored DESC LIMIT 1)[OFFSET(0)] AS players
            )""";

    private static final String DEFINITION = """
            config { type: "table" }

            SELECT
                ${stats.build(
                  "goalsScored"
                )}
                AS topScorers
            """;

    private static final String CONFIG_DEFINITION = """
            config {
              type: "table",
              columns: column_descriptions.columns,
              bigquery: {
                clusterBy: ["teamName"]
              }
            }

            SELECT 1
            """;

    private final DataformValueFoldClickListener listener = new DataformValueFoldClickListener();

    private CustomFoldRegion configureMultilineValue() {
        PsiFile file = configureDefinition("mart.sqlx", DEFINITION);
        DataformExpression expression =
                DataformExpressionCollector.collectSqlxTemplates(file).getFirst();
        seed(file, expression.source(), MULTILINE_VALUE);
        List<CustomFoldRegion> regions = customRegions();
        assertEquals("the fixture needs one multi-line value to click on", 1, regions.size());
        return regions.getFirst();
    }

    public void testEveryNewlyOpenedEditorIsMadeClickable() {
        configureDefinition("first.sqlx", DEFINITION);
        assertTrue("the platform must call the listener for the first editor",
                DataformValueFoldClickListener.isAttachedTo(myFixture.getEditor()));

        configureDefinition("second.sqlx", DEFINITION);
        assertTrue("a file opened later must be made clickable too",
                DataformValueFoldClickListener.isAttachedTo(myFixture.getEditor()));
    }

    public void testClickingTheValueBringsTheExpressionSourceBack() {
        CustomFoldRegion region = configureMultilineValue();

        EditorMouseEvent event = clickOn(region);
        listener.mouseClicked(event);

        assertTrue("the click must be consumed so it does not move the caret", event.isConsumed());
        assertEmpty("clicking the value must remove the multi-line region", customRegions());
    }

    public void testClickingOutsideAValueChangesNothing() {
        configureMultilineValue();

        EditorMouseEvent event = clickOn(null);
        listener.mouseClicked(event);

        assertFalse("a click outside a value must not be consumed", event.isConsumed());
        assertEquals("the multi-line region must survive", 1, customRegions().size());
    }

    public void testClickingAConfigBlockIncludesValueBringsTheSourceBack() {
        myFixture.addFileToProject("includes/column_descriptions.js",
                "const columns = {};\nmodule.exports = { columns };\n");
        PsiFile file = configureDefinition("stats.sqlx", CONFIG_DEFINITION);
        seed(file, "column_descriptions.columns", MULTILINE_VALUE);
        List<CustomFoldRegion> regions = customRegions();
        assertEquals("the config block reference must be painted over several lines",
                1, regions.size());

        EditorMouseEvent event = clickOn(regions.getFirst());
        listener.mouseClicked(event);

        assertTrue("the click must be consumed", event.isConsumed());
        assertEmpty("clicking the value must remove the multi-line region", customRegions());
    }

    public void testRightClickIsLeftToTheContextMenu() {
        CustomFoldRegion region = configureMultilineValue();

        EditorMouseEvent event = mouseEvent(region, MouseEvent.BUTTON3, 1);
        listener.mouseClicked(event);

        assertFalse("a right click must not be consumed", event.isConsumed());
        assertEquals("the multi-line region must survive", 1, customRegions().size());
    }

    private EditorMouseEvent clickOn(@Nullable FoldRegion region) {
        return mouseEvent(region, MouseEvent.BUTTON1, 1);
    }

    private EditorMouseEvent mouseEvent(@Nullable FoldRegion region, int button, int clickCount) {
        Editor editor = myFixture.getEditor();
        MouseEvent mouseEvent = new MouseEvent(editor.getContentComponent(),
                MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0,
                clickCount, false, button);
        return new EditorMouseEvent(editor, mouseEvent, EditorMouseEventArea.EDITING_AREA,
                0, new LogicalPosition(0, 0), new VisualPosition(0, 0), true, region, null, null);
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
