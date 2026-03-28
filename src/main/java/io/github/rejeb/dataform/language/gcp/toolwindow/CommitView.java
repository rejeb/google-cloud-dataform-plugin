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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.RefreshAction;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class CommitView extends JPanel {

    private final Project project;
    private final GcpPanelActionDispatcher dispatcher;

    private final CheckedTreeNode rootNode = new CheckedTreeNode("Changes");
    private final CheckboxTree changesTree;
    private final JTextArea commitMessageField = new JBTextArea(4, 40);

    public CommitView(
            @NotNull Project project,
            @NotNull GcpPanelActionDispatcher dispatcher
    ) {
        super(new BorderLayout());
        this.project = project;
        this.dispatcher = dispatcher;

        changesTree = new CheckboxTree(
                new ChangeNodeRenderer(),
                rootNode,
                new CheckboxTreeBase.CheckPolicy(
                        true,
                        true,
                        true,
                        true
                )
        );
        changesTree.setRootVisible(true);
        changesTree.setShowsRootHandles(true);

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
                        commitMessageField.setText("");
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
        JPanel filesPanel = new JPanel(new BorderLayout());
        filesPanel.add(new JBScrollPane(changesTree), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, filesPanel, buildMessagePanel());
        splitPane.setResizeWeight(0.6);
        splitPane.setBorder(null);

        JButton commitBtn = new JButton("Commit");
        JButton pushBtn = new JButton("Push");
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
     * Replaces the tree content with the given uncommitted changes and checks all nodes.
     */
    public void setChanges(@NotNull List<UncommittedChange> changes) {
        rootNode.removeAllChildren();
        rootNode.setUserObject("Changes" + (changes.isEmpty() ? "" : " · " + changes.size() + " files"));
        rootNode.setChecked(false);

        for (UncommittedChange change : changes) {
            CheckedTreeNode node = new CheckedTreeNode(change);
            node.setChecked(false);
            rootNode.add(node);
        }

        ((DefaultTreeModel) changesTree.getModel()).reload();
        changesTree.expandRow(0);
    }


    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<UncommittedChange> getCheckedChanges() {
        UncommittedChange[] checked = changesTree.getCheckedNodes(UncommittedChange.class, null);
        return Arrays.asList(checked);
    }

    private void onCommit() {
        String message = commitMessageField.getText().trim();
        List<UncommittedChange> checked = getCheckedChanges();
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (valid()) {
            dispatcher.commitChanges(workspaceId,
                    checked.stream().map(UncommittedChange::path).toList(), message);
        }
    }

    private void onCommitAndPush() {
        String message = commitMessageField.getText().trim();
        List<UncommittedChange> checked = getCheckedChanges();
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (valid()) {
            dispatcher.commitAndPush(workspaceId,
                    checked.stream().map(UncommittedChange::path).toList(), message);
        }

    }

    public boolean valid() {
        String message = commitMessageField.getText().trim();
        if (message.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a commit message.", "Commit Message Required",
                    JOptionPane.WARNING_MESSAGE);
            commitMessageField.requestFocus();
            return false;
        }
        List<UncommittedChange> checked = getCheckedChanges();
        if (checked.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one file to commit.", "No Files Selected",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (workspaceId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a workspace first.", "No Workspace Selected",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
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

    private static final class ChangeNodeRenderer extends CheckboxTree.CheckboxTreeCellRenderer {

        @Override
        public void customizeRenderer(
                JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus
        ) {
            if (!(value instanceof CheckedTreeNode node)) return;
            Object userObject = node.getUserObject();

            if (userObject instanceof UncommittedChange change) {
                String filename = extractFilename(change.path());
                String parent = extractParent(change.path());
                Color nameColor = colorFor(change.state());
                Icon fileIcon = fileIconFor(filename);

                if (fileIcon != null) getTextRenderer().setIcon(fileIcon);

                getTextRenderer().append(filename,
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, nameColor));

                if (parent != null) {
                    getTextRenderer().append("  " + parent,
                            SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                }

            } else if (userObject instanceof String label) {
                getTextRenderer().append(label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                getThreeStateCheckBox().setVisible(node.getChildCount() > 0);
            }
        }

        @Nullable
        private static Icon fileIconFor(@NotNull String filename) {
            FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(filename);
            return fileType.getIcon();
        }

        private static String extractFilename(@NotNull String path) {
            int idx = path.lastIndexOf('/');
            return idx >= 0 ? path.substring(idx + 1) : path;
        }

        @Nullable
        private static String extractParent(@NotNull String path) {
            int idx = path.lastIndexOf('/');
            if (idx <= 0) return null;
            int prev = path.lastIndexOf('/', idx - 1);
            return prev >= 0 ? path.substring(prev + 1, idx) : path.substring(0, idx);
        }

        private static Color colorFor(@NotNull UncommittedChange.ChangeState state) {
            return switch (state) {
                case ADDED -> JBColor.green;
                case MODIFIED -> JBColor.blue;
                case DELETED -> JBColor.GRAY;
                case HAS_CONFLICTS -> JBColor.RED;
                default -> UIManager.getColor("Label.foreground");
            };
        }
    }
}
