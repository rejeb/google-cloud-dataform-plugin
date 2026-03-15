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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.treeStructure.Tree;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class DataformGcpPanel extends JPanel {

    private final Project project;
    private DataformRepoTreeModel treeModel;
    private DataformGcpToolbar toolbar;
    private Tree tree;

    public DataformGcpPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;

        DataformRepositoryConfig config = GcpRepositorySettings.getInstance(project).getConfig();

        if (config == null) {
            showUnconfiguredState();
        } else {
            initConfiguredState(config);
        }
    }

    private void showUnconfiguredState() {
        removeAll();
        setLayout(new BorderLayout());

        LinkLabel<Void> link = new LinkLabel<>(
                "Configure Dataform repository",
                AllIcons.General.Settings,
                (aSource, aLinkData) -> openConfigDialog()
        );
        link.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel center = new JPanel(new GridBagLayout());
        center.add(link);
        add(center, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void initConfiguredState(@NotNull DataformRepositoryConfig config) {
        removeAll();
        setLayout(new BorderLayout());

        treeModel = new DataformRepoTreeModel(config.repositoryId());

        PanelCallback callback = buildPanelCallback();

        toolbar = new DataformGcpToolbar(project, callback);

        tree = new Tree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DataformRepoTreeCellRenderer());
        tree.setRowHeight(0);

        add(toolbar, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private @NonNull PanelCallback buildPanelCallback() {
        return new PanelCallback() {
            @Override
            public void onRefreshWorkspaces() {
                refreshWorkspaces();
            }

            @Override
            public void onPull(@Nullable String workspaceId) {
                pull(workspaceId);
            }

            @Override
            public void onSync(@Nullable String workspaceId) {
                sync(workspaceId);
            }

            @Override
            public void onPush(@NotNull String workspaceId) {
                push(workspaceId);
            }

            @Override
            public void onConfigure() {
                openConfigDialog();
            }
        };
    }

    private void openConfigDialog() {
        DataformRepositoryConfigDialog dialog = new DataformRepositoryConfigDialog(project);
        if (dialog.showAndGet()) {
            // Sauvegarde OK → réinitialise le panel avec la nouvelle config
            DataformRepositoryConfig config = GcpRepositorySettings.getInstance(project).getConfig();
            if (config != null) initConfiguredState(config);
        }
    }


    private void refreshWorkspaces() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Loading Dataform workspaces…") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<Workspace> workspaces = DataformGcpService.getInstance(project).listWorkspaces();
                ApplicationManager.getApplication().invokeLater(
                        () -> toolbar.setWorkspaces(workspaces)
                );
            }
        });
    }

    private void pull(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Pulling from workspace '" + workspaceId + "'…"
                : "Reading files from repo main branch…";

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Map<String, String> files = DataformGcpService.getInstance(project)
                        .pullCode(workspaceId);
                ApplicationManager.getApplication().invokeLater(() -> treeModel.setFiles(files));
            }
        });
    }

    private void sync(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Syncing from workspace '" + workspaceId + "'…"
                : "Syncing from repo main branch…";

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                DataformGcpService.getInstance(project).syncCode(workspaceId);
                ApplicationManager.getApplication().invokeLater(
                        () -> JOptionPane.showMessageDialog(
                                DataformGcpPanel.this,
                                "Local files updated successfully.",
                                "Sync Complete",
                                JOptionPane.INFORMATION_MESSAGE
                        )
                );
            }
        });
    }

    private void push(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Pushing to workspace '" + workspaceId + "'…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        DataformGcpService.getInstance(project).pushCode(workspaceId);
                    }
                }
        );
    }

    /**
     * Callback interface used by toolbar actions to trigger panel operations.
     */
    public interface PanelCallback {
        void onRefreshWorkspaces();

        void onPull(@Nullable String workspaceId);

        /**
         * Pull and apply files to local project.
         */
        void onSync(@Nullable String workspaceId);

        void onPush(@NotNull String workspaceId);

        void onConfigure();
    }
}
