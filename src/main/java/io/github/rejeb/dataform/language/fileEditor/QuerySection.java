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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class QuerySection extends JPanel {

    private final Project project;
    private final FileType fileType;
    private final boolean isError;
    private final JPanel editorWrap;
    private EditorEx editor;

    QuerySection(String title, FileType fileType, Project project, boolean isError) {
        super(new BorderLayout());
        this.project = project;
        this.fileType = fileType != null ? fileType : PlainTextFileType.INSTANCE;
        this.isError = isError;
        setOpaque(false);
        setBorder(JBUI.Borders.emptyBottom(12));

        JLabel label = new JLabel(title);
        label.setFont(JBUI.Fonts.label(11).asBold());
        label.setForeground(isError ? JBColor.RED : UIUtil.getContextHelpForeground());
        label.setBorder(JBUI.Borders.emptyBottom(4));

        editorWrap = new JPanel(new BorderLayout());
        editorWrap.setOpaque(false);
        editorWrap.setPreferredSize(new Dimension(-1, 150));

        add(label, BorderLayout.NORTH);
        add(editorWrap, BorderLayout.CENTER);
        setVisible(false);
    }

    /**
     * Shows the content, creating the editor on first use: a file has five sections per action
     * and most of them stay empty, so an editor is only paid for when there is text to show.
     */
    void setContent(String content) {
        boolean hasContent = content != null && !content.isBlank();
        if (hasContent) {
            EditorEx target = ensureEditor();
            WriteCommandAction.runWriteCommandAction(project, () ->
                    target.getDocument().setText(content));
        } else if (editor != null) {
            WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText(""));
        }
        setVisible(hasContent);
    }

    private EditorEx ensureEditor() {
        if (editor == null) {
            Document doc = EditorFactory.getInstance().createDocument("");
            editor = (EditorEx) EditorFactory.getInstance().createEditor(doc, project, fileType, true);
            editor.getSettings().setLineNumbersShown(!isError);
            editor.getSettings().setFoldingOutlineShown(false);
            editor.setHighlighter(
                    EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
            );
            editorWrap.add(editor.getComponent(), BorderLayout.CENTER);
        }
        return editor;
    }

    @Nullable
    EditorEx getEditor() {
        return editor;
    }

    void dispose() {
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor);
            editor = null;
        }
    }

    /**
     * Returns true if this section has non-blank SQL content.
     */
    public boolean hasContent() {
        EditorEx ed = getEditor();
        if (ed == null) return false;
        return !ed.getDocument().getText().isBlank();
    }
}
