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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowCreationResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import org.jetbrains.annotations.NotNull;

/**
 * Low-level GCP API contract for Dataform workflow operations.
 * Implementations must not depend on IntelliJ services.
 */
public interface WorkflowRepository {

    /**
     * Compiles the workspace and creates a workflow run.
     *
     * @return the GCP resource name of the created workflow run
     * @throws GcpApiException on API error
     */
    @NotNull WorkflowCreationResult createWorkflowRun(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull WorkflowRunRequest request
    );

    /**
     * Returns a progress snapshot: run state + all action results.
     *
     * @throws GcpApiException on API error
     */
    @NotNull WorkflowInvocationProgress getWorkflowRunProgress(@NotNull WorkflowCreationResult workflowRun);

    /**
     * Cancels a running workflow.
     *
     * @throws GcpApiException on API error
     */
    void cancelWorkflowRun(@NotNull String workflowRunName);
}
