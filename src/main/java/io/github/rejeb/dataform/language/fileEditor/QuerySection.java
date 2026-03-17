/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import javax.swing.*;
import java.awt.*;

class QuerySection extends JPanel {

    private final EditorEx editor;
    private final Project project;

    QuerySection(String title, FileType fileType, Project project, boolean isError) {
        super(new BorderLayout());
        this.project = project;
        setOpaque(false);
        setBorder(JBUI.Borders.emptyBottom(12));

        JLabel label = new JLabel(title);
        label.setFont(JBUI.Fonts.label(11).asBold());
        label.setForeground(isError ? JBColor.RED : UIUtil.getContextHelpForeground());
        label.setBorder(JBUI.Borders.emptyBottom(4));

        FileType ft = fileType != null ? fileType : PlainTextFileType.INSTANCE;
        Document doc = EditorFactory.getInstance().createDocument("");
        editor = (EditorEx) EditorFactory.getInstance().createEditor(doc, project, ft, true);
        editor.getSettings().setLineNumbersShown(!isError);
        editor.getSettings().setFoldingOutlineShown(false);
        editor.setHighlighter(
                EditorHighlighterFactory.getInstance().createEditorHighlighter(project, ft)
        );

        // Hauteur fixe pour ne pas écraser les autres sections
        JPanel editorWrap = new JPanel(new BorderLayout());
        editorWrap.setOpaque(false);
        editorWrap.setPreferredSize(new Dimension(-1, 150));
        editorWrap.add(editor.getComponent(), BorderLayout.CENTER);

        add(label, BorderLayout.NORTH);
        add(editorWrap, BorderLayout.CENTER);
        setVisible(false);
    }

    void setContent(String content) {
        boolean hasContent = content != null && !content.isBlank();
        if (hasContent) {
            WriteCommandAction.runWriteCommandAction(project, () ->
                    editor.getDocument().setText(content));
        }
        setVisible(hasContent);
    }

    EditorEx getEditor() {
        return editor;
    }

    void dispose() {
        EditorFactory.getInstance().releaseEditor(editor);
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
