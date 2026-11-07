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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The repository and workspace chosen in the Dataform tool window, shared by its pickers. The
 * choice is stored in {@link GcpRepositorySettings}; the workspaces of the active repository are
 * kept as they arrive. Every read is safe from any thread.
 */
public final class WorkspaceSelection {

    /**
     * The name shown when no workspace is selected and the repository's main branch is used.
     */
    public static final String MAIN_BRANCH = "No workspace (main)";

    /**
     * The name shown when no workspace is selected in a repository linked to a Git remote, with no
     * local clone to read it from.
     */
    public static final String NO_WORKSPACE = "No workspace";

    private static final String MAIN_BRANCH_CHOICE = "No workspace (repository main)";

    private final Project project;
    private final Runnable onSelectionChanged;
    private volatile @Nullable List<String> workspaceIds;
    private volatile @NotNull MainBranchSource mainBranchSource = MainBranchSource.DATAFORM;

    /**
     * Listens for loaded workspaces until {@code parent} is disposed; {@code onSelectionChanged}
     * runs after the user picks another repository or workspace.
     */
    public WorkspaceSelection(@NotNull Project project, @NotNull Runnable onSelectionChanged, @NotNull Disposable parent) {
        this.project = project;
        this.onSelectionChanged = onSelectionChanged;
        project.getMessageBus()
                .connect(parent)
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onMainBranchLoaded(@NotNull MainBranchSource source) {
                        setMainBranchSource(source);
                    }

                    @Override
                    public void onWorkspacesLoaded(@NotNull List<Workspace> workspaces) {
                        setWorkspaces(workspaces);
                    }
                });
    }

    /**
     * Every configured repository.
     */
    public @NotNull List<DataformRepositoryConfig> repositories() {
        return settings().getAllConfigs();
    }

    /**
     * The repository the tool window shows, if any.
     */
    public @Nullable DataformRepositoryConfig activeRepository() {
        return settings().getActiveConfig();
    }

    /**
     * Makes the given repository the active one; its workspaces are unknown until they load again.
     */
    public void selectRepository(@NotNull DataformRepositoryConfig repository) {
        if (repository.repositoryConfigId().equals(settings().getActiveRepositoryId())) {
            return;
        }
        settings().setActiveRepositoryId(repository.repositoryConfigId());
        startLoading();
        onSelectionChanged.run();
    }

    /**
     * The workspace IDs of the active repository, or {@code null} while they load.
     */
    public @Nullable List<String> workspaceIds() {
        return workspaceIds;
    }

    /**
     * The selected workspace, or {@code null} for the repository's main branch.
     */
    public @Nullable String selectedWorkspaceId() {
        return settings().getSelectedWorkspaceId();
    }

    /**
     * Selects a workspace, or the main branch for {@code null}.
     */
    public void selectWorkspace(@Nullable String workspaceId) {
        if (Objects.equals(workspaceId, selectedWorkspaceId())) {
            return;
        }
        settings().setSelectedWorkspaceId(workspaceId);
        onSelectionChanged.run();
        ActivityTracker.getInstance().inc();
    }

    /**
     * Keeps the loaded workspaces; a selected workspace that no longer exists falls back to the
     * main branch.
     */
    public void setWorkspaces(@NotNull List<Workspace> workspaces) {
        List<String> ids = workspaces.stream().map(Workspace::workspaceId).toList();
        workspaceIds = ids;
        String selected = selectedWorkspaceId();
        if (selected != null && !ids.contains(selected)) {
            settings().setSelectedWorkspaceId(null);
        }
        ActivityTracker.getInstance().inc();
    }

    /**
     * Keeps where the active repository is read from when no workspace is selected.
     */
    public void setMainBranchSource(@NotNull MainBranchSource source) {
        mainBranchSource = source;
        ActivityTracker.getInstance().inc();
    }

    /**
     * The choice of the main branch in the workspace picker, {@code null} when it cannot be read.
     */
    public @Nullable String mainBranchChoice() {
        return switch (mainBranchSource) {
            case MainBranchSource.Dataform dataform -> MAIN_BRANCH_CHOICE;
            case MainBranchSource.GitRemote gitRemote -> "Git remote (" + gitRemote.branch().trackingName() + ")";
            case MainBranchSource.Unavailable unavailable -> null;
        };
    }

    /**
     * The name shown when no workspace is selected.
     */
    public @NotNull String noWorkspaceName() {
        return switch (mainBranchSource) {
            case MainBranchSource.Dataform dataform -> MAIN_BRANCH;
            case MainBranchSource.GitRemote gitRemote -> gitRemote.branch().trackingName();
            case MainBranchSource.Unavailable unavailable -> NO_WORKSPACE;
        };
    }

    /**
     * Forgets the workspaces and where the main branch comes from until the next load.
     */
    public void startLoading() {
        workspaceIds = null;
        mainBranchSource = MainBranchSource.DATAFORM;
    }

    private @NotNull GcpRepositorySettings settings() {
        return GcpRepositorySettings.getInstance(project);
    }
}
