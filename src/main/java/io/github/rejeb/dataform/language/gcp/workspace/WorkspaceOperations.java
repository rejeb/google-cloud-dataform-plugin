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
     * Pushes local Git commits in the given workspace to the remote repository.
     *
     * @param workspaceId the target workspace ID
     * @throws GcpApiException on API error
     */
    void commitCode(@NotNull String workspaceId);

    /**
     * Pulls changes from the remote into the given workspace,
     * or reads file contents directly from the repository if {@code workspaceId} is {@code null}.
     * <p>When {@code workspaceId} is {@code null}, returns a map of relative path → file content
     * intended for display only — local files are never modified.
     *
     * @param workspaceId the target workspace ID, or {@code null} to read from the repository
     * @return file contents keyed by relative path when {@code workspaceId} is {@code null},
     *         empty map otherwise
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

    void testConnection(@NotNull DataformRepositoryConfig config);

    void pushCode(@NotNull String workspaceId);

    void createRepository(@NotNull DataformRepositoryConfig config);
}
