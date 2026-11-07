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
package io.github.rejeb.dataform.language.ui.sqlviewer;

import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.diagnostics.ValidationProblemInlayRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqlViewerErrorMarkTest extends BasePlatformTestCase {

    private static final String QUERY = "SELECT\n  SUM(x) AS s\nFROM t";

    private final List<SqlViewer> viewers = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            viewers.forEach(SqlViewer::dispose);
        } finally {
            super.tearDown();
        }
    }

    public void testMarkErrorPaintsTheLineAndUnderlinesTheWord() {
        SqlViewer viewer = viewer(QUERY);

        viewer.markError(2, 3, "bad");

        RangeHighlighter line = only(errorHighlighters(viewer), HighlighterTargetArea.LINES_IN_RANGE);
        assertEquals(1, editor(viewer).getDocument().getLineNumber(line.getStartOffset()));
        RangeHighlighter word = only(errorHighlighters(viewer), HighlighterTargetArea.EXACT_RANGE);
        assertEquals("SUM", viewer.text().substring(word.getStartOffset(), word.getEndOffset()));
    }

    public void testMarkErrorShowsTheMessageBelowTheLine() {
        SqlViewer viewer = viewer(QUERY);

        viewer.markError(2, 3, "bad");

        List<Inlay<?>> inlays = blockInlays(viewer);
        assertEquals(1, inlays.size());
        assertInstanceOf(inlays.getFirst().getRenderer(), ValidationProblemInlayRenderer.class);
        assertEquals(Inlay.Placement.BELOW_LINE, inlays.getFirst().getPlacement());
        assertEquals(9, inlays.getFirst().getOffset());
    }

    public void testANewMarkReplacesThePreviousOne() {
        SqlViewer viewer = viewer(QUERY);

        viewer.markError(2, 3, "bad");
        viewer.markError(3, 1, "worse");

        RangeHighlighter line = only(errorHighlighters(viewer), HighlighterTargetArea.LINES_IN_RANGE);
        assertEquals(2, editor(viewer).getDocument().getLineNumber(line.getStartOffset()));
        assertEquals(1, blockInlays(viewer).size());
    }

    public void testSetTextClearsTheMark() {
        SqlViewer viewer = viewer(QUERY);

        viewer.markError(2, 3, "bad");
        viewer.setText("SELECT 2");

        assertEmpty(errorHighlighters(viewer));
        assertEmpty(blockInlays(viewer));
    }

    public void testAPositionPastTheEndIsClamped() {
        SqlViewer viewer = viewer("SELECT 1");

        viewer.markError(9, 40, "x");

        RangeHighlighter line = only(errorHighlighters(viewer), HighlighterTargetArea.LINES_IN_RANGE);
        assertEquals(0, editor(viewer).getDocument().getLineNumber(line.getStartOffset()));
    }

    public void testMarkErrorBeforeAnyTextDoesNothing() {
        SqlViewer viewer = new SqlViewer(getProject(), SqlViewerOptions.fill());
        viewers.add(viewer);

        viewer.markError(1, 1, "x");

        assertNull(viewer.editor());
    }

    private SqlViewer viewer(String text) {
        SqlViewer viewer = new SqlViewer(getProject(), SqlViewerOptions.fill());
        viewers.add(viewer);
        viewer.setText(text);
        return viewer;
    }

    private static EditorEx editor(SqlViewer viewer) {
        EditorEx editor = viewer.editor();
        assertNotNull(editor);
        return editor;
    }

    private static List<RangeHighlighter> errorHighlighters(SqlViewer viewer) {
        return Arrays.stream(editor(viewer).getMarkupModel().getAllHighlighters())
                .filter(highlighter -> highlighter.getLayer() == HighlighterLayer.ERROR)
                .toList();
    }

    private static List<Inlay<?>> blockInlays(SqlViewer viewer) {
        EditorEx editor = editor(viewer);
        return new ArrayList<>(editor.getInlayModel().getBlockElementsInRange(0, editor.getDocument().getTextLength()));
    }

    private static RangeHighlighter only(List<RangeHighlighter> highlighters, HighlighterTargetArea area) {
        List<RangeHighlighter> inArea = highlighters.stream()
                .filter(highlighter -> highlighter.getTargetArea() == area)
                .toList();
        assertEquals(1, inArea.size());
        return inArea.getFirst();
    }
}
