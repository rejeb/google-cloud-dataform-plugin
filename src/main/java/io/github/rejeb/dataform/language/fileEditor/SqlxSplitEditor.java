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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.DumbAware;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.fileEditor.action.CompileProjectAction;
import io.github.rejeb.dataform.language.fileEditor.action.ExecuteQueryAction;
import io.github.rejeb.dataform.language.fileEditor.action.RunFileAction;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SqlxSplitEditor extends TextEditorWithPreview {

    private final SqlxCompiledPreviewEditor myPreview;

    public SqlxSplitEditor(@NotNull TextEditor textEditor,
                           @NotNull SqlxCompiledPreviewEditor previewEditor) {
        super(textEditor, previewEditor, "SQLX Editor",
                Layout.SHOW_EDITOR,
                false);
        this.myPreview = previewEditor;
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
        group.add(new CompileProjectAction(myPreview.getProject()));
        group.add(new ExecuteQueryAction(myPreview));
        group.addSeparator();
        group.add(new RunFileAction(myPreview));
        return group;
    }

    @Override
    protected boolean isShowActionsInTabs() {
        return false;
    }

    @Override
    protected @NotNull ActionToolbar createRightToolbar() {
        DefaultActionGroup group = new DefaultActionGroup(createViewActionGroup(), Separator.create(),
                previewViewActionGroup());
        return ActionManager.getInstance().createActionToolbar(ActionPlaces.TEXT_EDITOR_WITH_PREVIEW, group, true);
    }

    @Override
    protected @NotNull ActionGroup createViewActionGroup() {
        return new DefaultActionGroup(
                new LayoutAction("Editor only", DataformIcons.LAYOUT_EDITOR_ONLY, Layout.SHOW_EDITOR),
                new LayoutAction("Editor and preview", DataformIcons.LAYOUT_EDITOR_AND_PREVIEW,
                        Layout.SHOW_EDITOR_AND_PREVIEW),
                new LayoutAction("Preview only", DataformIcons.LAYOUT_PREVIEW_ONLY, Layout.SHOW_PREVIEW));
    }

    private @NotNull ActionGroup previewViewActionGroup() {
        return new DefaultActionGroup(
                new ShowViewAction("Query", "Show the compiled queries", DataformIcons.PREVIEW_QUERY,
                        SqlxCompiledPreviewEditor.View.QUERY),
                new ShowViewAction("Schema", "Show the schemas of the actions", DataformIcons.PREVIEW_SCHEMA,
                        SqlxCompiledPreviewEditor.View.SCHEMA),
                new ShowViewAction("Lineage", "Show the lineage of the file", DataformIcons.LINEAGE,
                        SqlxCompiledPreviewEditor.View.LINEAGE),
                new ShowViewAction("Test", "Show the compiled test and expected output queries",
                        DataformIcons.PREVIEW_TEST, SqlxCompiledPreviewEditor.View.TEST));
    }

    private final class LayoutAction extends ToggleAction implements DumbAware {

        private final Layout layout;

        LayoutAction(@NotNull String text, @NotNull Icon icon, @NotNull Layout layout) {
            super(text, text, icon);
            this.layout = layout;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return getLayout() == layout;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) setLayout(layout);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    private class ShowViewAction extends AnAction implements Toggleable, DumbAware {

        private final SqlxCompiledPreviewEditor.View view;

        ShowViewAction(@NotNull String text, @NotNull String description,
                       @NotNull Icon icon,
                       @NotNull SqlxCompiledPreviewEditor.View view) {
            super(text, description, icon);
            this.view = view;
            Toolbars.withText(this);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            if (isShown()) {
                setLayout(Layout.SHOW_EDITOR);
            } else {
                if (getLayout() != Layout.SHOW_PREVIEW) setLayout(Layout.SHOW_EDITOR_AND_PREVIEW);
                myPreview.showPanel(view);
            }
            Toggleable.setSelected(e.getPresentation(), isShown());
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setVisible(myPreview.getMode().shows(view));
            Toggleable.setSelected(e.getPresentation(), isShown());
        }

        private boolean isShown() {
            return getLayout() != Layout.SHOW_EDITOR && myPreview.getActiveView() == view;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
