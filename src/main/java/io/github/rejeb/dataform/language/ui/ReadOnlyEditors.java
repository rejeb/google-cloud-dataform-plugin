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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Creates a SQL viewer without gutter decorations or scroll bars, whose long lines wrap.
     */
    public static @NotNull EditorEx compactSql(@NotNull Project project, @NotNull String sql, boolean lineNumbers) {
        EditorEx editor = viewer(project, sql, FileTypeManager.getInstance().getFileTypeByExtension("sql"));
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
     * Releases an editor created by this factory; does nothing for {@code null} or an already released one.
     */
    public static void release(@Nullable Editor editor) {
        if (editor != null && !editor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }
}
