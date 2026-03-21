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

import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public class RepositorySelectorPanel extends JPanel {

    private final Project project;
    private final ComboBox<DataformRepositoryConfig> repoCombo = new ComboBox<>();
    private final ComboBox<WorkspaceItem> workspaceCombo = new ComboBox<>();
    private final Runnable onRepositoryChanged;
    private final Runnable onWorkspaceChanged;
    private boolean updatingRepo = false;
    private boolean updatingWorkspace = false;

    public RepositorySelectorPanel(
            @NotNull Project project,
            @NotNull Runnable onRepositoryChanged,
            @NotNull Runnable onWorkspaceChanged
    ) {
        super(new GridBagLayout());
        this.project = project;
        this.onRepositoryChanged = onRepositoryChanged;
        this.onWorkspaceChanged = onWorkspaceChanged;

        buildRepoCombo();
        buildWorkspaceCombo();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0 — Repository label | repoCombo
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(new JBLabel("Repository:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(repoCombo, gbc);

        // Row 1 — Workspace label | workspaceCombo
        gbc.gridy = 1;

        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(new JBLabel("Workspace:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(workspaceCombo, gbc);

        project.getMessageBus()
                .connect()
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onWorkspacesLoaded(@NotNull List<Workspace> workspaces) {
                        setWorkspaces(workspaces);
                    }
                });

        refresh();
    }

    public void refresh() {
        updatingRepo = true;
        try {
            repoCombo.removeAllItems();
            List<DataformRepositoryConfig> all =
                    GcpRepositorySettings.getInstance(project).getAllConfigs();
            String activeId = GcpRepositorySettings.getInstance(project).getActiveRepositoryId();
            DataformRepositoryConfig toSelect = null;
            for (DataformRepositoryConfig c : all) {
                repoCombo.addItem(c);
                if (c.repositoryId().equals(activeId)) toSelect = c;
            }
            if (toSelect != null) repoCombo.setSelectedItem(toSelect);
        } finally {
            updatingRepo = false;
        }
    }

    public void setWorkspaces(@NotNull List<Workspace> workspaces) {
        String previouslySelected =
                GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        updatingWorkspace = true;
        try {
            workspaceCombo.removeAllItems();
            workspaceCombo.addItem(new WorkspaceItem(null, "No workspace (repo main)"));
            for (Workspace w : workspaces) {
                workspaceCombo.addItem(new WorkspaceItem(w.workspaceId(), w.workspaceId()));
            }
            if (previouslySelected != null) {
                for (int i = 0; i < workspaceCombo.getItemCount(); i++) {
                    if (previouslySelected.equals(workspaceCombo.getItemAt(i).workspaceId())) {
                        workspaceCombo.setSelectedIndex(i);
                        return;
                    }
                }
            }
            workspaceCombo.setSelectedIndex(0);
            GcpRepositorySettings.getInstance(project).setSelectedWorkspaceId(null);
        } finally {
            updatingWorkspace = false;
        }
    }

    private void buildRepoCombo() {
        repoCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataformRepositoryConfig c) setText(c.displayName());
                return this;
            }
        });
        repoCombo.addItemListener(e -> {
            if (updatingRepo) return;
            if (e.getStateChange() == ItemEvent.SELECTED
                    && e.getItem() instanceof DataformRepositoryConfig c) {
                GcpRepositorySettings.getInstance(project).setActiveRepositoryId(c.repositoryId());
                onRepositoryChanged.run();
            }
        });
    }

    private void buildWorkspaceCombo() {
        workspaceCombo.addItem(new WorkspaceItem(null, "No workspace (repo main)"));
        workspaceCombo.addItemListener(e -> {
            if (updatingWorkspace) return;
            if (e.getStateChange() == ItemEvent.SELECTED) {
                WorkspaceItem selected = (WorkspaceItem) e.getItem();
                GcpRepositorySettings.getInstance(project)
                        .setSelectedWorkspaceId(selected.workspaceId());
                onWorkspaceChanged.run();
                ActivityTracker.getInstance().inc();
            }
        });
    }

    private record WorkspaceItem(@Nullable String workspaceId, @NotNull String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
