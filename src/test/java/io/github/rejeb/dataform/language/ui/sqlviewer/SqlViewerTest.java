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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.ui.CodeBlock;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;

public class SqlViewerTest extends BasePlatformTestCase {

    private static final String LONG_LINE = "SELECT " + "customer_id, ".repeat(200) + "1";

    private final List<SqlViewer> viewers = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            viewers.forEach(SqlViewer::dispose);
        } finally {
            super.tearDown();
        }
    }

    public void testAViewerWithoutTextIsHiddenAndHasNoEditor() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "  ");

        assertFalse(viewer.isVisible());
        assertFalse(viewer.hasText());
        assertNull(viewer.editor());
    }

    public void testTheTextShowsInAReadOnlyEditor() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");

        EditorEx editor = viewer.editor();
        assertNotNull(editor);
        assertTrue(viewer.isVisible());
        assertEquals("SELECT 1", editor.getDocument().getText());
        assertTrue(editor.isViewer());
        assertTrue(editor.getSettings().isLineNumbersShown());
    }

    public void testNewTextReplacesTheTextOfTheSameEditor() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");
        EditorEx editor = viewer.editor();

        viewer.setText("SELECT 2");

        assertSame(editor, viewer.editor());
        assertEquals("SELECT 2", viewer.text());
    }

    public void testWindowsLineSeparatorsAreAccepted() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT\r\n1");

        viewer.setText("SELECT\r\n2");

        assertEquals("SELECT\n2", viewer.editor().getDocument().getText());
    }

    public void testBlankTextHidesTheViewer() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");

        viewer.setText(null);

        assertFalse(viewer.isVisible());
        assertFalse(viewer.hasText());
    }

    public void testLineNumbersCanBeTurnedOff() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill().withLineNumbers(false), "SELECT 1");

        assertFalse(viewer.editor().getSettings().isLineNumbersShown());
    }

    public void testLongLinesWrapAndNoScrollBarIsShown() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), LONG_LINE);

        EditorEx editor = viewer.editor();
        JScrollPane scrollPane = editor.getScrollPane();
        assertTrue(editor.getSettings().isUseSoftWraps());
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.getHorizontalScrollBarPolicy());
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.getVerticalScrollBarPolicy());
        assertEquals(0, scrollPane.getVerticalScrollBar().getPreferredSize().width);
    }

    public void testNoRoomIsLeftUnderTheLastLine() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");

        assertEquals(0, viewer.editor().getSettings().getAdditionalLinesCount());
        assertEquals(0, viewer.editor().getSettings().getAdditionalColumnsCount());
    }

    public void testTheTextIsShownInARoundedCodeBlock() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");

        CodeBlock block = (CodeBlock) SwingUtilities.getAncestorOfClass(CodeBlock.class,
                viewer.editor().getComponent());

        assertNotNull(block);
        assertTrue(SwingUtilities.isDescendingFrom(block, viewer));
    }

    public void testTheCopyButtonCopiesTheText() throws Exception {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");

        ActionToolbar toolbar = viewer.copyToolbar();
        assertNotNull(toolbar);
        AnAction copy = toolbar.getActionGroup().getChildren(null)[0];
        assertEquals("Copy", copy.getTemplatePresentation().getText());
        assertSame(DataformIcons.COPY, copy.getTemplatePresentation().getIcon());
        copy.actionPerformed(TestActionEvent.createTestEvent(copy));
        assertEquals("SELECT 1", CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
    }

    public void testTheCopyButtonCanBeTurnedOff() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill().withCopyButton(false), "SELECT 1");

        assertNull(viewer.copyToolbar());
    }

    public void testAFitTextViewerIsAsTallAsItsLinesUpToTheLimit() {
        SqlViewer shortOne = viewer(SqlViewerOptions.fitText(20), "SELECT\n  1\nFROM x");
        SqlViewer longOne = viewer(SqlViewerOptions.fitText(20), "SELECT 1\n".repeat(60));

        int lineHeight = shortOne.editor().getLineHeight();
        int shortHeight = shortOne.editorAreaHeight();
        int longHeight = longOne.editorAreaHeight();

        assertTrue(shortHeight >= 3 * lineHeight && shortHeight < 4 * lineHeight);
        assertTrue(longHeight >= 20 * lineHeight && longHeight < 21 * lineHeight);
        assertTrue(longOne.getPreferredSize().height > longHeight);
    }

    public void testALongLineDoesNotWidenTheViewer() {
        SqlViewer shortLine = viewer(SqlViewerOptions.fitText(20), "SELECT 1");
        SqlViewer longLine = viewer(SqlViewerOptions.fitText(20), LONG_LINE);

        assertEquals(shortLine.getPreferredSize().width, longLine.getPreferredSize().width);
    }

    public void testAFillViewerTakesTheHeightItIsGiven() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");
        viewer.setSize(400, 300);

        SwingTestLayout.layOut(viewer);

        CodeBlock block = (CodeBlock) SwingUtilities.getAncestorOfClass(CodeBlock.class,
                viewer.editor().getComponent());
        assertEquals(300, block.getY() + block.getHeight());
    }

    public void testDisposingTheViewerReleasesItsEditor() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");
        EditorEx editor = viewer.editor();

        viewer.dispose();

        assertTrue(editor.isDisposed());
        assertNull(viewer.editor());
    }

    public void testTheHeaderSharesTheRowOfTheCopyButton() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");
        JLabel header = new JLabel("MERGE");

        viewer.setHeader(header);

        ActionToolbar copy = viewer.copyToolbar();
        assertNotNull(copy);
        assertSame(copy.getComponent().getParent(), header.getParent());
    }

    public void testTheHeaderIsShownWithoutCopyButton() {
        SqlViewer viewer = new SqlViewer(getProject(), SqlViewerOptions.fill().withCopyButton(false));
        viewers.add(viewer);
        JLabel header = new JLabel("MERGE");

        viewer.setHeader(header);
        viewer.setText("SELECT 1");

        assertTrue(SwingUtilities.isDescendingFrom(header, viewer));
    }

    public void testANewHeaderReplacesThePreviousOne() {
        SqlViewer viewer = viewer(SqlViewerOptions.fill(), "SELECT 1");
        JLabel first = new JLabel("MERGE");
        JLabel second = new JLabel("SELECT");

        viewer.setHeader(first);
        viewer.setHeader(second);

        assertFalse(SwingUtilities.isDescendingFrom(first, viewer));
        assertTrue(SwingUtilities.isDescendingFrom(second, viewer));
    }

    private SqlViewer viewer(SqlViewerOptions options, String text) {
        SqlViewer viewer = new SqlViewer(getProject(), options);
        viewers.add(viewer);
        viewer.setText(text);
        return viewer;
    }
}
