/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.gcp.workspace;

import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface WorkspaceOperations {

    /**
     * Lists all workspaces in the configured GCP Dataform repository.
     */
    @NotNull List<Workspace> listWorkspaces();

    /**
     * Commits and pushes local workspace changes to the remote repository.
     *
     * @param workspaceId the target workspace ID
     * @throws GcpApiException on API error
     */
    void pushGitCommits(@NotNull String workspaceId);

    /**
     * Fetches file contents from the given workspace or from repo main if {@code null}.
     *
     * @param workspaceId the target workspace ID, or {@code null} to read from the repository
     * @return file contents keyed by relative path
     * @throws GcpApiException on API error
     */
    @NotNull Map<String, String> fetchCode(@Nullable String workspaceId);

    /**
     * Pulls files from the given workspace (or repo main if {@code null})
     * and writes them to the local project, replacing existing files.
     *
     * @param workspaceId the target workspace ID, or {@code null} to sync from repo main
     * @throws GcpApiException on API error
     */
    void pullCode(@Nullable String workspaceId);

    /**
     * Tests the connection to the given Dataform repository config.
     *
     * @throws GcpApiException on failure
     */
    void testConnection(@NotNull DataformRepositoryConfig config);

    /**
     * Pushes local files to the given workspace.
     *
     * @param workspaceId the target workspace ID
     * @throws GcpApiException on API error
     */
    void pushCode(@NotNull String workspaceId);

    /**
     * Creates a new Dataform repository in GCP for the given config.
     *
     * @throws GcpApiException if creation fails
     */
    void createRepository(@NotNull DataformRepositoryConfig config);

    /**
     * Creates a new workspace in the active GCP Dataform repository.
     *
     * @param workspaceId the ID of the workspace to create
     * @throws GcpApiException if creation fails or config is missing
     */
    void createWorkspace(@NotNull String workspaceId);

    /**
     * Returns all files with uncommitted Git changes in the given workspace.
     *
     * @throws GcpApiException on API error
     */
    @NotNull List<UncommittedChange> fetchGitStatuses(@NotNull String workspaceId);

    /**
     * Commits the specified files in the given workspace with the provided message.
     *
     * @param workspaceId the target workspace
     * @param paths       relative paths of files to include in the commit
     * @param message     commit message (must not be blank)
     * @throws GcpApiException on API error
     */
    void commitWorkspaceChanges(
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message
    );

    /**
     * if workspace is not null,
     * Return all files and directories of workspace
     * <p>
     * if the workspace is null,
     * Return all files and directories of repository
     *
     * @param workspaceId the workspace ID
     * @return List<String> paths
     */
    List<String> listAllPaths(@Nullable String workspaceId);

    @NotNull
    String getFileContent(@Nullable String workspaceId, @NotNull String filePath);

}
