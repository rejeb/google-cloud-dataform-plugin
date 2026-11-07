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

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTreeBase;
import com.intellij.ui.CheckboxTreeListener;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.workspace.CommitRoute;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Changes tab: the uncommitted changes of the selected workspace, all checked, above the commit
 * message, the Commit button and the Push button. A change opens its local file on double-click and
 * offers the workspace file menu on right-click. Committing and pushing wait for the commit route to be
 * known: a project cloned from the Git remote of the repository commits with Git instead, and any other
 * project linked to a Git remote asks for the branch receiving the commits.
 */
public class CommitView extends JPanel {

    private static final String ROOT_LABEL = "Changes";
    private static final String CONTEXT_MENU_PLACE = "Dataform.Changes";
    private static final String COMMIT = "Commit";
    private static final String MESSAGE_REQUIRED = "Enter a commit message";
    private static final String PUSH_TOOLTIP = "Push the workspace commits to the Git remote";
    private static final String REPOSITORY_UNREADABLE = "The repository settings could not be read. Refresh to try again.";
    private static final String PUSH_POPUP_TITLE = "Push to Git Remote";
    private static final String USE_GIT = "This project is a Git clone of the repository's remote. "
            + "Commit and push with Git so the IDE's branch and history stay in step.";
    private static final String NEW_BRANCH = "New Branch…";
    private static final String CLONE_SUGGESTION = "This project is not a Git clone of the repository's remote, "
            + "so commits go through the Dataform workspace. Clone the repository to work with Git.";
    private static final String CLONE_REPOSITORY = "Clone Repository…";
    private static final int MESSAGE_ROWS = 3;

    private final Project project;
    private final GcpPanelActionDispatcher dispatcher;
    private final @Nullable String workspaceId;
    private final CheckedTreeNode rootNode = new CheckedTreeNode(ROOT_LABEL);
    private final CheckboxTree changesTree;
    private final WorkspaceFileActions fileActions;
    private final EditorTextField commitMessageField;
    private final ComponentValidator messageValidator;
    private final Action commitAction = action(COMMIT, e -> submit(false));
    private final Action commitAndPushAction = action("Commit and Push…", e -> submit(true));
    private final JBOptionButton commitButton = new JBOptionButton(commitAction, new Action[]{commitAndPushAction});
    private final JButton pushButton = new JButton("Push", DataformIcons.PUSH);
    private final GitLinks gitLinks;
    private @Nullable CommitRoute route;
    private @Nullable JComponent routeBanner;

    /**
     * Shows the changes of the selected workspace once they load; {@code selectWorkspace} runs from
     * the link shown while no workspace is selected.
     */
    public CommitView(
            @NotNull Project project,
            @NotNull GcpPanelActionDispatcher dispatcher,
            @NotNull Runnable selectWorkspace,
            @NotNull Disposable parent
    ) {
        this(project, dispatcher, selectWorkspace, parent, new IdeGitLinks(project));
    }

    CommitView(
            @NotNull Project project,
            @NotNull GcpPanelActionDispatcher dispatcher,
            @NotNull Runnable selectWorkspace,
            @NotNull Disposable parent,
            @NotNull GitLinks gitLinks
    ) {
        super(new BorderLayout());
        this.project = project;
        this.dispatcher = dispatcher;
        this.gitLinks = gitLinks;
        this.workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();

        changesTree = new CheckboxTree(new ChangeNodeRenderer(), rootNode,
                new CheckboxTreeBase.CheckPolicy(true, true, true, true));
        changesTree.setRootVisible(false);
        changesTree.setShowsRootHandles(false);
        changesTree.addCheckboxTreeListener(new CheckboxTreeListener() {
            @Override
            public void nodeStateChanged(@NotNull CheckedTreeNode node) {
                updateCommitButtons();
            }

            @Override
            public void mouseDoubleClicked(@NotNull CheckedTreeNode node) {
                if (node.getUserObject() instanceof UncommittedChange change) {
                    fileActions.openLocal(change.path());
                }
            }
        });
        fileActions = new WorkspaceFileActions(project, this::selectedChange);
        fileActions.installOn(changesTree, parent);
        changesTree.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component component, int x, int y) {
                showFileMenu(x, y);
            }
        });

        commitMessageField = new EditorTextField(project, PlainTextFileType.INSTANCE);
        commitMessageField.setOneLineMode(false);
        commitMessageField.setDisposedWith(parent);
        commitMessageField.setPlaceholder("Commit message");
        commitMessageField.setShowPlaceholderWhenFocused(true);
        commitMessageField.addSettingsProvider(editor -> {
            editor.getSettings().setUseSoftWraps(true);
            editor.setVerticalScrollbarVisible(true);
        });
        commitMessageField.setPreferredSize(new Dimension(0, messageHeight()));
        messageValidator = new ComponentValidator(parent).installOn(commitMessageField);
        commitMessageField.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (!commitMessageField.getText().isBlank()) {
                    messageValidator.updateInfo(null);
                }
            }
        });

        commitButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, Boolean.TRUE);
        pushButton.setToolTipText(PUSH_TOOLTIP);
        pushButton.addActionListener(e -> onPush());

        add(ScrollPaneFactory.createScrollPane(changesTree, true), BorderLayout.CENTER);
        add(commitArea(), BorderLayout.SOUTH);

        if (workspaceId == null) {
            EmptyStates.showWithLinkBelow(changesTree.getEmptyText(), "No workspace selected",
                    "Commits are made in a workspace.", "Select a workspace…", selectWorkspace);
        } else {
            showNoChanges();
        }
        commitMessageField.setEnabled(false);
        pushButton.setEnabled(false);
        updateCommitButtons();

        MessageBusConnection connection = project.getMessageBus().connect(parent);
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                if (events.stream().anyMatch(CommitView::isGitConfigEvent)) {
                    ApplicationManager.getApplication().invokeLater(CommitView.this::checkRouteAgain, project.getDisposed());
                }
            }
        });
        connection
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onCommitRouteLoaded(@Nullable CommitRoute route) {
                        setCommitRoute(route);
                    }

                    @Override
                    public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                        setChanges(changes);
                        commitMessageField.setText("");
                    }
                });
    }

    /**
     * The context menu of a change, acting on the selected change.
     */
    @NotNull List<AnAction> fileMenu() {
        return fileActions.fileMenu();
    }

    /**
     * The selected change, or null when the root or nothing is selected.
     */
    @Nullable WorkspaceFileActions.Target selectedChange() {
        TreePath path = changesTree.getSelectionPath();
        UncommittedChange change = path == null ? null : changeOf(path.getLastPathComponent());
        return change == null ? null : new WorkspaceFileActions.Target(change.path(), change.state());
    }

    private void showFileMenu(int x, int y) {
        TreePath path = changesTree.getPathForLocation(x, y);
        if (path == null || changeOf(path.getLastPathComponent()) == null) {
            return;
        }
        changesTree.setSelectionPath(path);
        WorkspaceFileActions.showMenu(changesTree, x, y, CONTEXT_MENU_PLACE, fileMenu());
    }

    private static @Nullable UncommittedChange changeOf(@Nullable Object node) {
        return node instanceof CheckedTreeNode checked && checked.getUserObject() instanceof UncommittedChange change
                ? change : null;
    }

    private @NotNull JPanel commitArea() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(commitButton);
        buttons.add(Box.createHorizontalStrut(JBUI.scale(8)));
        buttons.add(pushButton);

        JPanel area = new JPanel();
        area.setLayout(new BoxLayout(area, BoxLayout.Y_AXIS));
        area.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(8, 10, 10, 10)));
        commitMessageField.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        area.add(commitMessageField);
        area.add(Box.createVerticalStrut(JBUI.scale(8)));
        area.add(buttons);
        return area;
    }

    private int messageHeight() {
        int lineHeight = commitMessageField.getFontMetrics(commitMessageField.getFont()).getHeight();
        return lineHeight * MESSAGE_ROWS + JBUI.scale(12);
    }

    private static @NotNull Action action(@NotNull String name, @NotNull Consumer<ActionEvent> perform) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                perform.accept(e);
            }
        };
    }

    /**
     * Replaces the tree content with the given uncommitted changes, all checked.
     */
    public void setChanges(@NotNull List<UncommittedChange> changes) {
        rootNode.removeAllChildren();
        rootNode.setChecked(true);
        for (UncommittedChange change : changes) {
            CheckedTreeNode node = new CheckedTreeNode(change);
            node.setChecked(true);
            rootNode.add(node);
        }
        ((DefaultTreeModel) changesTree.getModel()).reload();
        changesTree.setRootVisible(!changes.isEmpty());
        changesTree.expandRow(0);
        if (changes.isEmpty() && workspaceId != null) {
            showNoChanges();
        }
        commitMessageField.setEnabled(!changes.isEmpty() && commitsHere());
        updateCommitButtons();
    }

    private void setCommitRoute(@Nullable CommitRoute route) {
        this.route = route;
        pushButton.setEnabled(workspaceId != null && commitsHere());
        pushButton.setToolTipText(route == null ? REPOSITORY_UNREADABLE : PUSH_TOOLTIP);
        commitButton.setToolTipText(route == null ? REPOSITORY_UNREADABLE : null);
        commitMessageField.setEnabled(rootNode.getChildCount() > 0 && commitsHere());
        showRouteBanner();
        updateCommitButtons();
    }

    private boolean commitsHere() {
        return route != null && route.commitsThroughWorkspace();
    }

    private void showRouteBanner() {
        if (routeBanner != null) {
            remove(routeBanner);
            routeBanner = null;
        }
        InlineBanner banner = route == null ? null : bannerOf(route);
        if (banner != null) {
            routeBanner = FilesView.bannerRow(banner);
            add(routeBanner, BorderLayout.NORTH);
        }
        revalidate();
        repaint();
    }

    private @Nullable InlineBanner bannerOf(@NotNull CommitRoute route) {
        if (route.handsOverToGit()) {
            return bannerWithLink(USE_GIT, NEW_BRANCH, gitLinks.newBranch(this));
        }
        return route.suggestsCloning() ? bannerWithLink(CLONE_SUGGESTION, CLONE_REPOSITORY, gitLinks.cloneRepository()) : null;
    }

    private static @NotNull InlineBanner bannerWithLink(@NotNull String message, @NotNull String linkText, @Nullable Runnable link) {
        InlineBanner banner = Banners.info(message);
        if (link != null) {
            banner.addAction(linkText, link);
        }
        return banner;
    }

    private static boolean isGitConfigEvent(@NotNull VFileEvent event) {
        String path = event.getPath();
        return path.endsWith("/.git") || path.endsWith("/.git/config");
    }

    private void checkRouteAgain() {
        if (route == null || route.repository().hasGitRemote()) {
            dispatcher.refreshWhenCommitRouteChanges(route);
        }
    }

    private void showNoChanges() {
        EmptyStates.show(changesTree.getEmptyText(), "No changes in " + workspaceId,
                "Edit files in the workspace or push project files to it.");
    }

    private void updateCommitButtons() {
        int checked = getCheckedChanges().size();
        boolean canCommit = workspaceId != null && checked > 0 && commitsHere();
        commitAction.putValue(Action.NAME, checked == 0 ? COMMIT : COMMIT + " " + checked + (checked == 1 ? " file" : " files"));
        commitAction.setEnabled(canCommit);
        commitAndPushAction.setEnabled(canCommit);
        commitButton.setEnabled(canCommit);
    }

    private List<UncommittedChange> getCheckedChanges() {
        UncommittedChange[] checked = changesTree.getCheckedNodes(UncommittedChange.class, null);
        return Arrays.asList(checked);
    }

    private void submit(boolean push) {
        if (workspaceId == null || getCheckedChanges().isEmpty()) {
            return;
        }
        String message = commitMessageField.getText().trim();
        if (message.isBlank()) {
            messageValidator.updateInfo(new ValidationInfo(MESSAGE_REQUIRED, commitMessageField));
            commitMessageField.requestFocusInWindow();
            return;
        }
        List<String> paths = getCheckedChanges().stream().map(UncommittedChange::path).toList();
        if (push) {
            choosePushBranch(commitButton, branch -> dispatcher.commitAndPush(workspaceId, paths, message, branch));
        } else {
            dispatcher.commitChanges(workspaceId, paths, message);
        }
    }

    private void onPush() {
        if (workspaceId != null) {
            choosePushBranch(pushButton, branch -> dispatcher.pushGitCommits(workspaceId, branch));
        }
    }

    private void choosePushBranch(@NotNull JComponent anchor, @NotNull Consumer<@Nullable String> push) {
        if (route == null || workspaceId == null) {
            return;
        }
        List<String> branches = route.repository().pushBranches(workspaceId);
        if (branches.isEmpty()) {
            push.accept(null);
            return;
        }
        JBPopupFactory.getInstance().createListPopup(new PushBranchStep(branches, push)).showUnderneathOf(anchor);
    }

    private static final class PushBranchStep extends BaseListPopupStep<String> {

        private final Consumer<@Nullable String> push;

        private PushBranchStep(@NotNull List<String> branches, @NotNull Consumer<@Nullable String> push) {
            super(PUSH_POPUP_TITLE, branches, DataformIcons.BRANCH);
            this.push = push;
        }

        @Override
        public @NotNull String getTextFor(String branch) {
            return "Push to " + branch;
        }

        @Override
        public @Nullable PopupStep<?> onChosen(String branch, boolean finalChoice) {
            return doFinalStep(() -> push.accept(branch));
        }
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
                Icon fileIcon = fileIconFor(filename);
                if (fileIcon != null) getTextRenderer().setIcon(fileIcon);
                getTextRenderer().append(filename, WorkspaceFileStatuses.attributes(change.state()));
                if (parent != null) {
                    getTextRenderer().append("  " + parent, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            } else if (userObject instanceof String label) {
                getTextRenderer().append(label, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                int count = node.getChildCount();
                getTextRenderer().append("  " + count + (count == 1 ? " file" : " files"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                getThreeStateCheckBox().setVisible(count > 0);
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
            return idx > 0 ? path.substring(0, idx) : null;
        }
    }
}
