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
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class ManageRepositoriesDialog extends DialogWrapper {

    private final Project project;
    private final DefaultListModel<DataformRepositoryConfig> listModel = new DefaultListModel<>();
    private final JBList<DataformRepositoryConfig> repoList = new JBList<>(listModel);
    private final RepositoryEditPanel editPanel;

    private int editedIndex = -1;
    private boolean suppressListener = false;

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_EDIT = "edit";
    private final CardLayout rightCardLayout = new CardLayout();
    private final JPanel rightPanel = new JPanel(rightCardLayout);

    public ManageRepositoriesDialog(@NotNull Project project) {
        super(project, true);
        this.project = project;
        this.editPanel = new RepositoryEditPanel(project);

        // Après Create in GCP réussi : flush + persist immédiat
        editPanel.setOnCreateSuccess(() -> {
            flushEditedIndex();
            persistList();
        });

        setTitle("Manage Dataform Repositories");

        GcpRepositorySettings.getInstance(project).getAllConfigs().forEach(listModel::addElement);

        repoList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DataformRepositoryConfig c) {
                    setText(c.displayName());
                    setIcon(AllIcons.Nodes.DataSchema);
                }
                return this;
            }
        });

        repoList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressListener) return;
            flushEditedIndex();
            DataformRepositoryConfig selected = repoList.getSelectedValue();
            editedIndex = repoList.getSelectedIndex();
            if (selected != null) {
                editPanel.load(selected);
            } else {
                editedIndex = -1;
                editPanel.clear();
            }
        });

        rightPanel.add(new JBScrollPane(editPanel), CARD_EDIT);
        rightPanel.add(noRepositoryMessage(), CARD_EMPTY);

        listModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override public void intervalAdded(javax.swing.event.ListDataEvent e) { updateRightPanel(); }
            @Override public void intervalRemoved(javax.swing.event.ListDataEvent e) { updateRightPanel(); }
            @Override public void contentsChanged(javax.swing.event.ListDataEvent e) { updateRightPanel(); }
        });

        init();

        if (!listModel.isEmpty()) {
            repoList.setSelectedIndex(0);
        }
        updateRightPanel();
    }

    private void updateRightPanel() {
        rightCardLayout.show(rightPanel, listModel.isEmpty() ? CARD_EMPTY : CARD_EDIT);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        AnAction copyButton = createCopyButton();
        JPanel listPanel = ToolbarDecorator.createDecorator(repoList)
                .setAddAction(button -> addRepository())
                .setRemoveAction(button -> removeSelected())
                .addExtraAction(copyButton)
                .setRemoveActionUpdater(e -> !repoList.isSelectionEmpty())
                .disableUpDownActions()
                .createPanel();

        JBSplitter splitter = new JBSplitter(false, 0.35f);
        splitter.setFirstComponent(listPanel);
        splitter.setSecondComponent(rightPanel);
        splitter.setPreferredSize(new Dimension(700, 400));
        return splitter;
    }

    private @NonNull AnActionButton createCopyButton() {
        return new AnActionButton(
                "Copy", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull com.intellij.openapi.actionSystem.AnActionEvent e) {
                copySelected();
            }

            @Override
            public boolean isEnabled() {
                return !repoList.isSelectionEmpty();
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    private JBLabel noRepositoryMessage() {
        JBLabel noRepositoryMessage = new JBLabel("Click (+) to add a new repository");
        noRepositoryMessage.setHorizontalAlignment(SwingConstants.CENTER);
        noRepositoryMessage.setVerticalAlignment(SwingConstants.CENTER);
        return noRepositoryMessage;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (repoList.getSelectedValue() != null) {
            return editPanel.validationInfo();
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        flushEditedIndex();
        persistList();
        super.doOKAction();
    }

    @Override
    protected @NotNull Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected void createDefaultActions() {
        super.createDefaultActions();
        setOKButtonText("Save");
    }

    private void addRepository() {
        flushEditedIndex();
        int newIndex = listModel.size();
        DataformRepositoryConfig newConfig = new DataformRepositoryConfig(UUID.randomUUID().toString(),
                "Repository " + (newIndex + 1), "", "", "");
        insertAndSelect(newConfig, newIndex);
        SwingUtilities.invokeLater(editPanel::focusLabel);
    }

    private void copySelected() {
        flushEditedIndex();
        int srcIdx = repoList.getSelectedIndex();
        if (srcIdx < 0) return;

        DataformRepositoryConfig src = listModel.get(srcIdx);
        String copyLabel = generateCopyLabel(src.displayName());
        DataformRepositoryConfig copy = new DataformRepositoryConfig(
                UUID.randomUUID().toString(),
                copyLabel,
                src.projectId(),
                src.repositoryId(),
                src.location()
        );
        int newIndex = listModel.size();
        insertAndSelect(copy, newIndex);
        SwingUtilities.invokeLater(editPanel::focusLabel);
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

        suppressListener = true;
        try {
            editedIndex = -1;
            listModel.remove(idx);
            if (!listModel.isEmpty()) {
                int nextIdx = Math.min(idx, listModel.size() - 1);
                repoList.setSelectedIndex(nextIdx);
                editedIndex = nextIdx;
                editPanel.load(listModel.get(nextIdx));
            } else {
                editPanel.clear();
            }
        } finally {
            suppressListener = false;
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Insère {@code config} à {@code index} dans le modèle, sélectionne la ligne
     * et charge le formulaire — tout en supprimant le listener pour éviter
     * les flushes parasites.
     */
    private void insertAndSelect(@NotNull DataformRepositoryConfig config, int index) {
        suppressListener = true;
        try {
            listModel.add(index, config);
            repoList.setSelectedIndex(index);
        } finally {
            suppressListener = false;
        }
        editedIndex = index;
        editPanel.load(config);
    }

    /**
     * Génère un label de copie unique de la forme "{base} (Copy)", "{base} (Copy 2)", etc.
     */
    @NotNull
    private String generateCopyLabel(@NotNull String base) {
        // Retirer un suffixe de copie existant pour repartir du label de base
        String root = base.replaceAll("\\s*\\(Copy(\\s+\\d+)?\\)$", "").trim();

        String candidate = root + " (Copy)";
        if (!labelExists(candidate)) return candidate;

        for (int n = 2; ; n++) {
            candidate = root + " (Copy " + n + ")";
            if (!labelExists(candidate)) return candidate;
        }
    }

    private boolean labelExists(@NotNull String label) {
        for (int i = 0; i < listModel.size(); i++) {
            if (label.equals(listModel.get(i).displayName())) return true;
        }
        return false;
    }

    /**
     * Persiste l'état du formulaire dans {@code listModel[editedIndex]}.
     * Le label existant sert de fallback si le champ label est vide.
     */
    private void flushEditedIndex() {
        if (editedIndex < 0 || editedIndex >= listModel.size()) return;
        DataformRepositoryConfig existing = listModel.get(editedIndex);
        String labelFallback = (existing.label() != null && !existing.label().isBlank())
                ? existing.label()
                : "Repository " + (editedIndex + 1);
        DataformRepositoryConfig updated = editPanel.buildConfig(labelFallback);
        listModel.set(editedIndex, updated);
        repoList.repaint();
    }

    private void persistList() {
        List<DataformRepositoryConfig> all = IntStream
                .range(0, listModel.size())
                .mapToObj(listModel::get)
                .toList();

        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(project);
        settings.saveAllConfigs(all);

        String activeId = settings.getActiveRepositoryId();
        boolean activeStillExists = activeId != null && all.stream().anyMatch(c -> c.repositoryConfigId().equals(activeId));
        if (!activeStillExists) {
            settings.setActiveRepositoryId(all.isEmpty() ? null : all.getFirst().repositoryConfigId());
        }
    }
}
