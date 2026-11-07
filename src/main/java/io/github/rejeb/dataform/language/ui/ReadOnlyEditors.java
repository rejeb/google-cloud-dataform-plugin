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
package io.github.rejeb.dataform.language.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.Dimension;

/**
 * Factory of read-only IntelliJ editors used to show query text in panels. Every editor created here
 * must be given back to {@link #release(Editor)}.
 */
public final class ReadOnlyEditors {

    private ReadOnlyEditors() {
    }

    /**
     * Creates a viewer of the given text, highlighted as the given file type.
     */
    public static @NotNull EditorEx viewer(@NotNull Project project, @NotNull String text, @NotNull FileType fileType) {
        EditorFactory factory = EditorFactory.getInstance();
        return (EditorEx) factory.createEditor(factory.createDocument(text), project, fileType, true);
    }

    /**
     * Creates a multi-line read-only field showing the given text. The field creates its editor when
     * it is shown and releases it when it is hidden, so it needs no {@link #release(Editor)}.
     */
    public static @NotNull EditorTextField field(@NotNull Project project, @NotNull String text, @NotNull FileType fileType) {
        EditorTextField field = new EditorTextField(EditorFactory.getInstance().createDocument(text), project, fileType, true, false);
        field.addSettingsProvider(editor -> {
            editor.getSettings().setUseSoftWraps(true);
            editor.setVerticalScrollbarVisible(true);
        });
        return field;
    }

    /**
     * Creates a SQL viewer without gutter decorations or scroll bars, whose long lines wrap.
     */
    public static @NotNull EditorEx compactSql(@NotNull Project project, @NotNull String sql, boolean lineNumbers) {
        return compact(project, sql, sqlFileType(), lineNumbers);
    }

    /**
     * The file type SQL text is highlighted as.
     */
    public static @NotNull FileType sqlFileType() {
        return FileTypeManager.getInstance().getFileTypeByExtension("sql");
    }

    /**
     * Creates a viewer of the given text, highlighted as the given file type, without gutter
     * decorations or scroll bars, whose long lines wrap.
     */
    public static @NotNull EditorEx compact(@NotNull Project project, @NotNull String text, @NotNull FileType fileType,
                                            boolean lineNumbers) {
        EditorEx editor = viewer(project, text, fileType);
        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(lineNumbers);
        settings.setFoldingOutlineShown(false);
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(false);
        settings.setVirtualSpace(false);
        settings.setUseSoftWraps(true);
        settings.setRightMarginShown(false);
        editor.setHorizontalScrollbarVisible(false);
        editor.setVerticalScrollbarVisible(false);
        return editor;
    }

    /**
     * Hides the vertical scroll bar of the editor while the mouse wheel and the touchpad still scroll it.
     */
    public static void hideVerticalScrollbar(@NotNull EditorEx editor) {
        JScrollPane scrollPane = editor.getScrollPane();
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
    }

    /**
     * Releases an editor created by this factory; does nothing for {@code null} or an already released one.
     */
    public static void release(@Nullable Editor editor) {
        if (editor != null && !editor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }
}
