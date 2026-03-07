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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sql.SqlFileType;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.DatabaseIcons;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledQuery;
import io.github.rejeb.dataform.language.service.DataformCompilationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.List;

public class SqlxCompiledPreviewEditor implements FileEditor {

    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final JTabbedPane tabs;

    private final LineagePanel lineagePanel;
    private final QueryPanel queryPanel;

    private final Project project;
    private final VirtualFile file;
    private long myLastCompiledStamp = -1;

    public SqlxCompiledPreviewEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;

        lineagePanel = new LineagePanel(project);
        queryPanel = new QueryPanel(project, resolveFileType(file));

        tabs = new JBTabbedPane();
        tabs.setOpaque(false);
        tabs.setBorder(JBUI.Borders.empty());
        tabs.addTab("Query", DatabaseIcons.Sql, queryPanel);
        tabs.addTab("Lineage", AllIcons.General.Layout, lineagePanel);
        mainPanel.setOpaque(true);
        mainPanel.setBackground(UIUtil.getPanelBackground());
        mainPanel.add(tabs, BorderLayout.CENTER);

        tabs.setBackground(UIUtil.getPanelBackground());
        updateCompiledSql();
    }

    public void updateCompiledSql() {

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Dataform: compiling", true) {
            private List<CompiledQuery> compiledQueries;
            private List<LineageGraph> lineageGraphs;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Compiling " + file.getName() + "...");
                DataformCompilationService svc = project.getService(DataformCompilationService.class);
                CompiledGraph graph = svc.runIfFilesChanged();
                if (graph != null) {
                    String path = file.getCanonicalPath();
                    compiledQueries = graph.findCompiledQueryByFileName(path);
                    lineageGraphs = LineageGraphHelper.buildGraph(graph, path);
                }
                indicator.checkCanceled();
            }

            @Override
            public void onSuccess() {
                ApplicationManager.getApplication().invokeLater(() ->
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            queryPanel.setContent(compiledQueries);
                            lineagePanel.setData(lineageGraphs);
                        }), ModalityState.nonModal()
                );

                mainPanel.revalidate();
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                ApplicationManager.getApplication().invokeLater(() ->
                        WriteCommandAction.runWriteCommandAction(project, () ->
                                queryPanel.getEditor().getDocument().setText(
                                        "-- Compilation error:\n-- " + error.getMessage())
                        ), ModalityState.nonModal()
                );
            }
        });
    }


    private FileType resolveFileType(VirtualFile file) {
        if ("js".equals(file.getExtension())) {
            FileType t = FileTypeManager.getInstance().getFileTypeByExtension("js");
            if (!(t instanceof UnknownFileType)) return t;
        }
        return SqlFileType.INSTANCE;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public @NotNull String getName() {
        return "Dataform Actions Panel";
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return queryPanel.getEditor().getComponent();
    }

    @Override
    public void setState(@NotNull FileEditorState s) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener l) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener l) {
    }

    @Override
    public void dispose() {
        queryPanel.dispose();
    }

    @Override
    public @Nullable <T> T getUserData(@NotNull Key<T> key) {
        return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @org.jspecify.annotations.Nullable T t) {
    }

    @Override
    public void selectNotify() {
        long stamp = file.getTimeStamp();
        if (stamp > myLastCompiledStamp) {
            myLastCompiledStamp = stamp;
            updateCompiledSql();
        }
    }

}
