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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.settings.WorkflowSettingsGcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import io.github.rejeb.dataform.language.gcp.workspace.WorkspaceOperationsHandler;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.workspace.repository.GcpDataformWorkspaceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class DataformGcpServiceImpl implements DataformGcpService {

    private static final Logger LOG = Logger.getInstance(DataformGcpServiceImpl.class);

    private final WorkspaceOperationsHandler workspaceOperations;

    public DataformGcpServiceImpl(@NotNull Project project) {
        var configProvider = new WorkflowSettingsGcpConfigProvider(
                GcpRepositorySettings.getInstance(project)
        );
        this.workspaceOperations = new WorkspaceOperationsHandler(
                new GcpDataformWorkspaceRepository(),
                configProvider,
                project
        );
    }

    @Override
    @NotNull
    public List<Workspace> listWorkspaces() {
        try {
            return workspaceOperations.listWorkspaces();
        } catch (GcpApiException e) {
            LOG.error("Failed to list Dataform workspaces", e);
            return List.of();
        }
    }

    @Override
    public void pushCode(@NotNull String workspaceId) {
        try {
            workspaceOperations.pushCode(workspaceId);
        } catch (GcpApiException e) {
            LOG.error("Failed to push code to Dataform workspace: " + workspaceId, e);
        }
    }

    @Override
    @NotNull
    public Map<String, String> pullCode(@Nullable String workspaceId) {
        try {
            return workspaceOperations.pullCode(workspaceId);
        } catch (GcpApiException e) {
            LOG.error("Failed to pull code from Dataform workspace: " + workspaceId, e);
            return Map.of();
        }
    }

    @Override
    public void syncCode(@Nullable String workspaceId) {
        try {
            workspaceOperations.syncCode(workspaceId);
        } catch (GcpApiException e) {
            LOG.error("Failed to sync code from Dataform: " + workspaceId, e);
        }
    }

    @Override
    public void testConnection(@NotNull DataformRepositoryConfig config) {
        workspaceOperations.testConnection(config);
    }


}
