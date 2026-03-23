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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.projectWizard.DataformFacetType;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.ModuleUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CompareFileWithRemoteAction extends AnAction {

    public CompareFileWithRemoteAction() {
        super("Compare File with Remote");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // update() doit tourner sur BGT pour ne pas bloquer l'EDT
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        // Cacher si pas de projet ou pas de fichier
        if (project == null || file == null || file.isDirectory()) {
            e.getPresentation().setVisible(false);
            return;
        }

        // Visible uniquement dans un projet Dataform (facet détecté)
        boolean isDataformProject = isDataformProject(project, file);
        e.getPresentation().setVisible(isDataformProject);
        if (!isDataformProject) return;

        // Grisé si aucun repository configuré
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(project);
        boolean hasConfig = settings.getConfig() != null
                && settings.getConfig().repositoryId() != null
                && !settings.getConfig().repositoryId().isBlank();
        e.getPresentation().setEnabled(hasConfig);

        e.getPresentation().setText(hasConfig
                ? "Compare File with Remote"
                : "Compare File with Remote (no repository configured)");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile localFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || localFile == null) return;

        // Chemin relatif depuis la racine du projet
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return;
        String relativePath = com.intellij.openapi.vfs.VfsUtilCore
                .getRelativePath(localFile, baseDir, '/');
        if (relativePath == null) return;

        List<String> cachedFiles = DataformGcpService.getInstance(project).getCachedFiles();
        String remoteContent = "";

        FileType fileType = FileTypeManager.getInstance()
                .getFileTypeByFileName(localFile.getName());

        // Contenu distant : vide si pas dans le cache
        var remoteVfContent = remoteContent != null
                ? DiffContentFactory.getInstance().create(remoteContent, fileType)
                : DiffContentFactory.getInstance().create("", fileType);

        DiffManager.getInstance().showDiff(
                project,
                new SimpleDiffRequest(
                        "Compare: " + relativePath,
                        DiffContentFactory.getInstance().create(project, localFile),
                        remoteVfContent,
                        "Local",
                        "Remote (GCP)"
                )
        );
    }

    private boolean isDataformProject(@NotNull Project project, @NotNull VirtualFile file) {
        var module = ModuleUtilCore.findModuleForFile(file, project);
        if (module == null) return false;
        return FacetManager.getInstance(module)
                .getFacetByType(DataformFacetType.ID) != null;
    }
}