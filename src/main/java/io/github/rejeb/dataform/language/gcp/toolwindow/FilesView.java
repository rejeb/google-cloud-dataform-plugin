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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class FilesView extends JPanel {

    private final Project project;
    private final DataformRepoTreeModel treeModel;
    private final FileViewToolbar toolbar;

    public FilesView(
            @NotNull Project project,
            @NotNull DataformRepositoryConfig config,
            @NotNull GcpPanelActionDispatcher dispatcher
    ) {
        super(new BorderLayout());
        this.project = project;

        treeModel = new DataformRepoTreeModel(config.repositoryId());

        toolbar = new FileViewToolbar(
                project,
                () -> GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId(),
                dispatcher);

        Tree tree = new Tree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new DataformRepoTreeCellRenderer(treeModel));
        tree.setRowHeight(0);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;

                DataformRepoTreeModel.FileEntry entry =
                        treeModel.getFileEntry(path.getLastPathComponent());
                if (entry == null) return;

                openInEditor(entry);
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DataformRepoTreeModel.FileEntry entry =
                            treeModel.getFileEntry(path.getLastPathComponent());
                    if (entry != null) openInEditor(entry);
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
                DataformRepoTreeModel.FileEntry fileEntry = treeModel.getFileEntry(node);

                if (fileEntry != null) {
                    showFileContextMenu(tree, e.getX(), e.getY(), fileEntry);
                } else {
                    String dirPath = treeModel.getDirectoryPath(node);
                    if (dirPath != null) {
                        showDirContextMenu(tree, e.getX(), e.getY(), dirPath);
                    }
                }
            }

        });

        project.getMessageBus()
                .connect()
                .subscribe(DataformGcpEvent.TOPIC, new DataformGcpEvent() {
                    @Override
                    public void onFilesLoaded(@NotNull Map<String, String> files) {
                        treeModel.setLoading(false);
                        treeModel.setFiles(files);
                        TreeUtil.expandAll(tree);
                    }

                    @Override
                    public void onGitStatusesLoaded(@NotNull List<UncommittedChange> changes) {
                        treeModel.setGitStatuses(changes);
                    }

                    @Override
                    public void onNotification(@NotNull String message, @NotNull NotificationType type) {
                        NotificationGroupManager.getInstance()
                                .getNotificationGroup("Dataform.Notifications")
                                .createNotification(message, type)
                                .notify(project);
                    }
                });

        JLabel titleLabel = buildViewTitle("GCP remote project view");
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(toolbar, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);

    }


    static JLabel buildViewTitle(@NotNull String text) {
        JLabel label = new JLabel(text);
        label.setFont(JBUI.Fonts.label().asBold());
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0,
                        UIManager.getColor("Separator.separatorColor")),
                JBUI.Borders.empty(4, 8)));
        return label;
    }

    private void openInEditor(@NotNull DataformRepoTreeModel.FileEntry entry) {
        FileType fileType = FileTypeManager.getInstance()
                .getFileTypeByFileName(entry.displayName());

        LightVirtualFile virtualFile = new LightVirtualFile(
                entry.displayName() + " (read only)", fileType, entry.content());
        virtualFile.setWritable(false);

        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }

    @Nullable
    private VirtualFile findLocalFile(@NotNull String relativePath) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) return null;
        return baseDir.findFileByRelativePath(relativePath);
    }

    private void compareWithLocal(@NotNull DataformRepoTreeModel.FileEntry entry) {
        FileType fileType = FileTypeManager.getInstance()
                .getFileTypeByFileName(entry.displayName());

        DiffContent localContent;
        VirtualFile localFile = findLocalFile(entry.relativePath());
        if (localFile != null && localFile.exists()) {
            localContent = DiffContentFactory.getInstance().create(project, localFile);
        } else {
            localContent = DiffContentFactory.getInstance().create("", fileType);
        }

        DiffContent remoteContent = DiffContentFactory.getInstance()
                .create(entry.content(), fileType);

        DiffManager.getInstance().showDiff(
                project,
                new SimpleDiffRequest(
                        "Compare: " + entry.relativePath(),
                        localContent,
                        remoteContent,
                        "Local",
                        "Remote (GCP)"
                )
        );
    }


    private void showFileContextMenu(@NotNull JComponent parent, int x, int y,
                                     @NotNull DataformRepoTreeModel.FileEntry entry) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem compareItem = new JMenuItem("Compare with local");
        compareItem.addActionListener(ev -> compareWithLocal(entry));
        menu.add(compareItem);

        JMenuItem fetchItem = new JMenuItem("Fetch");
        fetchItem.addActionListener(ev -> fetchFileToLocal(entry));
        menu.add(fetchItem);

        menu.show(parent, x, y);
    }

    private void showDirContextMenu(@NotNull JComponent parent, int x, int y,
                                    @NotNull String dirPath) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem fetchItem = new JMenuItem("Fetch");
        fetchItem.addActionListener(ev -> fetchDirectoryToLocal(dirPath));
        menu.add(fetchItem);

        menu.show(parent, x, y);
    }

    private void fetchDirectoryToLocal(@NotNull String dirPath) {
        Map<String, String> allFiles = DataformGcpService.getInstance(project).getCachedFiles();

        String prefix = dirPath + "/";
        Map<String, String> matching = allFiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue));

        if (matching.isEmpty()) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Dataform.Notifications")
                    .createNotification("No files found under: " + dirPath,
                            NotificationType.WARNING)
                    .notify(project);
            return;
        }

        WriteCommandAction.runWriteCommandAction(project,
                "Fetch directory from GCP: " + dirPath, null, () -> {
                    int errors = 0;
                    for (Map.Entry<String, String> e : matching.entrySet()) {
                        try {
                            writeLocalFile(e.getKey(), e.getValue());
                        } catch (IOException ex) {
                            errors++;
                        }
                    }
                    if (errors > 0) {
                        int finalErrors = errors;
                        ApplicationManager.getApplication().invokeLater(() ->
                                NotificationGroupManager.getInstance()
                                        .getNotificationGroup("Dataform.Notifications")
                                        .createNotification(
                                                finalErrors + " file(s) failed to fetch.",
                                                NotificationType.ERROR)
                                        .notify(project)
                        );
                    }
                });
    }

    private void fetchFileToLocal(@NotNull DataformRepoTreeModel.FileEntry entry) {
        WriteCommandAction.runWriteCommandAction(project,
                "Fetch from GCP: " + entry.displayName(), null, () -> {
                    try {
                        writeLocalFile(entry.relativePath(), entry.content());
                    } catch (IOException ex) {
                        NotificationGroupManager.getInstance()
                                .getNotificationGroup("Dataform.Notifications")
                                .createNotification("Failed to fetch file: " + ex.getMessage(),
                                        NotificationType.ERROR)
                                .notify(project);
                    }
                });
    }

    /**
     * Crée ou écrase le fichier local au chemin relatif donné.
     * Doit être appelé dans un WriteCommandAction.
     */
    private void writeLocalFile(@NotNull String relativePath,
                                @NotNull String content) throws IOException {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) throw new IOException("Project base dir not found");

        String[] segments = relativePath.split("/");
        VirtualFile dir = baseDir;
        for (int i = 0; i < segments.length - 1; i++) {
            VirtualFile existing = dir.findChild(segments[i]);
            dir = (existing != null)
                    ? existing
                    : dir.createChildDirectory(this, segments[i]);
        }

        VirtualFile file = dir.findChild(segments[segments.length - 1]);
        if (file == null) {
            file = dir.createChildData(this, segments[segments.length - 1]);
        }
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }


}
