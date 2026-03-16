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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpFilesLoadedListener;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpGitStatusesListener;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpWorkspacesListener;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class FilesView extends JPanel {

    private static final String NOTIFICATION_GROUP = "Dataform.Notifications";

    private final Project project;
    private final DataformRepoTreeModel treeModel;
    private final FileViewToolbar toolbar;

    public FilesView(
            @NotNull Project project,
            @NotNull DataformRepositoryConfig config,
            @NotNull DataformGcpPanel.PanelCallback callback
    ) {
        super(new BorderLayout());
        this.project = project;

        treeModel = new DataformRepoTreeModel(config.repositoryId());
        toolbar = new FileViewToolbar(
                project,
                () -> GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId(),
                callback);

        Tree tree = new Tree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DataformRepoTreeCellRenderer(treeModel));
        tree.setRowHeight(0);

        initFiles(tree);
        refreshWorkspaces();

        JLabel titleLabel = buildViewTitle("GCP remote project view");
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Public operations
    // -------------------------------------------------------------------------

    public void refreshWorkspaces() {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Loading Dataform workspaces…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        List<Workspace> workspaces =
                                DataformGcpService.getInstance(project).listWorkspaces();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            project.getMessageBus()
                                    .syncPublisher(DataformGcpWorkspacesListener.TOPIC)
                                    .onWorkspacesLoaded(workspaces);
                            String workspaceId =
                                    GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
                            fetch(workspaceId);
                            if (workspaceId != null) {
                                fetchGitStatuses(workspaceId);
                            }
                        });
                    }
                });
    }

    public void fetch(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Fetching from workspace '" + workspaceId + "'…"
                : "Reading files from repo main branch…";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Map<String, String> files =
                        DataformGcpService.getInstance(project).fetchCode(workspaceId);
                ApplicationManager.getApplication().invokeLater(
                        () -> treeModel.setFiles(files));
            }
        });
    }

    public void fetchGitStatuses(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Loading git statuses…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        List<UncommittedChange> changes =
                                DataformGcpService.getInstance(project)
                                        .fetchGitStatuses(workspaceId);
                        ApplicationManager.getApplication().invokeLater(() ->
                                treeModel.setGitStatuses(changes));

                        project.getMessageBus()
                                .syncPublisher(DataformGcpGitStatusesListener.TOPIC)
                                .onGitStatusesLoaded(changes);
                    }
                });
    }

    public void pull(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Pulling from workspace '" + workspaceId + "'…"
                : "Pulling from repo main branch…";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                DataformGcpService.getInstance(project).pullCode(workspaceId);
            }

            @Override
            public void onSuccess() {
                notifyUser("Local files updated successfully.", NotificationType.INFORMATION);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                notifyUser("Pull failed: " + error.getMessage(), NotificationType.ERROR);
            }
        });
    }

    public void push(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project,
                        "Pushing to workspace '" + workspaceId + "'…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        DataformGcpService.getInstance(project).pushCode(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        notifyUser("Push failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    public void commitWorkspaceChanges(
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message
    ) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Committing changes…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        DataformGcpService.getInstance(project)
                                .commitWorkspaceChanges(workspaceId, paths, message);
                    }

                    @Override
                    public void onSuccess() {
                        fetchGitStatuses(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        notifyUser("Commit failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    public void pushGitCommits(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Pushing commits…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        DataformGcpService.getInstance(project).pushGitCommits(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        notifyUser("Push failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    public void commitAndPushWorkspaceChanges(
            @NotNull String workspaceId,
            @NotNull List<String> paths,
            @NotNull String message
    ) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Committing and pushing changes…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setText("Committing changes…");
                        DataformGcpService.getInstance(project)
                                .commitWorkspaceChanges(workspaceId, paths, message);
                        indicator.setText("Pushing commits…");
                        DataformGcpService.getInstance(project)
                                .pushGitCommits(workspaceId);
                    }

                    @Override
                    public void onSuccess() {
                        fetchGitStatuses(workspaceId);
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        notifyUser("Commit & Push failed: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    public void createWorkspace(
            @NotNull String workspaceId,
            @NotNull RepositorySelectorPanel selectorPanel
    ) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project,
                        "Creating workspace '" + workspaceId + "'…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        DataformGcpService.getInstance(project).createWorkspace(workspaceId);
                    }

                    @Override
                    public void onSuccess() {
                        ProgressManager.getInstance().run(
                                new Task.Backgroundable(project, "Loading Dataform workspaces…") {
                                    @Override
                                    public void run(@NotNull ProgressIndicator indicator) {
                                        List<Workspace> workspaces =
                                                DataformGcpService.getInstance(project).listWorkspaces();
                                        ApplicationManager.getApplication().invokeLater(() -> {
                                            selectorPanel.setWorkspaces(workspaces);
                                            selectorPanel.selectWorkspace(workspaceId);
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        notifyUser("Failed to create workspace: " + error.getMessage(), NotificationType.ERROR);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private void initFiles(@NotNull Tree tree) {
        DataformGcpService service = DataformGcpService.getInstance(project);
        Map<String, String> cached = service.getCachedFiles();

        if (!cached.isEmpty()) {
            treeModel.setFiles(cached);
            TreeUtil.expandAll(tree);
        } else if (!service.isLoading()) {
            treeModel.setLoading(true);
            service.refreshFilesAsync(null, files -> {
                treeModel.setLoading(false);
                treeModel.setFiles(files);
                TreeUtil.expandAll(tree);
            });
        } else {
            treeModel.setLoading(true);
            project.getMessageBus()
                    .connect()
                    .subscribe(DataformGcpFilesLoadedListener.TOPIC,
                            (DataformGcpFilesLoadedListener) files -> {
                                treeModel.setLoading(false);
                                treeModel.setFiles(files);
                                TreeUtil.expandAll(tree);
                            });
        }
    }

    private void notifyUser(@NotNull String message, @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(message, type)
                .notify(project);
    }

    static JLabel buildViewTitle(@NotNull String text) {
        JLabel label = new JLabel(text);
        label.setFont(JBUI.Fonts.label().asBold());
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0,
                        UIManager.getColor("Separator.separatorColor")),
                JBUI.Borders.empty(4, 8)));
        return label;
    }
}
