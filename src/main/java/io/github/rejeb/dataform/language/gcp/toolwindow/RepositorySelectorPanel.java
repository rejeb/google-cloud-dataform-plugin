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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public class RepositorySelectorPanel extends JPanel {

    private final Project project;
    private final ComboBox<DataformRepositoryConfig> repoCombo = new ComboBox<>();
    private final Runnable onSelectionChanged;
    private boolean updating = false;

    public RepositorySelectorPanel(@NotNull Project project, @NotNull Runnable onSelectionChanged) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        this.project = project;
        this.onSelectionChanged = onSelectionChanged;

        repoCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataformRepositoryConfig c) setText(c.displayName());
                return this;
            }
        });

        repoCombo.addItemListener(e -> {
            if (updating) return;
            if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof DataformRepositoryConfig c) {
                GcpRepositorySettings.getInstance(project).setActiveRepositoryId(c.repositoryId());
                onSelectionChanged.run();
            }
        });

        add(new JBLabel("Repository:"));
        add(repoCombo);
        add(buildConfigureButton());

        refresh();
    }

    /**
     * Reloads the combo from persisted settings and restores the active selection.
     */
    public void refresh() {
        updating = true;
        try {
            repoCombo.removeAllItems();
            List<DataformRepositoryConfig> all = GcpRepositorySettings.getInstance(project).getAllConfigs();
            String activeId = GcpRepositorySettings.getInstance(project).getActiveRepositoryId();
            DataformRepositoryConfig toSelect = null;
            for (DataformRepositoryConfig c : all) {
                repoCombo.addItem(c);
                if (c.repositoryId().equals(activeId)) toSelect = c;
            }
            if (toSelect != null) repoCombo.setSelectedItem(toSelect);
        } finally {
            updating = false;
        }
    }

    private JButton buildConfigureButton() {
        JButton button = new JButton(AllIcons.General.Settings);
        button.setToolTipText("Manage Repositories");
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(24, 24));
        button.addActionListener(e -> {
            new ManageRepositoriesDialog(project).show();
            refresh();
            onSelectionChanged.run();
        });
        return button;
    }
}
