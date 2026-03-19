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
package io.github.rejeb.dataform.language.gcp.workspace.repository;

import io.github.rejeb.dataform.language.gcp.common.CommitAuthorConfig;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WorkspaceRepository {

    /**
     * Fetches all workspaces for the given GCP Dataform repository.
     *
     * @throws GcpApiException on network or API error
     */
    @NotNull List<Workspace> findAll(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    );

    /**
     * Commits and pushes local workspace changes to the remote repository.
     *
     * @throws GcpApiException on network or API error
     */
    void pushGitCommits(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    );

    /**
     * Pulls changes from the remote repository into the workspace.
     *
     * @throws GcpApiException on network or API error
     */
    void pull(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull CommitAuthorConfig author
    );

    /**
     * Reads the content of the given file paths directly from the repository (no workspace).
     *
     * @return map of relative path → file content (UTF-8); absent paths are skipped
     * @throws GcpApiException if the client cannot be created or a fatal API error occurs
     */
    @NotNull Map<String, String> readFilesFromRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull List<String> paths
    );

    /**
     * Lists and reads all files from the GCP Dataform repository (main branch)
     * or from a workspace.
     *
     * @param workspaceId workspace ID, or {@code null} to read from repo main branch
     * @return map of relative path → file content
     * @throws GcpApiException on API error
     */
    @NotNull Map<String, String> readAllFiles(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    );

    @NotNull List<String> listAllPaths(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId
    );

    void push(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull Map<String, String> filesToWrite,
            @NotNull Set<String> pathsToDelete
    );

    /**
     * Creates a new Dataform repository in GCP.
     *
     * @throws GcpApiException if the API call fails (e.g. already exists, permission denied)
     */
    void createRepository(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    );

    /**
     * Creates a new workspace in the given GCP Dataform repository.
     *
     * @param workspaceId ID of the workspace to create (must be unique within the repository)
     * @throws GcpApiException if the API call fails (e.g. already exists, permission denied)
     */
    void createWorkspace(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    );

    /**
     * Returns all files with uncommitted Git changes in the given workspace.
     *
     * @throws GcpApiException on API error
     */
    @NotNull List<UncommittedChange> fetchFileGitStatuses(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId
    );

    /**
     * Commits the specified files in the given workspace with the provided message.
     *
     * @throws GcpApiException on API error
     */
    void commitWorkspaceChanges(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message,
            @NotNull CommitAuthorConfig author
    );

    @NotNull
    String getFileContent(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @Nullable String workspaceId,
            @NotNull String filePath);
}
