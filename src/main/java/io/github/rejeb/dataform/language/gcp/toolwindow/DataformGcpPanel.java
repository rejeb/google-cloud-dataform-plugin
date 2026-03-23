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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.LinkLabel;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcherImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class DataformGcpPanel extends JPanel {

    private final Project project;
    private FilesView filesView;
    private RepositorySelectorPanel repositorySelectorPanel;
    private final GcpPanelActionDispatcher dispatcher;

    public DataformGcpPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.dispatcher = new GcpPanelActionDispatcherImpl(project);
        refresh();
    }

    public GcpPanelActionDispatcher getDispatcher() {
        return dispatcher;
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
        repositorySelectorPanel = new RepositorySelectorPanel(
                project, this::onRepositorySelected, this::onWorkspaceSelected);
        dispatcher.refreshWorkspaces();

        filesView = new FilesView(project, config, dispatcher);
        CommitView commitView = new CommitView(project, dispatcher);

        JBTabbedPane tabs = new JBTabbedPane(JTabbedPane.LEFT);
        tabs.addTab("", AllIcons.Actions.ProjectDirectory, filesView);
        tabs.addTab("", AllIcons.Vcs.Branch, commitView);

        add(repositorySelectorPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private void onRepositorySelected() {
        removeAll();
        DataformRepositoryConfig active =
                GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (active != null) {
            initConfiguredState(active);
            dispatcher.refreshWorkspaces();
        } else {
            showUnconfiguredState();
        }
        revalidate();
        repaint();
    }

    private void onWorkspaceSelected() {
        removeAll();
        DataformRepositoryConfig active =
                GcpRepositorySettings.getInstance(project).getActiveConfig();
        if (active != null) {
            initConfiguredState(active);
            dispatcher.refreshWorkspaces();
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
}
