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
package io.github.rejeb.dataform.language.gcp.action;

import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.WorkspaceFileActions;
import io.github.rejeb.dataform.language.projectWizard.DataformFacetType;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a project file next to its version in the selected workspace, or in the main branch when
 * no workspace is selected, as Compare with Local does in the Files tab.
 */
public class CompareFileWithRemoteAction extends AnAction {

    public CompareFileWithRemoteAction() {
        super("Compare File with Remote");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || file == null || file.isDirectory()) {
            e.getPresentation().setVisible(false);
            return;
        }

        boolean isDataformProject = isDataformProject(project, file);
        e.getPresentation().setVisible(isDataformProject);
        if (!isDataformProject) return;

        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(project);
        boolean hasConfig = settings.getActiveConfig() != null
                && !settings.getActiveConfig().repositoryId().isBlank();
        e.getPresentation().setEnabled(hasConfig);

        e.getPresentation().setText(hasConfig
                ? "Compare File with Remote"
                : "Compare File with Remote (No Repository Configured)");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile localFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || localFile == null) return;

        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        String relativePath = baseDir == null ? null : VfsUtilCore.getRelativePath(localFile, baseDir, '/');
        if (relativePath == null) return;

        new WorkspaceFileActions(project, () -> null).compareWithLocal(relativePath);
    }

    private boolean isDataformProject(@NotNull Project project, @NotNull VirtualFile file) {
        var module = ModuleUtilCore.findModuleForFile(file, project);
        if (module == null) return false;
        return FacetManager.getInstance(module)
                .getFacetByType(DataformFacetType.ID) != null;
    }
}
