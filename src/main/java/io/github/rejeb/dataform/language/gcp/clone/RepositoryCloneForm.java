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

import io.github.rejeb.dataform.language.gcp.workspace.DataformRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The choices of the Dataform tab of the Get from Version Control dialog: the repository, where its
 * files come from, and whether they can be fetched. A repository linked to a Git remote is cloned
 * unless a workspace is chosen; any other repository can only be downloaded from a workspace.
 */
final class RepositoryCloneForm {

    private static final String NO_WORKSPACE =
            "This repository has no workspace to download. Create one in the Dataform console first.";

    private @Nullable DataformRepository repository;
    private @NotNull RepositorySource source = RepositorySource.GIT_REMOTE;
    private @NotNull List<String> workspaces = List.of();
    private @Nullable String selectedWorkspace;
    private @Nullable String workspaceProblem;
    private boolean workspacesRequested;

    void selectRepository(@Nullable DataformRepository repository) {
        this.repository = repository;
        source = repository == null || repository.hasGitRemote()
                ? RepositorySource.GIT_REMOTE
                : RepositorySource.WORKSPACE;
        workspaces = List.of();
        selectedWorkspace = null;
        workspaceProblem = null;
        workspacesRequested = false;
    }

    void selectSource(@NotNull RepositorySource source) {
        if (source == RepositorySource.GIT_REMOTE && repository != null && !repository.hasGitRemote()) {
            return;
        }
        this.source = source;
    }

    void workspacesRequested() {
        workspacesRequested = true;
    }

    void workspacesLoaded(@NotNull List<String> workspaces) {
        workspacesRequested = true;
        this.workspaces = List.copyOf(workspaces);
        selectedWorkspace = workspaces.isEmpty() ? null : workspaces.getFirst();
        workspaceProblem = workspaces.isEmpty() ? NO_WORKSPACE : null;
    }

    void workspacesFailed(@NotNull String message) {
        workspacesRequested = true;
        workspaces = List.of();
        selectedWorkspace = null;
        workspaceProblem = message;
    }

    void selectWorkspace(@Nullable String workspace) {
        selectedWorkspace = workspace;
    }

    @Nullable DataformRepository repository() {
        return repository;
    }

    @NotNull RepositorySource source() {
        return source;
    }

    @NotNull List<String> workspaces() {
        return workspaces;
    }

    @Nullable String selectedWorkspace() {
        return selectedWorkspace;
    }

    @Nullable String workspaceProblem() {
        return workspaceProblem;
    }

    boolean needsWorkspaces() {
        return repository != null && source == RepositorySource.WORKSPACE && !workspacesRequested;
    }

    boolean canProceed() {
        return repository != null && (source == RepositorySource.GIT_REMOTE || selectedWorkspace != null);
    }

    @NotNull String actionName() {
        return source == RepositorySource.WORKSPACE ? "Download" : "Clone";
    }
}
