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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.util.FileEditors;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opens the project-wide Dataform lineage view in a dedicated editor tab.
 */
public final class OpenLineageViewAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        open(project, null);
    }

    /**
     * Opens the project lineage editor as it was left, the marked tables included.
     *
     * @param source the source the editor switches to, {@code null} to keep the one it shows
     */
    public static void open(@NotNull Project project, @Nullable LineageSource source) {
        showIn(openEditor(project), source, null);
    }

    /**
     * Opens the project lineage editor on tables: one is selected and centred, several are marked.
     *
     * @param source  the source the editor switches to, {@code null} to keep the one it shows
     * @param nodeIds the tables to show, none to clear the marks
     */
    public static void open(@NotNull Project project, @Nullable LineageSource source, @NotNull List<String> nodeIds) {
        showIn(openEditor(project), source, nodeIds);
    }

    private static @NotNull FileEditor[] openEditor(@NotNull Project project) {
        LineageProjectVirtualFile file = LineageProjectVirtualFile.getInstance();
        FileEditors.open(project, file);
        return FileEditorManager.getInstance(project).getEditors(file);
    }

    static void showIn(@NotNull FileEditor[] editors, @Nullable LineageSource source, @Nullable List<String> nodeIds) {
        for (FileEditor editor : editors) {
            if (!(editor instanceof LineageFileEditor lineage)) continue;
            if (nodeIds != null) lineage.showNodes(source, nodeIds);
            else if (source != null) lineage.showSource(source);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean available = project != null && Utils.isDataformProject(project);
        e.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
