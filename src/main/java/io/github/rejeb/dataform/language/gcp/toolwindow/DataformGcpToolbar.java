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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.PullFromWorkspaceAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.PushToWorkspaceAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.RefreshAction;
import io.github.rejeb.dataform.language.gcp.workspace.Workspace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public class DataformGcpToolbar extends JPanel {

    private final ComboBox<WorkspaceItem> workspaceCombo;
    private final Project project;
    private final DataformGcpPanel.PanelCallback callback;

    public DataformGcpToolbar(
            @NotNull Project project,
            @NotNull DataformGcpPanel.PanelCallback callback
    ) {
        super(new BorderLayout());
        this.project = project;
        this.callback = callback;
        this.workspaceCombo = new ComboBox<>();
        workspaceCombo.addItem(new WorkspaceItem(null, "No workspace (repo main)"));
        workspaceCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                WorkspaceItem selected = (WorkspaceItem) e.getItem();
                GcpRepositorySettings.getInstance(project)
                        .setSelectedWorkspaceId(selected.workspaceId());
            }
        });

        add(buildToolbarComponent(), BorderLayout.CENTER);
    }

    /**
     * Replaces the workspace list in the dropdown.
     */
    public void setWorkspaces(@NotNull List<Workspace> workspaces) {
        String previouslySelected = GcpRepositorySettings.getInstance(project)
                .getSelectedWorkspaceId();

        workspaceCombo.removeAllItems();
        workspaceCombo.addItem(new WorkspaceItem(null, "No workspace (repo main)"));
        for (Workspace w : workspaces) {
            workspaceCombo.addItem(new WorkspaceItem(w.workspaceId(), w.workspaceId()));
        }

        if (previouslySelected != null) {
            for (int i = 0; i < workspaceCombo.getItemCount(); i++) {
                WorkspaceItem item = workspaceCombo.getItemAt(i);
                if (previouslySelected.equals(item.workspaceId())) {
                    workspaceCombo.setSelectedIndex(i);
                    return;
                }
            }
            workspaceCombo.setSelectedIndex(0);
            GcpRepositorySettings.getInstance(project).setSelectedWorkspaceId(null);
        }
    }


    /**
     * @return the currently selected workspace ID, or {@code null} if "no workspace" is selected
     */
    @Nullable
    public String getSelectedWorkspaceId() {
        WorkspaceItem selected = (WorkspaceItem) workspaceCombo.getSelectedItem();
        return selected != null ? selected.workspaceId() : null;
    }

    private JComponent buildToolbarComponent() {
        DefaultActionGroup group = new DefaultActionGroup();

        group.add(new RefreshAction(this::getSelectedWorkspaceId, callback));
        group.addSeparator();
        group.add(new PullFromWorkspaceAction(this::getSelectedWorkspaceId, callback));
        group.add(new PushToWorkspaceAction(this::getSelectedWorkspaceId, callback));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformGcpToolbar", group, true);
        toolbar.setTargetComponent(this);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(new JBLabel("Workspace:"));
        panel.add(workspaceCombo);
        panel.add(toolbar.getComponent());
        return panel;
    }

    private record WorkspaceItem(@Nullable String workspaceId, @NotNull String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
