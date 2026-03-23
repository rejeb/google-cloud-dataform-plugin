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

import com.google.cloud.dataform.v1beta1.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
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
    public WorkflowCreationResult createWorkflowRun(
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

            return new WorkflowCreationResult(invocation.getName(), wsName);
        } catch (Exception e) {
            throw new GcpApiException("Error creating workflow run.", e);
        }
    }

    @Override
    @NotNull
    public WorkflowInvocationProgress getWorkflowRunProgress(@NotNull WorkflowCreationResult workflowRun) {
        try {
            WorkflowInvocation inv = client.getWorkflowInvocation(
                    GetWorkflowInvocationRequest.newBuilder()
                            .setName(workflowRun.invocationName())
                            .build()
            );

            List<InvocationActionResult> actions = new ArrayList<>();
            for (WorkflowInvocationAction action : client.queryWorkflowInvocationActions(
                    QueryWorkflowInvocationActionsRequest.newBuilder()
                            .setName(workflowRun.invocationName())
                            .build()
            ).iterateAll()) {
                Target t = action.getTarget();
                String label = t.getDatabase().isEmpty()
                        ? t.getSchema() + "." + t.getName()
                        : t.getDatabase() + "." + t.getSchema() + "." + t.getName();

                Instant startTime = null;
                Instant endTime = null;
                if (action.hasInvocationTiming()) {
                    com.google.type.Interval timing = action.getInvocationTiming();
                    if (timing.hasStartTime() && timing.getStartTime().getSeconds() > 0) {
                        startTime = Instant.ofEpochSecond(
                                timing.getStartTime().getSeconds(),
                                timing.getStartTime().getNanos());
                    }
                    if (timing.hasEndTime() && timing.getEndTime().getSeconds() > 0) {
                        endTime = Instant.ofEpochSecond(
                                timing.getEndTime().getSeconds(),
                                timing.getEndTime().getNanos());
                    }
                }

                String jobId       = null;
                String jobProject  = null;
                String sqlScript   = null;


                if (action.hasBigqueryAction()) {
                    String raw = action.getBigqueryAction().getJobId();
                    jobId = raw.isBlank() ? null : raw;
                    String sql = action.getBigqueryAction().getSqlScript();
                    sqlScript = sql.isBlank() ? null : sql;
                }

                jobProject = !t.getDatabase().isEmpty() ? t.getDatabase(): null;

                actions.add(new InvocationActionResult(
                        label, mapActionState(action.getState()),
                        action.getState() == WorkflowInvocationAction.State.FAILED ? action.getFailureReason() : null,
                        startTime, endTime,
                        jobId, jobProject, null,
                        sqlScript
                ));
            }

            InvocationSummary summary = buildSummary(inv, workflowRun.workspaceFullName());
            return new WorkflowInvocationProgress(workflowRun.invocationName(), mapRunState(inv.getState()), actions, summary);
        } catch (Exception e) {
            throw new GcpApiException("Error fetching workflow run progress.", e);
        }
    }

    private static InvocationSummary buildSummary(
            @NotNull WorkflowInvocation inv,
            @Nullable String workspaceFullName
    ) {
        String compilationResultName = inv.getCompilationResult();

        String sourceType = (workspaceFullName != null) ? "WORKSPACE" : "COMPILATION_RESULT";

        Instant startTime = Instant.now();
        Instant endTime = null;

        if (inv.hasInvocationTiming()) {
            com.google.type.Interval timing = inv.getInvocationTiming();
            if (timing.hasStartTime()) {
                startTime = Instant.ofEpochSecond(
                        timing.getStartTime().getSeconds(),
                        timing.getStartTime().getNanos()
                );
            }
            if (timing.hasEndTime()) {
                com.google.protobuf.Timestamp ts = timing.getEndTime();
                if (ts.getSeconds() > 0) {
                    endTime = Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
                }
            }
        }

        String contents = inv.hasInvocationConfig()
                ? buildContentsLabel(inv.getInvocationConfig())
                : "Full workflow";

        return new InvocationSummary(
                inv.getName(),
                compilationResultName,
                sourceType,
                workspaceFullName,
                contents,
                startTime,
                endTime
        );
    }

    private static String buildContentsLabel(@NotNull InvocationConfig cfg) {
        List<String> parts = new ArrayList<>();
        if (!cfg.getIncludedTagsList().isEmpty()) {
            parts.add("tags: " + String.join(", ", cfg.getIncludedTagsList()));
        }
        if (!cfg.getIncludedTargetsList().isEmpty()) {
            parts.add("targets: " + cfg.getIncludedTargetsList().stream()
                    .map(t -> t.getSchema() + "." + t.getName())
                    .collect(java.util.stream.Collectors.joining(", ")));
        }
        if (cfg.getTransitiveDependenciesIncluded()) parts.add("+deps");
        if (cfg.getTransitiveDependentsIncluded()) parts.add("+dependents");
        if (cfg.getFullyRefreshIncrementalTablesEnabled()) parts.add("full refresh");
        return parts.isEmpty() ? "All Actions" : String.join(" | ", parts);
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
