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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ManageRepositoriesDialog extends DialogWrapper {

    private final Project project;
    private final DefaultListModel<DataformRepositoryConfig> listModel = new DefaultListModel<>();
    private final JBList<DataformRepositoryConfig> repoList = new JBList<>(listModel);

    public ManageRepositoriesDialog(@NotNull Project project) {
        super(project, true);
        this.project = project;
        setTitle("Manage Dataform Repositories");

        GcpRepositorySettings.getInstance(project).getAllConfigs().forEach(listModel::addElement);

        repoList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataformRepositoryConfig c) {
                    setText(c.displayName() + "  [" + c.projectId() + " / " + c.location()+ " / " + c.repositoryId() + "]");
                    setIcon(AllIcons.Nodes.DataSchema);
                }
                return this;
            }
        });

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel decorated = ToolbarDecorator.createDecorator(repoList)
                .setAddAction(button -> addRepository())
                .setEditAction(button -> editSelected())
                .setRemoveAction(button -> removeSelected())
                .setEditActionUpdater(e -> !repoList.isSelectionEmpty())
                .setRemoveActionUpdater(e -> !repoList.isSelectionEmpty())
                .disableUpDownActions()
                .createPanel();
        decorated.setPreferredSize(new Dimension(520, 280));
        return decorated;
    }

    /**
     * Exposes only a "Close" button — changes are persisted immediately on each action.
     */
    @Override
    protected @NotNull Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected void createDefaultActions() {
        super.createDefaultActions();
        setOKButtonText("Close");
    }

    private void addRepository() {
        DataformRepositoryConfigDialog dialog = new DataformRepositoryConfigDialog(project, null);
        if (dialog.showAndGet()) {
            DataformRepositoryConfig created = dialog.getResultConfig();
            if (created != null) {
                listModel.addElement(created);
                persistList();
                if (listModel.size() == 1) {
                    GcpRepositorySettings.getInstance(project).setActiveRepositoryId(created.repositoryId());
                }
            }
        }
    }

    private void editSelected() {
        int idx = repoList.getSelectedIndex();
        if (idx < 0) return;
        DataformRepositoryConfig existing = listModel.get(idx);
        DataformRepositoryConfigDialog dialog = new DataformRepositoryConfigDialog(project, existing);
        if (dialog.showAndGet()) {
            DataformRepositoryConfig updated = dialog.getResultConfig();
            if (updated != null) {
                listModel.set(idx, updated);
                persistList();
            }
        }
    }

    private void removeSelected() {
        int idx = repoList.getSelectedIndex();
        if (idx < 0) return;
        DataformRepositoryConfig target = listModel.get(idx);

        int confirmed = Messages.showYesNoDialog(
                project,
                "Remove repository \"" + target.displayName() + "\"?\nThis cannot be undone.",
                "Remove Repository",
                Messages.getWarningIcon()
        );
        if (confirmed != Messages.YES) return;

        listModel.remove(idx);
        persistList();
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(project);
        if (target.repositoryId().equals(settings.getActiveRepositoryId())) {
            settings.setActiveRepositoryId(listModel.isEmpty() ? null : listModel.get(0).repositoryId());
        }
    }


    private void persistList() {
        List<DataformRepositoryConfig> all = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) all.add(listModel.get(i));
        GcpRepositorySettings.getInstance(project).saveAllConfigs(all);
    }
}
