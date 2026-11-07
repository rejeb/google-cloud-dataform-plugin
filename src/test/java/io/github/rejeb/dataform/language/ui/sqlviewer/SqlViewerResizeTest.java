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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.ui.CodeBlock;

import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class SqlViewerResizeTest extends BasePlatformTestCase {

    private static final String TEN_LINES = "SELECT 1\n".repeat(9) + "SELECT 1";
    private static final String SIXTY_LINES = "SELECT 1\n".repeat(59) + "SELECT 1";

    private final SqlViewerHeights heights = new SqlViewerHeights();
    private final List<SqlViewer> viewers = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            viewers.forEach(SqlViewer::dispose);
        } finally {
            super.tearDown();
        }
    }

    public void testTheCursorShowsTheResizeArrowOnTheBottomEdgeOnly() {
        SqlViewer viewer = resizable("a", TEN_LINES);
        CodeBlock block = SqlViewerDrag.block(viewer);

        SqlViewerDrag.mouse(block, MouseEvent.MOUSE_MOVED, block.getHeight() - 2, 0);
        assertEquals(Cursor.S_RESIZE_CURSOR, block.getCursor().getType());

        SqlViewerDrag.mouse(block, MouseEvent.MOUSE_MOVED, 2, 0);
        assertEquals(Cursor.DEFAULT_CURSOR, block.getCursor().getType());
    }

    public void testAViewerThatIsNotResizableHasNoResizeEdge() {
        SqlViewer viewer = viewer(SqlViewerOptions.fitText(20), SIXTY_LINES);
        int before = viewer.editorAreaHeight();
        CodeBlock block = SqlViewerDrag.block(viewer);

        SqlViewerDrag.mouse(block, MouseEvent.MOUSE_MOVED, block.getHeight() - 2, 0);
        SqlViewerDrag.drag(viewer, 3 * lineHeight(viewer));

        assertEquals(Cursor.DEFAULT_CURSOR, block.getCursor().getType());
        assertEquals(before, viewer.editorAreaHeight());
    }

    public void testDraggingTheBottomEdgeChangesTheHeight() {
        SqlViewer viewer = resizable("a", SIXTY_LINES);
        int lineHeight = lineHeight(viewer);
        int before = viewer.editorAreaHeight();
        int preferredBefore = viewer.getPreferredSize().height;

        SqlViewerDrag.drag(viewer, 3 * lineHeight);

        assertEquals(before + 3 * lineHeight, viewer.editorAreaHeight());
        assertEquals(preferredBefore + 3 * lineHeight, viewer.getPreferredSize().height);
        assertEquals(Integer.valueOf(before + 3 * lineHeight), heights.get("a"));
    }

    public void testTheHeightStaysBetweenThreeLinesAndTheWholeText() {
        SqlViewer viewer = resizable("a", TEN_LINES);
        int lineHeight = lineHeight(viewer);
        int wholeText = viewer.editorAreaHeight();

        SqlViewerDrag.drag(viewer, -100 * lineHeight);
        assertEquals(3 * lineHeight, viewer.editorAreaHeight());

        SqlViewerDrag.drag(viewer, 100 * lineHeight);
        assertEquals(wholeText, viewer.editorAreaHeight());
    }

    public void testATextShorterThanThreeLinesCannotShrink() {
        SqlViewer viewer = resizable("a", "SELECT 1");
        int wholeText = viewer.editorAreaHeight();

        SqlViewerDrag.drag(viewer, -100 * lineHeight(viewer));

        assertEquals(wholeText, viewer.editorAreaHeight());
    }

    public void testDoubleClickingTheEdgeRestoresTheAutomaticHeight() {
        SqlViewer viewer = resizable("a", SIXTY_LINES);
        int automatic = viewer.editorAreaHeight();
        SqlViewerDrag.drag(viewer, 3 * lineHeight(viewer));
        CodeBlock block = SqlViewerDrag.block(viewer);

        SqlViewerDrag.mouse(block, MouseEvent.MOUSE_CLICKED, block.getHeight() - 2, 2);

        assertEquals(automatic, viewer.editorAreaHeight());
        assertNull(heights.get("a"));
    }

    public void testTheResizedHeightReplacesTheLineLimit() {
        SqlViewer viewer = resizable("a", SIXTY_LINES);
        int lineHeight = lineHeight(viewer);

        SqlViewerDrag.drag(viewer, 5 * lineHeight);

        assertTrue(viewer.editorAreaHeight() >= 25 * lineHeight);
    }

    public void testAViewerRebuiltWithTheSameKeyKeepsTheHeight() {
        SqlViewer first = resizable("a", SIXTY_LINES);
        int automatic = first.editorAreaHeight();
        SqlViewerDrag.drag(first, 3 * lineHeight(first));

        SqlViewer rebuilt = resizable("a", SIXTY_LINES);
        SqlViewer other = resizable("b", SIXTY_LINES);

        assertEquals(first.editorAreaHeight(), rebuilt.editorAreaHeight());
        assertEquals(automatic, other.editorAreaHeight());
    }

    public void testARememberedHeightTallerThanTheTextShowsTheWholeText() {
        SqlViewer automatic = viewer(SqlViewerOptions.fitText(20), TEN_LINES);
        heights.remember("a", 100_000);

        SqlViewer viewer = resizable("a", TEN_LINES);

        assertEquals(automatic.editorAreaHeight(), viewer.editorAreaHeight());
    }

    private SqlViewer resizable(String key, String text) {
        return viewer(SqlViewerOptions.fitText(20).withResize(heights, key), text);
    }

    private SqlViewer viewer(SqlViewerOptions options, String text) {
        SqlViewer viewer = new SqlViewer(getProject(), options);
        viewers.add(viewer);
        viewer.setText(text);
        SqlViewerDrag.layOut(viewer);
        return viewer;
    }

    private static int lineHeight(SqlViewer viewer) {
        return viewer.editor().getLineHeight();
    }
}
