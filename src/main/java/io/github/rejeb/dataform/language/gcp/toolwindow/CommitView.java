/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.RefreshAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CommitView extends JPanel {

    private static final Color COLOR_ADDED    = JBColor.green;
    private static final Color COLOR_MODIFIED = JBColor.blue;
    private static final Color COLOR_DELETED  = JBColor.gray;
    private static final Color COLOR_CONFLICT = JBColor.red;

    private final Project project;
    private final GcpPanelActionDispatcher dispatcher;

    private final DefaultListModel<UncommittedChange> listModel = new DefaultListModel<>();
    private final JBList<UncommittedChange> changeList = new JBList<>(listModel);
    private final JTextArea commitMessageField = new JBTextArea(4, 40);
    private final JBLabel changesHeader = new JBLabel("Changes");

    public CommitView(
            @NotNull Project project,
            @NotNull GcpPanelActionDispatcher dispatcher
    ) {
        super(new BorderLayout());
        this.project = project;
        this.dispatcher = dispatcher;

        changeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changeList.setCellRenderer(new ChangeListCellRenderer());

        commitMessageField.setLineWrap(true);
        commitMessageField.setWrapStyleWord(true);

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        project.getMessageBus()
                .connect()
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                        setChanges(changes);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Private builders
    // -------------------------------------------------------------------------

    private JComponent buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new RefreshAction(dispatcher));

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("DataformCommitView", group, true);
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    private JPanel buildContent() {
        JPanel filesPanel = buildFilesPanel();
        JPanel messagePanel = buildMessagePanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filesPanel, messagePanel);
        splitPane.setResizeWeight(0.6);
        splitPane.setBorder(null);

        JButton commitBtn     = new JButton("Commit");
        JButton pushBtn       = new JButton("Push");
        JButton commitPushBtn = new JButton("Commit and Push...");

        commitBtn.addActionListener(e -> onCommit());
        pushBtn.addActionListener(e -> onPush());
        commitPushBtn.addActionListener(e -> onCommitAndPush());

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttonsPanel.add(pushBtn);
        buttonsPanel.add(commitBtn);
        buttonsPanel.add(commitPushBtn);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(JBUI.Borders.empty(4));
        content.add(splitPane, BorderLayout.CENTER);
        content.add(buttonsPanel, BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildFilesPanel() {
        changesHeader.setBorder(JBUI.Borders.empty(4, 6));
        changesHeader.setForeground(JBUI.CurrentTheme.Label.foreground());

        JPanel filesPanel = new JPanel(new BorderLayout());
        filesPanel.add(changesHeader, BorderLayout.NORTH);
        filesPanel.add(new JBScrollPane(changeList), BorderLayout.CENTER);
        return filesPanel;
    }

    private JPanel buildMessagePanel() {
        JBLabel messageLabel = new JBLabel("Commit message");
        messageLabel.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        messageLabel.setFont(JBUI.Fonts.label(11));
        messageLabel.setBorder(JBUI.Borders.empty(4, 6, 2, 6));

        JPanel labelWrapper = new JPanel(new BorderLayout());
        labelWrapper.add(new JSeparator(), BorderLayout.NORTH);
        labelWrapper.add(messageLabel, BorderLayout.CENTER);

        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.add(labelWrapper, BorderLayout.NORTH);
        messagePanel.add(new JBScrollPane(commitMessageField), BorderLayout.CENTER);
        return messagePanel;
    }


    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Replaces the current list of uncommitted changes and updates the header counter.
     */
    public void setChanges(@NotNull List<UncommittedChange> changes) {
        listModel.clear();
        if (!changes.isEmpty()) {
            changes.forEach(listModel::addElement);
            changeList.setSelectionInterval(0, changes.size() - 1);
        }
        updateChangesHeader(changes.size());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void updateChangesHeader(int count) {
        changesHeader.setText("Changes" + (count > 0 ? " · " + count + " files" : ""));
    }

    private void onCommit() {
        String message = commitMessageField.getText().trim();
        if (message.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a commit message.", "Commit Message Required",
                    JOptionPane.WARNING_MESSAGE);
            commitMessageField.requestFocus();
            return;
        }
        List<UncommittedChange> selected = changeList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one file to commit.", "No Files Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (workspaceId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a workspace first.", "No Workspace Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> paths = selected.stream().map(UncommittedChange::path).toList();
        dispatcher.commitChanges(workspaceId, paths, message);
    }

    private void onCommitAndPush() {
        String message = commitMessageField.getText().trim();
        if (message.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a commit message.", "Commit Message Required",
                    JOptionPane.WARNING_MESSAGE);
            commitMessageField.requestFocus();
            return;
        }
        List<UncommittedChange> selected = changeList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one file to commit.", "No Files Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (workspaceId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a workspace first.", "No Workspace Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> paths = selected.stream().map(UncommittedChange::path).toList();
        dispatcher.commitAndPush(workspaceId, paths, message);
    }

    private void onPush() {
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (workspaceId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a workspace first.", "No Workspace Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        dispatcher.pushGitCommits(workspaceId);
    }

    // -------------------------------------------------------------------------
    // Renderer
    // -------------------------------------------------------------------------

    private static final class ChangeListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus
        ) {
            JCheckBox checkBox = new JCheckBox();
            if (value instanceof UncommittedChange change) {
                String filename = extractFilename(change.path());
                String parent   = extractParent(change.path());

                checkBox.setIcon(iconFor(change.state()));
                checkBox.setText(filename);
                checkBox.setForeground(colorFor(change.state()));
                checkBox.setSelected(list.isSelectedIndex(index));

                if (parent != null) {
                    // Append grayed parent folder to the right via HTML
                    checkBox.setText("<html>" + filename
                            + " <font color='gray'>" + parent + "</font></html>");
                }
            }
            checkBox.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            return checkBox;
        }

        private static String extractFilename(@NotNull String path) {
            int idx = path.lastIndexOf('/');
            return idx >= 0 ? path.substring(idx + 1) : path;
        }

        private static String extractParent(@NotNull String path) {
            int idx = path.lastIndexOf('/');
            if (idx <= 0) return null;
            int prev = path.lastIndexOf('/', idx - 1);
            return prev >= 0 ? path.substring(prev + 1, idx) : path.substring(0, idx);
        }

        private static Icon iconFor(@NotNull UncommittedChange.ChangeState state) {
            return switch (state) {
                case ADDED         -> com.intellij.icons.AllIcons.Actions.New;
                case MODIFIED      -> com.intellij.icons.AllIcons.Actions.Edit;
                case DELETED       -> com.intellij.icons.AllIcons.Actions.GC;
                case HAS_CONFLICTS -> com.intellij.icons.AllIcons.General.Error;
                default            -> com.intellij.icons.AllIcons.FileTypes.Unknown;
            };
        }

        private static Color colorFor(@NotNull UncommittedChange.ChangeState state) {
            return switch (state) {
                case ADDED         -> COLOR_ADDED;
                case MODIFIED      -> COLOR_MODIFIED;
                case DELETED       -> COLOR_DELETED;
                case HAS_CONFLICTS -> COLOR_CONFLICT;
                default            -> UIManager.getColor("Label.foreground");
            };
        }
    }
}
