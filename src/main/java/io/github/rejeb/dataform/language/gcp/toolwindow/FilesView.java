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

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class FilesView extends JPanel {

    private static final String CONTEXT_MENU_PLACE = "Dataform.Files";
    private static final String MAIN_BRANCH_EXPLANATION =
            "Read-only view of the repository main branch. Pick a workspace to compare, pull or push files.";
    private static final String NO_WORKSPACE_SELECTED = "No workspace selected";
    private static final String NEEDS_LOCAL_CLONE = "Reading the Git remote needs a local clone.";
    private static final String SELECT_WORKSPACE = "Select a workspace…";

    /**
     * What a double-click on a remote file does.
     */
    enum OpenMode {
        READ_ONLY,
        COMPARE,
        EXPLAIN_DELETED
    }

    private final Project project;
    private final DataformRepoTreeModel treeModel;
    private final Tree tree;
    private final WorkspaceFileActions fileActions;
    private final Runnable selectWorkspace;
    private @Nullable JComponent mainBranchBanner;

    public FilesView(
            @NotNull Project project,
            @NotNull DataformRepositoryConfig config,
            @NotNull Runnable selectWorkspace,
            @NotNull Disposable parent
    ) {
        super(new BorderLayout());
        this.project = project;
        this.selectWorkspace = selectWorkspace;

        treeModel = new DataformRepoTreeModel(config.repositoryId());
        tree = buildTree();
        fileActions = new WorkspaceFileActions(project, this::selectedFile);
        fileActions.installOn(tree, parent);

        project.getMessageBus()
                .connect(parent)
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onMainBranchLoaded(@NotNull MainBranchSource source) {
                        showNoWorkspaceState(source);
                    }

                    @Override
                    public void onFilesLoaded(@NotNull List<String> files) {
                        treeModel.setLoading(false);
                        treeModel.setFiles(files);
                        TreeUtil.expandAll(tree);
                    }

                    @Override
                    public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                        TreeState expanded = TreeState.createOn(tree);
                        treeModel.setGitStatuses(changes);
                        expanded.applyTo(tree);
                    }

                    @Override
                    public void onNotification(@NotNull String message, @NotNull NotificationType type) {
                        DataformNotifications.create(message, type)
                                .notify(project);
                    }
                });

        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER);
        add(footer(config), BorderLayout.SOUTH);
    }

    /**
     * What the view says when no workspace is selected: where the files come from, or that it needs a
     * workspace when they cannot be read. Waits for the source to be known, so a banner never flashes
     * for the wrong one.
     */
    private void showNoWorkspaceState(@NotNull MainBranchSource source) {
        boolean noWorkspace = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId() == null;
        if (noWorkspace && source instanceof MainBranchSource.Unavailable) {
            EmptyStates.showWithLinkBelow(tree.getEmptyText(), NO_WORKSPACE_SELECTED, NEEDS_LOCAL_CLONE,
                    SELECT_WORKSPACE, selectWorkspace);
        } else {
            tree.getEmptyText().setText(StatusText.getDefaultEmptyText());
        }
        showMainBranchBanner(noWorkspace ? explanationOf(source) : null);
    }

    private static @Nullable String explanationOf(@NotNull MainBranchSource source) {
        return switch (source) {
            case MainBranchSource.Dataform dataform -> MAIN_BRANCH_EXPLANATION;
            case MainBranchSource.GitRemote gitRemote -> "This is " + gitRemote.branch().name()
                    + " on the Git remote (" + gitRemote.branch().trackingName() + "), not a Dataform branch. "
                    + "It is read-only: pick a workspace to pull or push files.";
            case MainBranchSource.Unavailable unavailable -> null;
        };
    }

    private void showMainBranchBanner(@Nullable String explanation) {
        if (mainBranchBanner != null) {
            remove(mainBranchBanner);
            mainBranchBanner = null;
        }
        if (explanation != null) {
            mainBranchBanner = bannerRow(Banners.info(explanation));
            add(mainBranchBanner, BorderLayout.NORTH);
        }
        revalidate();
        repaint();
    }

    static @NotNull JComponent bannerRow(@NotNull JComponent banner) {
        JPanel bannerRow = new JPanel(new BorderLayout());
        bannerRow.setOpaque(false);
        bannerRow.setBorder(JBUI.Borders.empty(8, 10, 0, 10));
        bannerRow.add(banner, BorderLayout.CENTER);
        return bannerRow;
    }

    /**
     * What a double-click does on a file in the given workspace state.
     */
    static @NotNull OpenMode openModeOf(@Nullable UncommittedChange.ChangeState state) {
        if (state == UncommittedChange.ChangeState.MODIFIED) {
            return OpenMode.COMPARE;
        }
        if (state == UncommittedChange.ChangeState.DELETED) {
            return OpenMode.EXPLAIN_DELETED;
        }
        return OpenMode.READ_ONLY;
    }

    /**
     * The context menu of a remote file, acting on the selected file.
     */
    @NotNull List<AnAction> fileMenu() {
        return fileActions.fileMenu();
    }

    /**
     * The context menu of a remote folder.
     */
    @NotNull List<AnAction> directoryMenu(@NotNull String dirPath) {
        return fileActions.directoryMenu(dirPath);
    }

    private @Nullable WorkspaceFileActions.Target selectedFile() {
        TreePath path = tree.getSelectionPath();
        DataformRepoTreeModel.FileEntry entry = path == null ? null : treeModel.getFileEntry(path.getLastPathComponent());
        return entry == null ? null
                : new WorkspaceFileActions.Target(entry.relativePath(), treeModel.getChangeState(entry.relativePath()));
    }

    private static @NotNull JComponent footer(@NotNull DataformRepositoryConfig config) {
        JBLabel label = new JBLabel(config.projectId() + " · " + config.location());
        label.setFont(JBFont.medium());
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(4, 12)));
        return label;
    }

    private void open(@NotNull DataformRepoTreeModel.FileEntry entry) {
        String path = entry.relativePath();
        switch (openModeOf(treeModel.getChangeState(path))) {
            case COMPARE -> fileActions.compareWithLocal(path);
            case EXPLAIN_DELETED -> DataformNotifications.create(
                            path + " was deleted in the workspace; it only exists in the commit history.",
                            NotificationType.INFORMATION)
                    .notify(project);
            case READ_ONLY -> fileActions.openReadOnly(path);
        }
    }

    private @NotNull Tree buildTree() {
        Tree tree = new Tree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setCellRenderer(new DataformRepoTreeCellRenderer(treeModel));
        tree.setRowHeight(0);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DataformRepoTreeModel.FileEntry entry =
                            treeModel.getFileEntry(path.getLastPathComponent());
                    if (entry != null) open(entry);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                tree.setSelectionPath(path);

                Object node = path.getLastPathComponent();
                if (treeModel.getFileEntry(node) != null) {
                    WorkspaceFileActions.showMenu(tree, e.getX(), e.getY(), CONTEXT_MENU_PLACE, fileMenu());
                } else {
                    String dirPath = treeModel.getDirectoryPath(node);
                    if (dirPath != null) {
                        WorkspaceFileActions.showMenu(tree, e.getX(), e.getY(), CONTEXT_MENU_PLACE,
                                directoryMenu(dirPath));
                    }
                }
            }
        });
        return tree;
    }
}
