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
package io.github.rejeb.dataform.language.gcp.service;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface DataformGcpService {

    /**
     * @return the service instance for the given project
     */
    static DataformGcpService getInstance(@NotNull Project project) {
        return project.getService(DataformGcpService.class);
    }

    /**
     * Lists all workspaces in the configured GCP Dataform repository.
     * <p>Must be called off the EDT.
     *
     * @return list of workspaces, or empty list if config is missing or the API call fails
     */
    @NotNull List<Workspace> listWorkspaces();
    /**
     * Pushes local Git commits in the given workspace to the remote repository.
     * <p>Must be called off the EDT.
     *
     * @param workspaceId the target workspace ID
     */
    void commitCode(@NotNull String workspaceId);

    /**
     * Pushes local Git commits in the given workspace to the remote repository.
     * <p>Must be called off the EDT.
     *
     * @param workspaceId the target workspace ID
     */
    void pushCode(@NotNull String workspaceId);

    /**
     * Pulls changes from the remote repository into the given workspace.
     * <p>Must be called off the EDT.
     *
     * @param workspaceId the target workspace ID
     */
    @NotNull Map<String, String> fetchCode(@Nullable String workspaceId);

    /**
     * Pulls files from the given workspace (or repo main if {@code null})
     * and writes them to the local project, replacing existing files.
     * <p>Must be called off the EDT.
     *
     * @param workspaceId the target workspace ID, or {@code null} to sync from repo main
     */
    void pullCode(@Nullable String workspaceId);

    /**
     * Tests the connection to the given Dataform repository config
     * by listing workspaces. Throws {@link GcpApiException} on failure.
     */
    void testConnection(@NotNull DataformRepositoryConfig config);

    /**
     * Fetches files from GCP in background and updates the internal cache.
     * Notifies the given callback on the EDT when done.
     *
     * @param workspaceId workspace to fetch from, or {@code null} for repo main
     * @param onDone      called on EDT with the fetched files (empty map on error)
     */
    void refreshFilesAsync(
            @Nullable String workspaceId,
            @NotNull java.util.function.Consumer<Map<String, String>> onDone
    );

    /**
     * @return the last successfully fetched files, or an empty map if cache is empty
     */
    @NotNull Map<String, String> getCachedFiles();

    /**
     * Invalidates the file cache.
     */
    void invalidateCache();

    /** @return {@code true} if a background file refresh is currently running */
    boolean isLoading();

}
