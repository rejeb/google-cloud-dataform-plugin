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
package io.github.rejeb.dataform.language.gcp.execution.workflow;

import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.common.GcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import io.github.rejeb.dataform.language.gcp.execution.workflow.repository.WorkflowRepository;
import org.jetbrains.annotations.NotNull;

public final class WorkflowOperationsHandler implements WorkflowOperations {

    private final WorkflowRepository repository;
    private final GcpConfigProvider configProvider;

    public WorkflowOperationsHandler(
            @NotNull WorkflowRepository repository,
            @NotNull GcpConfigProvider configProvider
    ) {
        this.repository = repository;
        this.configProvider = configProvider;
    }

    @Override
    @NotNull
    public String createWorkflowRun(@NotNull WorkflowRunRequest request) {
        GcpConfig config = requireConfig();
        return repository.createWorkflowRun(
                config.projectId(), config.location(), config.repositoryId(), request);
    }

    @Override
    @NotNull
    public WorkflowInvocationProgress getWorkflowRunProgress(@NotNull String workflowRunName) {
        return repository.getWorkflowRunProgress(workflowRunName);
    }

    @Override
    public void cancelWorkflowRun(@NotNull String workflowRunName) {
        repository.cancelWorkflowRun(workflowRunName);
    }

    @NotNull
    private GcpConfig requireConfig() {
        String projectId    = configProvider.getProjectId();
        String location     = configProvider.getLocation();
        String repositoryId = configProvider.getRepositoryId();
        if (projectId == null || location == null || repositoryId == null) {
            throw new GcpApiException("No active repository config — configure a repository first.");
        }
        return new GcpConfig(projectId, location, repositoryId);
    }

    private record GcpConfig(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId
    ) {}
}
