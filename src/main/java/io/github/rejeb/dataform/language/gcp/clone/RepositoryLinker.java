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
package io.github.rejeb.dataform.language.gcp.clone;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.setup.DataformPackageInstaller;
import io.github.rejeb.dataform.language.setup.DataformPackageJson;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Makes a project fetched from Google Cloud ready to use: its repository becomes the active one of
 * the Dataform tool window, and its packages are installed when it declares some.
 */
final class RepositoryLinker {

    private static final String NODE_MODULES = "node_modules";

    private RepositoryLinker() {
    }

    static void link(@NotNull Project project, @NotNull RepositoryLink link) {
        activate(GcpRepositorySettings.getInstance(project), link);
        installPackages(project);
        ApplicationManager.getApplication().invokeLater(
                () -> project.getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onRepositoryChanged(),
                project.getDisposed());
    }

    private static void activate(@NotNull GcpRepositorySettings settings, @NotNull RepositoryLink link) {
        DataformRepositoryConfig config = settings.getAllConfigs().stream()
                .filter(existing -> isSameRepository(existing, link))
                .findFirst()
                .orElseGet(() -> add(settings, link));
        settings.setActiveRepositoryId(config.repositoryConfigId());
        if (link.workspaceId() != null) {
            settings.setSelectedWorkspaceId(link.workspaceId());
        }
    }

    private static boolean isSameRepository(@NotNull DataformRepositoryConfig config, @NotNull RepositoryLink link) {
        return config.projectId().equals(link.projectId())
                && config.location().equals(link.location())
                && config.repositoryId().equals(link.repositoryId());
    }

    private static @NotNull DataformRepositoryConfig add(@NotNull GcpRepositorySettings settings,
                                                         @NotNull RepositoryLink link) {
        DataformRepositoryConfig config = new DataformRepositoryConfig(UUID.randomUUID().toString(),
                link.repositoryId(), link.projectId(), link.repositoryId(), link.location(), link.serviceAccount());
        List<DataformRepositoryConfig> configs = new ArrayList<>(settings.getAllConfigs());
        configs.add(config);
        settings.saveAllConfigs(configs);
        return config;
    }

    private static void installPackages(@NotNull Project project) {
        VirtualFile directory = ProjectUtil.guessProjectDir(project);
        if (directory != null
                && directory.findChild(DataformPackageJson.FILE_NAME) != null
                && directory.findChild(NODE_MODULES) == null) {
            DataformPackageInstaller.getInstance(project).installAsync(directory);
        }
    }
}
