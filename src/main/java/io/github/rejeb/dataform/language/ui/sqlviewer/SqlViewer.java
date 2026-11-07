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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.diagnostics.ValidationProblemInlayRenderer;
import io.github.rejeb.dataform.language.ui.CodeBlock;
import io.github.rejeb.dataform.language.ui.ReadOnlyEditors;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-only viewer every place of the plugin shows a query in: a Copy button above a rounded
 * code block holding the highlighted text, whose long lines wrap and which scrolls with the wheel
 * and the touchpad without showing a scroll bar. {@link SqlViewerOptions} sets its height, its
 * language, whether it shows the Copy button and the line numbers, and whether the user can resize
 * it.
 * <p>
 * A header can share the row of the Copy button, at its left. An error can be marked on a line: the
 * line is painted, the word at the error is underlined, the message is shown below the line and the
 * line is scrolled to the middle of the viewer; new text clears the mark.
 * <p>
 * The editor is only created once there is text to show, and the viewer is hidden while it has
 * none. The viewer must be disposed to release its editor.
 */
public final class SqlViewer extends JPanel implements Disposable {

    private static final String PLACE = "Dataform.SqlViewer";
    private static final int GAP = 6;
    private static final int MIN_RESIZED_LINES = 3;
    private static final JBColor ERROR_LINE = new JBColor(0xFFEBEB, 0x3F2B2D);

    private final Project project;
    private final SqlViewerOptions options;
    private final FileType fileType;
    private final JPanel editorArea;
    private final @Nullable ActionToolbar copyToolbar;
    private final List<RangeHighlighter> errorHighlighters = new ArrayList<>();
    private @Nullable Inlay<?> errorMessage;
    private @Nullable JPanel topRow;
    private @Nullable JComponent header;
    private @Nullable EditorEx editor;
    private @Nullable Integer userHeight;
    private String text = "";

    /**
     * Creates an empty viewer, hidden until it is given text.
     */
    public SqlViewer(@NotNull Project project, @NotNull SqlViewerOptions options) {
        super(new BorderLayout(0, JBUI.scale(GAP)));
        this.project = project;
        this.options = options;
        this.fileType = options.fileType() != null ? options.fileType() : ReadOnlyEditors.sqlFileType();
        this.userHeight = options.resize() != null ? options.resize().heights().get(options.resize().key()) : null;
        setOpaque(false);
        editorArea = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, editorAreaHeight());
            }
        };
        editorArea.setOpaque(false);
        copyToolbar = options.copyButton()
                ? Toolbars.horizontal(PLACE, this, Toolbars.withText(new CopyAction()))
                : null;
        if (copyToolbar != null) {
            topRow().add(copyToolbar.getComponent(), BorderLayout.EAST);
        }
        setVisible(false);
    }

    /**
     * Shows the given text; blank text hides the viewer. Must be called on the EDT.
     */
    public void setText(@Nullable String newText) {
        clearErrorMark();
        text = newText != null ? StringUtil.convertLineSeparators(newText) : "";
        boolean shown = !text.isBlank();
        if (editor != null) {
            EditorEx target = editor;
            String shownText = text;
            WriteCommandAction.runWriteCommandAction(project, () -> target.getDocument().setText(shownText));
        } else if (shown) {
            createEditor();
        }
        setVisible(shown);
        revalidate();
        repaint();
    }

    /**
     * Shows the given component at the left of the Copy button, in place of the previous header;
     * {@code null} removes it. Must be called on the EDT.
     */
    public void setHeader(@Nullable JComponent newHeader) {
        if (header != null && topRow != null) topRow.remove(header);
        header = newHeader;
        if (newHeader != null) topRow().add(newHeader, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Marks an error at a 1-based line and column of the text, in place of the previous mark: a
     * position past the end of the text is moved to its last line or the end of the line. Does
     * nothing before the viewer is given text. Must be called on the EDT.
     */
    public void markError(int line, int column, @NotNull String message) {
        if (editor == null) return;
        clearErrorMark();
        Document document = editor.getDocument();
        if (document.getLineCount() == 0) return;
        int lineIndex = Math.clamp(line - 1, 0, document.getLineCount() - 1);
        int lineStart = document.getLineStartOffset(lineIndex);
        int lineEnd = document.getLineEndOffset(lineIndex);
        int start = Math.clamp((long) lineStart + column - 1, lineStart, lineEnd);
        int end = wordEnd(document.getCharsSequence(), start, lineEnd);
        MarkupModel markup = editor.getMarkupModel();
        TextAttributes lineAttributes = new TextAttributes(null, ERROR_LINE, null, null, Font.PLAIN);
        errorHighlighters.add(markup.addLineHighlighter(lineIndex, HighlighterLayer.ERROR, lineAttributes));
        if (end > start) {
            errorHighlighters.add(markup.addRangeHighlighter(start, end, HighlighterLayer.ERROR,
                    editor.getColorsScheme().getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES),
                    HighlighterTargetArea.EXACT_RANGE));
        }
        errorMessage = editor.getInlayModel().addBlockElement(start, true, false, 0,
                new ValidationProblemInlayRenderer(List.of(message)));
        editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(start), ScrollType.CENTER);
        revalidate();
        repaint();
    }

    /**
     * The text shown, with {@code \n} line separators.
     */
    public @NotNull String text() {
        return text;
    }

    /**
     * Whether the viewer shows text that is not blank.
     */
    public boolean hasText() {
        return !text.isBlank();
    }

    /**
     * The editor showing the text, {@code null} before the viewer is given text and once disposed.
     */
    public @Nullable EditorEx editor() {
        return editor;
    }

    /**
     * The height of the area showing the text, inside the code block.
     */
    public int editorAreaHeight() {
        if (editor == null) return 0;
        int textHeight = editor.getContentComponent().getPreferredSize().height;
        return switch (options.height()) {
            case SqlViewerHeight.Fill ignored -> textHeight;
            case SqlViewerHeight.FitText fit -> userHeight != null
                    ? clampToText(userHeight, textHeight)
                    : Math.min(textHeight, fit.maxLines() * editor.getLineHeight());
        };
    }

    @Nullable
    ActionToolbar copyToolbar() {
        return copyToolbar;
    }

    @Override
    public void dispose() {
        clearErrorMark();
        ReadOnlyEditors.release(editor);
        editor = null;
    }

    private void clearErrorMark() {
        errorHighlighters.stream().filter(RangeHighlighter::isValid).forEach(RangeHighlighter::dispose);
        errorHighlighters.clear();
        if (errorMessage != null && errorMessage.isValid()) Disposer.dispose(errorMessage);
        errorMessage = null;
    }

    private static int wordEnd(@NotNull CharSequence chars, int start, int lineEnd) {
        int end = start;
        while (end < lineEnd && Character.isJavaIdentifierPart(chars.charAt(end))) end++;
        return end == start && start < lineEnd ? start + 1 : end;
    }

    @NotNull
    private JPanel topRow() {
        if (topRow == null) {
            topRow = new JPanel(new BorderLayout());
            topRow.setOpaque(false);
            add(topRow, BorderLayout.NORTH);
        }
        return topRow;
    }

    private void createEditor() {
        EditorEx created = ReadOnlyEditors.compact(project, text, fileType, options.lineNumbers());
        ReadOnlyEditors.hideVerticalScrollbar(created);
        created.getSettings().setAdditionalLinesCount(0);
        created.getSettings().setAdditionalColumnsCount(0);
        if (options.height() instanceof SqlViewerHeight.FitText) {
            created.getContentComponent().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    revalidate();
                }
            });
        }
        editor = created;
        editorArea.add(created.getComponent(), BorderLayout.CENTER);
        CodeBlock block = new CodeBlock(created, editorArea);
        if (options.resize() != null && options.height() instanceof SqlViewerHeight.FitText) {
            BottomEdgeResizer.install(block, new ResizeTarget(options.resize()));
        }
        add(block, BorderLayout.CENTER);
    }

    private int clampToText(int height, int textHeight) {
        int lineHeight = editor != null ? editor.getLineHeight() : 0;
        int smallest = Math.min(MIN_RESIZED_LINES * lineHeight, textHeight);
        return Math.clamp(height, smallest, Math.max(smallest, textHeight));
    }

    private void heightChanged() {
        revalidate();
        repaint();
    }

    private final class ResizeTarget implements BottomEdgeResizer.Target {

        private final SqlViewerOptions.Resize resize;

        private ResizeTarget(@NotNull SqlViewerOptions.Resize resize) {
            this.resize = resize;
        }

        @Override
        public int height() {
            return editorAreaHeight();
        }

        @Override
        public void resizeTo(int height) {
            if (editor == null) return;
            int resized = clampToText(height, editor.getContentComponent().getPreferredSize().height);
            userHeight = resized;
            resize.heights().remember(resize.key(), resized);
            heightChanged();
        }

        @Override
        public void reset() {
            userHeight = null;
            resize.heights().forget(resize.key());
            heightChanged();
        }
    }

    private final class CopyAction extends DumbAwareAction {

        private CopyAction() {
            super("Copy", "Copy the text", DataformIcons.COPY);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!text.isEmpty());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
