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
package io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.ui.Messages;

public class GcpPanelActionDispatcherImpl implements GcpPanelActionDispatcher {

    private static final int MAX_LISTED_DELETIONS = 15;

    private final Project project;

    public GcpPanelActionDispatcherImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void refreshWorkspaces() {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Loading Dataform workspaces…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        List<Workspace> workspaces = gcpService().listWorkspaces();
                        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
                        ApplicationManager.getApplication().invokeLater(() ->
                                publish().onWorkspacesLoaded(workspaces));
                        fetchFiles(workspaceId);
                        if (workspaceId != null) {
                            fetchGitStatusesInternal(workspaceId);
                        }
                    }
                });
    }

    @Override
    public void fetchFiles(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Fetching from workspace '" + workspaceId + "'…"
                : "Reading files from repo main branch…";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<String> files = gcpService().listAllPaths(workspaceId);
                ApplicationManager.getApplication().invokeLater(() ->
                        publish().onFilesLoaded(List.copyOf(files)));
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                publish().onFilesLoaded(List.of());
                publish().onNotification("Could not list the remote files: " + error.getMessage(),
                        NotificationType.ERROR);
            }
        });
    }

    @Override
    public void pull(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Pulling from workspace '" + workspaceId + "'…"
                : "Pulling from repo main branch…";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                gcpService().pullCode(workspaceId);
            }

            @Override
            public void onSuccess() {
                publish().onNotification("Local files updated successfully.", NotificationType.INFORMATION);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                publish().onNotification("Pull failed: " + error.getMessage(), NotificationType.ERROR);
            }
        });
    }

    @Override
    public void push(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Uploading files to workspace '" + workspaceId + "'…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        gcpService().pushCode(workspaceId, deletions -> confirmDeletions(workspaceId, deletions));
                    }

                    @Override
                    public void onSuccess() {
                        publish().onNotification("Local files uploaded to workspace '" + workspaceId + "'.",
                                NotificationType.INFORMATION);
                        fetchFiles(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        publish().onNotification("Push failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    @Override
    public void commitChanges(@NotNull String workspaceId, @NotNull List<String> paths, @NotNull String message) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Committing changes…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        gcpService().commitWorkspaceChanges(workspaceId, paths, message);
                    }

                    @Override
                    public void onSuccess() {
                        fetchGitStatusesInternal(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        publish().onNotification("Commit failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    @Override
    public void pushGitCommits(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Pushing commits…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        gcpService().pushGitCommits(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        publish().onNotification("Push commits failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    @Override
    public void commitAndPush(@NotNull String workspaceId, @NotNull List<String> paths, @NotNull String message) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Committing and pushing changes…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setText("Committing changes…");
                        gcpService().commitWorkspaceChanges(workspaceId, paths, message);
                        indicator.setText("Pushing commits…");
                        gcpService().pushGitCommits(workspaceId);
                    }

                    @Override
                    public void onSuccess() {
                        fetchGitStatusesInternal(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        publish().onNotification("Commit & Push failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    @Override
    public void createWorkspace(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Creating workspace '" + workspaceId + "'…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        gcpService().createWorkspace(workspaceId);
                    }

                    @Override
                    public void onSuccess() {
                        ProgressManager.getInstance().run(
                                new Task.Backgroundable(project, "Loading Dataform workspaces…") {
                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        List<Workspace> workspaces = gcpService().listWorkspaces();
                                        ApplicationManager.getApplication().invokeLater(() ->
                                                publish().onWorkspacesLoaded(workspaces));
                                    }
                                });
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        publish().onNotification("Failed to create workspace: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    /**
     * Asks the user, on the event thread, whether the remote files absent from the local project
     * may be deleted from the workspace. Called from the push task's background thread.
     */
    private boolean confirmDeletions(@NotNull String workspaceId, @NotNull Set<String> deletions) {
        List<String> shown = deletions.stream().sorted().limit(MAX_LISTED_DELETIONS).toList();
        StringBuilder message = new StringBuilder()
                .append("The following ").append(deletions.size())
                .append(" file(s) exist in workspace '").append(workspaceId)
                .append("' but not in the local project and will be deleted:\n\n");
        shown.forEach(path -> message.append("  ").append(path).append('\n'));
        if (deletions.size() > shown.size()) {
            message.append("  … and ").append(deletions.size() - shown.size()).append(" more\n");
        }
        message.append("\nContinue?");
        Ref<Boolean> answer = new Ref<>(false);
        ApplicationManager.getApplication().invokeAndWait(() -> answer.set(
                Messages.showYesNoDialog(project, message.toString(), "Delete Remote Files",
                        Messages.getWarningIcon()) == Messages.YES));
        return answer.get();
    }

    private void fetchGitStatusesInternal(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Loading git statuses…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        List<UncommittedChange> changes = gcpService().fetchGitStatuses(workspaceId);
                        ApplicationManager.getApplication().invokeLater(() ->
                                publish().onGitStatusesLoaded(changes));
                    }
                });
    }

    private DataformGcpService gcpService() {
        return DataformGcpService.getInstance(project);
    }

    private DataformGcpEvent publish() {
        return project.getMessageBus().syncPublisher(DataformGcpEvent.TOPIC);
    }
}
