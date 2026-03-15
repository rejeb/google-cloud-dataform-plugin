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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpFilesLoadedListener;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

        JPanel contentPanel = new JPanel(new CardLayout());
        contentPanel.add(buildFilesView(config), "FILES");
        contentPanel.add(buildGitView(), "GIT");

        JPanel sideBar = buildSideBar(contentPanel);

        add(sideBar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        ((CardLayout) contentPanel.getLayout()).show(contentPanel, "FILES");

        revalidate();
        repaint();
    }

    private JPanel buildSideBar(@NotNull JPanel contentPanel) {
        JPanel sideBar = new JPanel();
        sideBar.setLayout(new BoxLayout(sideBar, BoxLayout.Y_AXIS));
        sideBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 1,
                UIManager.getColor("Separator.separatorColor")));

        sideBar.add(buildSideBarButton(
                AllIcons.Actions.ProjectDirectory,
                "Files",
                contentPanel, "FILES"
        ));
        sideBar.add(buildSideBarButton(
                AllIcons.Vcs.Branch,
                "Git",
                contentPanel, "GIT"
        ));
        sideBar.add(Box.createVerticalGlue());
        return sideBar;
    }

    private JButton buildSideBarButton(
            @NotNull Icon icon,
            @NotNull String tooltip,
            @NotNull JPanel contentPanel,
            @NotNull String cardKey
    ) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(30, 30));
        btn.setPreferredSize(new Dimension(30, 30));
        btn.addActionListener(e ->
                ((CardLayout) contentPanel.getLayout()).show(contentPanel, cardKey)
        );
        return btn;
    }

    private JComponent buildFilesView(@NotNull DataformRepositoryConfig config) {
        JPanel panel = new JPanel(new BorderLayout());

        treeModel = new DataformRepoTreeModel(config.repositoryId());
        PanelCallback callback = buildPanelCallback();
        toolbar = new DataformGcpToolbar(project, callback);

        tree = new Tree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DataformRepoTreeCellRenderer());
        tree.setRowHeight(0);

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

        refreshWorkspaces();

        // Titre contextuel
        JLabel titleLabel = new JLabel("GCP remote project view");
        titleLabel.setFont(JBUI.Fonts.label().asBold());
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0,
                        UIManager.getColor("Separator.separatorColor")),
                JBUI.Borders.empty(4, 8)
        ));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildGitView() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel titleLabel = new JLabel("Commit");
        titleLabel.setFont(JBUI.Fonts.label().asBold());
        titleLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0,
                        UIManager.getColor("Separator.separatorColor")),
                JBUI.Borders.empty(4, 8)
        ));

        panel.add(titleLabel, BorderLayout.NORTH);
        return panel;
    }


    private @NotNull PanelCallback buildPanelCallback() {
        return new PanelCallback() {
            @Override
            public void onRefreshWorkspaces() {
                refreshWorkspaces();
            }

            @Override
            public void onFetch(@Nullable String workspaceId) {
                fetch(workspaceId);
            }

            @Override
            public void onPull(@Nullable String workspaceId) {
                pull(workspaceId);
            }

            @Override
            public void onPush(@NotNull String workspaceId) {
                push(workspaceId);
            }

            @Override
            public void onCommit(@NotNull String workspaceId) {
                commit(workspaceId);
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

    private void fetch(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Pulling from workspace '" + workspaceId + "'…"
                : "Reading files from repo main branch…";

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Map<String, String> files = DataformGcpService.getInstance(project)
                        .fetchCode(workspaceId);
                ApplicationManager.getApplication().invokeLater(() -> treeModel.setFiles(files));
            }
        });
    }

    private void pull(@Nullable String workspaceId) {
        String title = workspaceId != null
                ? "Pulling from workspace '" + workspaceId + "'…"
                : "Pulling from repo main branch…";

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                DataformGcpService.getInstance(project).pullCode(workspaceId);
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

    private void commit(@NotNull String workspaceId) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Committing workspace '" + workspaceId + "'…") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        DataformGcpService.getInstance(project).commitCode(workspaceId);
                    }
                }
        );
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

    public interface PanelCallback {
        void onRefreshWorkspaces();

        void onFetch(@Nullable String workspaceId);

        void onPull(@Nullable String workspaceId);

        void onPush(@NotNull String workspaceId);

        void onCommit(@NotNull String workspaceId);

        void onConfigure();
    }
}
