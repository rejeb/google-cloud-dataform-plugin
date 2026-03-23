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
package io.github.rejeb.dataform.language.gcp.execution.workflow;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowCreationResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import org.jetbrains.annotations.NotNull;

/**
 * Business-level contract for triggering and monitoring Dataform workflow runs.
 * Config resolution (projectId, location, repositoryId) is handled by implementations.
 */
public interface WorkflowOperations {

    /**
     * Compiles the workspace and creates a workflow run.
     * Must be called off the EDT.
     *
     * @return the GCP resource name of the created run
     */
    @NotNull WorkflowCreationResult createWorkflowRun(@NotNull WorkflowRunRequest request);

    /**
     * Returns a progress snapshot for the given run.
     * Must be called off the EDT.
     */
    @NotNull WorkflowInvocationProgress getWorkflowRunProgress(@NotNull WorkflowCreationResult workflowRun);

    /**
     * Cancels a running workflow.
     * Must be called off the EDT.
     */
    void cancelWorkflowRun(@NotNull String workflowRunName);
}
