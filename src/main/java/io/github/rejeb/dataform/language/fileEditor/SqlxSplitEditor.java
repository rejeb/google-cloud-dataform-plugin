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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.util.Alarm;
import icons.DatabaseIcons;
import io.github.rejeb.dataform.language.fileEditor.action.ExecuteQueryAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static io.github.rejeb.dataform.language.fileEditor.SqlxCompiledPreviewEditor.View.*;

public class SqlxSplitEditor extends TextEditorWithPreview {

    private final SqlxCompiledPreviewEditor myPreview;
    private final Alarm alarm;

    public SqlxSplitEditor(@NotNull TextEditor textEditor,
                           @NotNull SqlxCompiledPreviewEditor previewEditor) {
        super(textEditor, previewEditor, "SQLX Editor",
                Layout.SHOW_EDITOR,
                false);
        this.myPreview = previewEditor;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

        textEditor.getEditor().getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void documentChanged(@NotNull DocumentEvent event) {
                        alarm.cancelAllRequests();
                        if (!alarm.isDisposed()) {
                            alarm.addRequest(myPreview::updateCompiledSql, 2000);
                        }
                    }
                }, this
        );
    }

    @Override
    protected void onLayoutChange(Layout oldValue, Layout newValue) {
        super.onLayoutChange(oldValue, newValue);
        if (newValue != Layout.SHOW_EDITOR) {
            myPreview.selectNotify();
        }
    }

    @Override
    public void selectNotify() {
        super.selectNotify();
        myPreview.selectNotify();
    }

    @Override
    protected @Nullable ActionGroup createLeftToolbarActionGroup() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new ExecuteQueryAction(myPreview));
        return group;
    }

    @Override
    protected @Nullable ActionGroup createRightToolbarActionGroup() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.addSeparator();
        group.add(getShowEditorAction());
        group.addSeparator();
        group.add(new ShowViewAction(
                "Lineage", "Show lineage graph",
                AllIcons.General.Layout,
                SqlxCompiledPreviewEditor.View.LINEAGE
        ));
        group.add(new ShowViewAction(
                "Query", "Show compiled SQL query",
                DatabaseIcons.Sql,
                SqlxCompiledPreviewEditor.View.QUERY
        ));
        group.add(new ShowViewAction(
                "Schema", "Show table schema",
                AllIcons.Nodes.DataTables,
                SqlxCompiledPreviewEditor.View.SCHEMA
        ));
        return group;
    }



    @Override
    protected @NotNull ActionGroup createViewActionGroup() {
        return new DefaultActionGroup(List.of());
    }

    private class ShowViewAction extends AnAction implements Toggleable {

        private final SqlxCompiledPreviewEditor.View view;

        ShowViewAction(@NotNull String text, @NotNull String description,
                       @NotNull Icon icon,
                       @NotNull SqlxCompiledPreviewEditor.View view) {
            super(text, description, icon);
            this.view = view;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Layout current = getLayout();
            boolean isThisViewActive = myPreview.getActiveView() == view;

            if (current == Layout.SHOW_EDITOR || !isThisViewActive) {
                setLayout(Layout.SHOW_EDITOR_AND_PREVIEW);
                setVerticalSplit(false);
                myPreview.showPanel(view);

            } else if (isVerticalSplit()) {
                setLayout(Layout.SHOW_EDITOR);

            } else {
                setVerticalSplit(true);
            }

            Toggleable.setSelected(e.getPresentation(), getLayout() != Layout.SHOW_EDITOR);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            boolean active = getLayout() == Layout.SHOW_EDITOR_AND_PREVIEW
                    && myPreview.getActiveView() == view;
            Toggleable.setSelected(e.getPresentation(), active);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }
    }


}
