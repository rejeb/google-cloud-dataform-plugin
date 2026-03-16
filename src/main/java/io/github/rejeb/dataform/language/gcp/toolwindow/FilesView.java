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
import io.github.rejeb.dataform.language.gcp.service.*;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class FilesView extends JPanel {

    private final Project project;
    private final DataformRepoTreeModel treeModel;
    private final FileViewToolbar toolbar;

    public FilesView(
            @NotNull Project project,
            @NotNull DataformRepositoryConfig config,
            @NotNull GcpPanelActionDispatcher dispatcher
    ) {
        super(new BorderLayout());
        this.project = project;

        treeModel = new DataformRepoTreeModel(config.repositoryId());

        toolbar = new FileViewToolbar(
                project,
                () -> GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId(),
                dispatcher);

        Tree tree = new Tree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DataformRepoTreeCellRenderer(treeModel));
        tree.setRowHeight(0);

        project.getMessageBus()
                .connect()
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onFilesLoaded(@NotNull Map<String, String> files) {
                        treeModel.setLoading(false);
                        treeModel.setFiles(files);
                        TreeUtil.expandAll(tree);
                    }

                    @Override
                    public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                        treeModel.setGitStatuses(changes);
                    }

                    @Override
                    public void onNotification(@NotNull String message, @NotNull NotificationType type) {
                        NotificationGroupManager.getInstance()
                                .getNotificationGroup("Dataform.Notifications")
                                .createNotification(message, type)
                                .notify(project);
                    }
                });

        JLabel titleLabel = buildViewTitle("GCP remote project view");
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);

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
