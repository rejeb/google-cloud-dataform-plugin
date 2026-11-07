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
package io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher;

import io.github.rejeb.dataform.language.gcp.workspace.CommitRoute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dispatches user-initiated actions from the GCP toolwindow.
 * Implementations are responsible for running background tasks and publishing
 * results on {@link io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent#TOPIC}.
 */
public interface GcpPanelActionDispatcher {

    /** Loads workspaces, then fetches files and git statuses for the selected workspace. */
    void refreshWorkspaces();

    /**
     * Fetches files from the given workspace, or from the main branch when {@code null}.
     *
     * @param workspaceId target workspace, or {@code null} for main branch
     */
    void fetchFiles(@Nullable String workspaceId);

    /**
     * Pulls remote changes into the local workspace.
     *
     * @param workspaceId target workspace, or {@code null} for main branch
     */
    void pull(@Nullable String workspaceId);

    /**
     * Pushes local files to the given workspace.
     *
     * @param workspaceId target workspace id
     */
    void push(@NotNull String workspaceId);

    /**
     * Commits the given file paths with the provided message.
     *
     * @param workspaceId target workspace id
     * @param paths       file paths to commit
     * @param message     commit message
     */
    void commitChanges(@NotNull String workspaceId, @NotNull List<String> paths, @NotNull String message);

    /**
     * Pushes git commits to the remote for the given workspace.
     *
     * @param workspaceId  target workspace id
     * @param remoteBranch the branch of the remote receiving the commits, {@code null} for the default
     *                     branch of the repository
     */
    void pushGitCommits(@NotNull String workspaceId, @Nullable String remoteBranch);

    /**
     * Reloads the repository when the way its commits reach Git is no longer {@code known}, as when the
     * project has just become a local clone.
     *
     * @param known the route the caller works with, {@code null} when it has none
     */
    void refreshWhenCommitRouteChanges(@Nullable CommitRoute known);

    /**
     * Commits then pushes the given file paths atomically.
     *
     * @param workspaceId target workspace id
     * @param paths       file paths to commit
     * @param message     commit message
     * @param remoteBranch the branch of the remote receiving the commits, {@code null} for the default
     *                     branch of the repository
     */
    void commitAndPush(@NotNull String workspaceId, @NotNull List<String> paths, @NotNull String message,
                       @Nullable String remoteBranch);

    /**
     * Creates a new workspace and publishes the updated workspace list on success.
     *
     * @param workspaceId id of the workspace to create
     */
    void createWorkspace(@NotNull String workspaceId);
}
