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
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.labels.LinkLabel;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DataformGcpPanel extends JPanel {

    private final Project project;
    private FilesView filesView;
    private RepositorySelectorPanel repositorySelectorPanel;

    public DataformGcpPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        refresh();
    }

    public void refresh() {
        removeAll();
        DataformRepositoryConfig config =
                GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (config == null) {
            showUnconfiguredState();
        } else {
            initConfiguredState(config);
        }
        revalidate();
        repaint();
    }

    private void showUnconfiguredState() {
        LinkLabel<Void> link = new LinkLabel<>(
                "Add a Dataform repository",
                AllIcons.General.Add,
                (aSource, aLinkData) -> openManageDialog()
        );
        link.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel center = new JPanel(new GridBagLayout());
        center.add(link);
        add(center, BorderLayout.CENTER);
    }

    private void initConfiguredState(@NotNull DataformRepositoryConfig config) {
        PanelCallback callback = buildPanelCallback();

        repositorySelectorPanel = new RepositorySelectorPanel(
                project, this::onRepositorySelected, callback);

        filesView = new FilesView(project, config, callback);
        CommitView commitView = new CommitView(project, callback);

        JPanel contentPanel = new JPanel(new CardLayout());
        contentPanel.add(filesView, "FILES");
        contentPanel.add(commitView, "GIT");

        JPanel sideBar = buildSideBar(contentPanel);

        add(repositorySelectorPanel, BorderLayout.NORTH);
        add(sideBar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        ((CardLayout) contentPanel.getLayout()).show(contentPanel, "FILES");
    }

    private void onRepositorySelected() {
        removeAll();
        DataformRepositoryConfig active =
                GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (active != null) {
            initConfiguredState(active);
            filesView.refreshWorkspaces();
        } else {
            showUnconfiguredState();
        }
        revalidate();
        repaint();
    }

    private void openManageDialog() {
        new ManageRepositoriesDialog(project).show();
        refresh();
    }

    private JPanel buildSideBar(@NotNull JPanel contentPanel) {
        JPanel sideBar = new JPanel();
        sideBar.setLayout(new BoxLayout(sideBar, BoxLayout.Y_AXIS));
        sideBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 1,
                UIManager.getColor("Separator.separatorColor")));
        sideBar.add(buildSideBarButton(AllIcons.Actions.ProjectDirectory, "Files",
                contentPanel, "FILES"));
        sideBar.add(buildSideBarButton(AllIcons.Vcs.Branch, "Git", contentPanel, "GIT"));
        sideBar.add(Box.createVerticalGlue());
        return sideBar;
    }

    private JButton buildSideBarButton(
            @NotNull Icon icon, @NotNull String tooltip,
            @NotNull JPanel contentPanel, @NotNull String cardKey
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
                ((CardLayout) contentPanel.getLayout()).show(contentPanel, cardKey));
        return btn;
    }

    private @NotNull PanelCallback buildPanelCallback() {
        return new PanelCallback() {
            @Override
            public void onRefreshWorkspaces() {
                filesView.refreshWorkspaces();
            }

            @Override
            public void onFetch(@Nullable String workspaceId) {
                filesView.fetch(workspaceId);
            }

            @Override
            public void onPull(@Nullable String workspaceId) {
                filesView.pull(workspaceId);
            }

            @Override
            public void onPush(@NotNull String workspaceId) {
                filesView.push(workspaceId);
            }

            @Override
            public void onCreateWorkspace(@NotNull String workspaceId) {
                filesView.createWorkspace(workspaceId, repositorySelectorPanel);
            }

            @Override
            public void onFetchGitStatuses(@NotNull String workspaceId) {
                filesView.fetchGitStatuses(workspaceId);
            }

            @Override
            public void onCommitWorkspaceChanges(
                    @NotNull String workspaceId,
                    @NotNull List<String> paths,
                    @NotNull String message
            ) {
                filesView.commitWorkspaceChanges(workspaceId, paths, message);
            }

            @Override
            public void onPushGitCommits(@NotNull String workspaceId) {
                filesView.pushGitCommits(workspaceId);
            }
        };
    }

    public interface PanelCallback {
        void onRefreshWorkspaces();
        void onFetch(@Nullable String workspaceId);
        void onPull(@Nullable String workspaceId);
        void onPush(@NotNull String workspaceId);
        void onCreateWorkspace(@NotNull String workspaceId);
        void onFetchGitStatuses(@NotNull String workspaceId);
        void onCommitWorkspaceChanges(
                @NotNull String workspaceId,
                @NotNull List<String> paths,
                @NotNull String message);
        void onPushGitCommits(@NotNull String workspaceId);
    }
}
