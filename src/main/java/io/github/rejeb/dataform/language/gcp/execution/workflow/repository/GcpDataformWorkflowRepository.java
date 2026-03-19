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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import com.google.cloud.dataform.v1beta1.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GcpDataformWorkflowRepository implements WorkflowRepository, Disposable {
    private static final Logger LOG = Logger.getInstance(GcpDataformWorkflowRepository.class);

    private final DataformClient client;

    public GcpDataformWorkflowRepository() {
        try {
            this.client = DataformClient.create(DataformSettings.newBuilder().build());
        } catch (IOException e) {
            throw new GcpApiException(
                    "Failed to create DataformClient — check Application Default Credentials.", e);
        }
    }

    @Override
    public void dispose() {
        client.close();
    }

    @Override
    @NotNull
    public String createWorkflowRun(
            @NotNull String projectId,
            @NotNull String location,
            @NotNull String repositoryId,
            @NotNull WorkflowRunRequest request
    ) {
        try {
            String repoName = RepositoryName.of(projectId, location, repositoryId).toString();
            String wsName = WorkspaceName.of(projectId, location, repositoryId, request.workspaceId()).toString();
            ensureNpmPackagesInstalled(wsName);
            CompilationResult compilation = client.createCompilationResult(
                    CreateCompilationResultRequest.newBuilder()
                            .setParent(repoName)
                            .setCompilationResult(
                                    CompilationResult.newBuilder()
                                            .setWorkspace(wsName)
                                            .build()
                            )
                            .build()
            );

            InvocationConfig.Builder config = InvocationConfig.newBuilder()
                    .setTransitiveDependenciesIncluded(request.transitiveDependenciesIncluded())
                    .setTransitiveDependentsIncluded(request.transitiveDependentsIncluded())
                    .setFullyRefreshIncrementalTablesEnabled(request.fullyRefreshIncrementalTables());

            request.includedTags().forEach(config::addIncludedTags);

            for (InvocationTarget t : request.includedTargets()) {
                Target.Builder tb = Target.newBuilder()
                        .setSchema(t.schema())
                        .setName(t.name());
                if (t.database() != null) {
                    tb.setDatabase(t.database());
                }
                config.addIncludedTargets(tb.build());
            }

            WorkflowInvocation invocation = client.createWorkflowInvocation(
                    CreateWorkflowInvocationRequest.newBuilder()
                            .setParent(repoName)
                            .setWorkflowInvocation(
                                    WorkflowInvocation.newBuilder()
                                            .setCompilationResult(compilation.getName())
                                            .setInvocationConfig(config.build())
                                            .build()
                            )
                            .build()
            );

            return invocation.getName();
        } catch (Exception e) {
            throw new GcpApiException("Error creating workflow run.", e);
        }
    }

    @Override
    @NotNull
    public WorkflowInvocationProgress getWorkflowRunProgress(@NotNull String workflowRunName) {
        try {
            WorkflowInvocation inv = client.getWorkflowInvocation(
                    GetWorkflowInvocationRequest.newBuilder()
                            .setName(workflowRunName)
                            .build()
            );

            List<InvocationActionResult> actions = new ArrayList<>();
            Iterable<WorkflowInvocationAction> actionsProgress = client.queryWorkflowInvocationActions(
                    QueryWorkflowInvocationActionsRequest.newBuilder()
                            .setName(workflowRunName)
                            .build()
            ).iterateAll();
            for (WorkflowInvocationAction action : actionsProgress) {
                Target t = action.getTarget();
                String label = t.getDatabase().isEmpty()
                        ? t.getSchema() + "." + t.getName()
                        : t.getDatabase() + "." + t.getSchema() + "." + t.getName();

                actions.add(new InvocationActionResult(
                        label,
                        mapActionState(action.getState()),
                        action.getState() == WorkflowInvocationAction.State.FAILED
                                ? action.getFailureReason()
                                : null
                ));
            }

            return new WorkflowInvocationProgress(workflowRunName, mapRunState(inv.getState()), actions);
        } catch (Exception e) {
            throw new GcpApiException("Error fetching workflow run progress.", e);
        }
    }

    @Override
    public void cancelWorkflowRun(@NotNull String workflowRunName) {
        try {
            client.cancelWorkflowInvocation(
                    CancelWorkflowInvocationRequest.newBuilder()
                            .setName(workflowRunName)
                            .build()
            );
        } catch (Exception e) {
            throw new GcpApiException("Error cancelling workflow run.", e);
        }
    }

    private static WorkflowInvocationState mapRunState(WorkflowInvocation.State s) {
        return switch (s) {
            case RUNNING -> WorkflowInvocationState.RUNNING;
            case SUCCEEDED -> WorkflowInvocationState.SUCCEEDED;
            case FAILED -> WorkflowInvocationState.FAILED;
            case CANCELLED -> WorkflowInvocationState.CANCELLED;
            default -> WorkflowInvocationState.UNKNOWN;
        };
    }

    private static InvocationActionState mapActionState(WorkflowInvocationAction.State s) {
        return switch (s) {
            case PENDING -> InvocationActionState.PENDING;
            case RUNNING -> InvocationActionState.RUNNING;
            case SKIPPED -> InvocationActionState.SKIPPED;
            case DISABLED -> InvocationActionState.DISABLED;
            case SUCCEEDED -> InvocationActionState.SUCCEEDED;
            case CANCELLED -> InvocationActionState.CANCELLED;
            case FAILED -> InvocationActionState.FAILED;
            default -> InvocationActionState.UNKNOWN;
        };
    }

    /**
     * Checks whether node_modules/@dataform/core exists in the workspace.
     * If not, triggers installNpmPackages and waits for completion.
     */
    private void ensureNpmPackagesInstalled(
            @NotNull String workspaceName
    ) {
        if (!isDataformCoreInstalled(workspaceName)) {
            LOG.info("@dataform/core not found in workspace, running installNpmPackages: " + workspaceName);
            client.installNpmPackages(
                    InstallNpmPackagesRequest.newBuilder()
                            .setWorkspace(workspaceName)
                            .build()
            );
            LOG.info("installNpmPackages completed for workspace: " + workspaceName);
        }
    }

    /**
     * Returns true if node_modules/@dataform/core is present in the workspace.
     * Uses QueryDirectoryContents on "node_modules/@dataform" to avoid listing
     * all of node_modules (potentially thousands of entries).
     */
    private boolean isDataformCoreInstalled(@NotNull String workspaceName) {
        try {
            QueryDirectoryContentsRequest request = QueryDirectoryContentsRequest.newBuilder()
                    .setWorkspace(workspaceName)
                    .setPath("node_modules/@dataform")
                    .build();
            // If the directory exists and contains "core", packages are installed
            for (DirectoryEntry entry : client.queryDirectoryContents(request).iterateAll()) {
                if (entry.hasDirectory() && "core".equals(entry.getDirectory())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // Directory doesn't exist → not installed
            LOG.info("node_modules/@dataform not found in workspace, npm install needed.");
            return false;
        }
    }
}
