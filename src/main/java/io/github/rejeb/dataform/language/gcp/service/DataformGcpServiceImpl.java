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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.execution.workflow.BigQueryJobOperations;
import io.github.rejeb.dataform.language.gcp.execution.workflow.BigQueryJobOperationsHandler;
import io.github.rejeb.dataform.language.gcp.execution.workflow.WorkflowOperations;
import io.github.rejeb.dataform.language.gcp.execution.workflow.WorkflowOperationsHandler;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowCreationResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowRunRequest;
import io.github.rejeb.dataform.language.gcp.execution.workflow.repository.GcpBigQueryJobRepository;
import io.github.rejeb.dataform.language.gcp.execution.workflow.repository.GcpDataformWorkflowRepository;
import io.github.rejeb.dataform.language.gcp.execution.workflow.repository.WorkflowRepository;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.settings.WorkflowSettingsGcpConfigProvider;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import io.github.rejeb.dataform.language.gcp.workspace.WorkspaceOperations;
import io.github.rejeb.dataform.language.gcp.workspace.WorkspaceOperationsHandler;
import io.github.rejeb.dataform.language.gcp.workspace.repository.GcpDataformWorkspaceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DataformGcpServiceImpl implements DataformGcpService, Disposable {

    private static final Logger LOG = Logger.getInstance(DataformGcpServiceImpl.class);

    private final WorkspaceOperations workspaceOperations;
    private final Project project;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final DataformGcpFileCache fileCache;
    private final WorkflowOperations workflowOperations;
    private final BigQueryJobOperations bigQueryJobOperations;

    public DataformGcpServiceImpl(@NotNull Project project) {
        this.project = project;
        this.fileCache = DataformGcpFileCache.getInstance(project);

        GcpDataformWorkspaceRepository repository = new GcpDataformWorkspaceRepository();
        Disposer.register(this, repository);

        var configProvider = new WorkflowSettingsGcpConfigProvider(GcpRepositorySettings.getInstance(project));
        this.workspaceOperations = new WorkspaceOperationsHandler(repository, configProvider, project);
        WorkflowRepository workflowRepository = new GcpDataformWorkflowRepository();
        this.workflowOperations = new WorkflowOperationsHandler(workflowRepository, configProvider);
        this.bigQueryJobOperations = new BigQueryJobOperationsHandler(new GcpBigQueryJobRepository());
    }

    @Override
    public void dispose() {
    }

    @Override
    @NotNull
    public List<String> getCachedFiles() {
        return fileCache.getCachedFiles();
    }

    @Override
    public @NotNull String getFileContent(@Nullable String workspaceId, @NotNull String filePath) {
        return workspaceOperations.getFileContent(workspaceId, filePath);
    }

    @Override
    public void invalidateCache() {
        fileCache.invalidate();
    }

    @Override
    public void refreshFilesAsync(@Nullable String workspaceId, @NotNull Consumer<List<String>> onDone) {
        if (!loading.compareAndSet(false, true)) return;

        com.intellij.openapi.progress.ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Backgroundable(project, "Loading Dataform repository files…", false) {
            @Override
            public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                try {
                    List<String> files = workspaceOperations.listAllPaths(workspaceId);
                    fileCache.update(files);
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> onDone.accept(files));
                    project.getMessageBus().syncPublisher(DataformGcpEvent.TOPIC).onFilesLoaded(files);
                } catch (Exception e) {
                    LOG.warn("Failed to refresh Dataform files.", e);
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> onDone.accept(List.of()));
                } finally {
                    loading.set(false);
                }
            }
        });
    }

    @Override
    @NotNull
    public List<Workspace> listWorkspaces() {
        try {
            return workspaceOperations.listWorkspaces();
        } catch (Exception e) {
            LOG.error("Failed to list Dataform workspaces", e);
            return List.of();
        }
    }

    @Override
    public void pushGitCommits(@NotNull String workspaceId) {
        try {
            workspaceOperations.pushGitCommits(workspaceId);
        } catch (GcpApiException e) {
            LOG.error("Failed to commit code to Dataform workspace: " + workspaceId, e);
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
    public Map<String, String> fetchCode(@Nullable String workspaceId) {
        try {
            Map<String, String> files = workspaceOperations.fetchCode(workspaceId);
            return files;
        } catch (GcpApiException e) {
            LOG.warn("Failed to fetch code from Dataform workspace: " + workspaceId, e);
            return Map.of();
        }
    }

    @Override
    public void pullCode(@Nullable String workspaceId) {
        try {
            workspaceOperations.pullCode(workspaceId);
            fileCache.invalidate();
        } catch (GcpApiException e) {
            LOG.error("Failed to sync code from Dataform: " + workspaceId, e);
        }
    }

    @Override
    public void testConnection(@NotNull DataformRepositoryConfig config) {
        workspaceOperations.testConnection(config);
    }

    @Override
    public boolean isLoading() {
        return loading.get();
    }

    @Override
    public void createGcpRepository(@NotNull DataformRepositoryConfig config) {
        workspaceOperations.createRepository(config);
    }

    @Override
    public void createWorkspace(@NotNull String workspaceId) {
        workspaceOperations.createWorkspace(workspaceId);
    }

    @Override
    @NotNull
    public List<UncommittedChange> fetchGitStatuses(@NotNull String workspaceId) {
        try {
            return workspaceOperations.fetchGitStatuses(workspaceId);
        } catch (GcpApiException e) {
            LOG.error("Failed to fetch git statuses for workspace: " + workspaceId, e);
            return List.of();
        }
    }

    @Override
    public void commitWorkspaceChanges(@NotNull String workspaceId, @NotNull List<String> paths, @NotNull String message) {
        workspaceOperations.commitWorkspaceChanges(workspaceId, paths, message);
    }

    @Override
    public @NotNull WorkflowCreationResult createWorkflowRun(@NotNull WorkflowRunRequest request) {
        return workflowOperations.createWorkflowRun(request);
    }

    @Override
    public @NotNull WorkflowInvocationProgress getWorkflowRunProgress(@NotNull WorkflowCreationResult workflowRun) {
        return workflowOperations.getWorkflowRunProgress(workflowRun);
    }

    @Override
    public void cancelWorkflowRun(@NotNull String workflowRunName) {
        workflowOperations.cancelWorkflowRun(workflowRunName);
    }

    @Override
    public List<String> listAllPaths(@Nullable String workspaceId) {
        return workspaceOperations.listAllPaths(workspaceId);
    }

    @Override
    @Nullable
    public BigQueryJobDetails getJobDetails(
            @NotNull String jobId,
            @NotNull String project,
            @NotNull String location
    ) {
        return bigQueryJobOperations.getJobDetails(jobId, project, location);
    }
}
