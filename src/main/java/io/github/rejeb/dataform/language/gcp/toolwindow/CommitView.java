/*
 * Licensed to the Apache Software Foundation (ASF) ...
 */
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpGitStatusesListener;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CommitView extends JPanel {

    private static final Color COLOR_ADDED    = new Color(0x629755);
    private static final Color COLOR_MODIFIED = new Color(0x6897BB);
    private static final Color COLOR_DELETED  = new Color(0x808080);
    private static final Color COLOR_CONFLICT = new Color(0xBC3F3C);

    private final Project project;
    private final DataformGcpPanel.PanelCallback callback;

    private final DefaultListModel<UncommittedChange> listModel = new DefaultListModel<>();
    private final JBList<UncommittedChange> changeList = new JBList<>(listModel);
    private final JTextArea commitMessageField = new JBTextArea(4, 40);

    public CommitView(
            @NotNull Project project,
            @NotNull DataformGcpPanel.PanelCallback callback
    ) {
        super(new BorderLayout());
        this.project = project;
        this.callback = callback;

        changeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changeList.setCellRenderer(new ChangeListCellRenderer());

        commitMessageField.setLineWrap(true);
        commitMessageField.setWrapStyleWord(true);
        commitMessageField.putClientProperty("StatusVisibleFunction",
                (java.util.function.BooleanSupplier) () -> true);

        add(FilesView.buildViewTitle("Commit"), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        // S'abonner au message bus pour recevoir les git statuses depuis FilesView
        project.getMessageBus()
                .connect()
                .subscribe(DataformGcpGitStatusesListener.TOPIC,
                        (DataformGcpGitStatusesListener) this::setChanges);
    }

    // -------------------------------------------------------------------------
    // Private builders
    // -------------------------------------------------------------------------

    private JPanel buildContent() {
        // Panneau liste des fichiers avec cases à cocher
        changeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel filesPanel = new JPanel(new BorderLayout());
        filesPanel.setBorder(new TitledBorder("Changed files"));

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        // La liste est rendue avec des cases à cocher via le renderer
        filesPanel.add(new JBScrollPane(changeList), BorderLayout.CENTER);

        // Panneau message de commit
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBorder(new TitledBorder("Commit message"));
        commitMessageField.setLineWrap(true);
        commitMessageField.setWrapStyleWord(true);
        messagePanel.add(new JBScrollPane(commitMessageField), BorderLayout.CENTER);

        // Boutons
        JButton commitBtn = new JButton("Commit");
        JButton pushBtn = new JButton("Push");
        JButton commitPushBtn = new JButton("Commit & Push");

        commitBtn.addActionListener(e -> onCommit());
        pushBtn.addActionListener(e -> onPush());
        commitPushBtn.addActionListener(e -> { onCommit(); onPush(); });

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttonsPanel.add(pushBtn);
        buttonsPanel.add(commitBtn);
        buttonsPanel.add(commitPushBtn);

        // Layout vertical : liste (flexible) + message + boutons
        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(JBUI.Borders.empty(4));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, filesPanel, messagePanel);
        splitPane.setResizeWeight(0.6);
        splitPane.setBorder(null);

        content.add(splitPane, BorderLayout.CENTER);
        content.add(buttonsPanel, BorderLayout.SOUTH);
        return content;
    }

    private void setChanges(@NotNull List<UncommittedChange> changes) {
        listModel.clear();
        changes.forEach(listModel::addElement);
        // Auto-sélectionner tous les fichiers
        if (!changes.isEmpty()) {
            changeList.setSelectionInterval(0, changes.size() - 1);
        }
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
        String workspaceId =
                GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (workspaceId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a workspace first.", "No Workspace Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> paths = selected.stream().map(UncommittedChange::path).toList();
        callback.onCommitWorkspaceChanges(workspaceId, paths, message);
    }

    private void onPush() {
        String workspaceId =
                GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        if (workspaceId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a workspace first.", "No Workspace Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        callback.onPushGitCommits(workspaceId);
    }

    // -------------------------------------------------------------------------
    // Renderer
    // -------------------------------------------------------------------------

    private static final class ChangeListCellRenderer
            extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus
        ) {
            JCheckBox checkBox = new JCheckBox();
            if (value instanceof UncommittedChange change) {
                checkBox.setText(change.path());
                checkBox.setForeground(colorFor(change.state()));
                checkBox.setSelected(list.isSelectedIndex(index));
            }
            checkBox.setBackground(isSelected
                    ? list.getSelectionBackground()
                    : list.getBackground());
            return checkBox;
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
