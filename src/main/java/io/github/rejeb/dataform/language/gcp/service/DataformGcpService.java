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

    static DataformGcpService getInstance(@NotNull Project project) {
        return project.getService(DataformGcpService.class);
    }

    /** Lists all workspaces. Must be called off the EDT. */
    @NotNull List<Workspace> listWorkspaces();

    /** Commits local workspace changes. Must be called off the EDT. */
    void commitCode(@NotNull String workspaceId);

    /** Pushes committed changes to remote. Must be called off the EDT. */
    void pushCode(@NotNull String workspaceId);

    /** Fetches files from GCP. Must be called off the EDT. */
    @NotNull Map<String, String> fetchCode(@Nullable String workspaceId);

    /** Pulls files from GCP and writes them locally. Must be called off the EDT. */
    void pullCode(@Nullable String workspaceId);

    /** Tests connectivity to the given config. Throws {@link GcpApiException} on failure. */
    void testConnection(@NotNull DataformRepositoryConfig config);

    /** Fetches files asynchronously and notifies {@code onDone} on the EDT. */
    void refreshFilesAsync(
            @Nullable String workspaceId,
            @NotNull java.util.function.Consumer<Map<String, String>> onDone
    );

    /** @return last successfully fetched files, or empty map */
    @NotNull Map<String, String> getCachedFiles();

    /** Invalidates the file cache. */
    void invalidateCache();

    /** @return {@code true} if a background file refresh is currently running */
    boolean isLoading();

    /**
     * Creates a new Dataform repository in GCP for the given config.
     * Must be called off the EDT.
     */
    void createGcpRepository(@NotNull DataformRepositoryConfig config);

    /**
     * Creates a new workspace in the active GCP Dataform repository.
     * Must be called off the EDT.
     *
     * @param workspaceId the ID of the workspace to create
     * @throws GcpApiException if creation fails (already exists, permissions, network…)
     */
    void createWorkspace(@NotNull String workspaceId);
}
