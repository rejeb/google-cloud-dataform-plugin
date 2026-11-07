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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService;
import io.github.rejeb.dataform.language.gcp.workspace.repository.WorkspaceRepository;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Clones the Git remote through the Git plugin, or downloads the workspace as a clone task, which
 * keeps its progress in the status bar and on the Welcome screen.
 */
final class DefaultRepositoryCheckoutStarter implements RepositoryCheckoutStarter {

    private final Project project;
    private final WorkspaceRepository repository;
    private final PendingRepositoryLinks links;
    private final Consumer<Path> projectOpener;

    DefaultRepositoryCheckoutStarter(@NotNull Project project, @NotNull WorkspaceRepository repository,
                                     @NotNull PendingRepositoryLinks links, @NotNull Consumer<Path> projectOpener) {
        this.project = project;
        this.repository = repository;
        this.links = links;
        this.projectOpener = projectOpener;
    }

    @Override
    public void start(@NotNull RepositoryCheckoutRequest request, @NotNull CheckoutProvider.Listener listener) {
        if (request.source() == RepositorySource.GIT_REMOTE) {
            GitRemoteCheckout.start(project, request, listener, links);
            return;
        }
        WorkspaceDownload download = new WorkspaceDownload(repository, links, projectOpener);
        CloneableProjectsService.getInstance().runCloneTask(request.directory(),
                new WorkspaceDownloadTask(download, request, listener, project.isDefault() ? null : project));
    }
}
