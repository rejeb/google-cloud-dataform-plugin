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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.gcp.toolwindow.action.OpenInConsoleAction;
import io.github.rejeb.dataform.language.gcp.workspace.MainBranchSource;
import io.github.rejeb.dataform.language.gcp.workspace.UncommittedChange;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the Files and Changes tabs do with a file of the Dataform workspace: the prototype file menu
 * (Compare with Local, Fetch to Local, Open in Google Cloud Console, Copy Path), acting on the file
 * the tab has selected, and opening the file read-only or its local copy.
 */
public final class WorkspaceFileActions {

    /**
     * A file of the workspace and its uncommitted change, if any.
     */
    public record Target(@NotNull String path, @Nullable UncommittedChange.ChangeState state) {

        boolean deleted() {
            return state == UncommittedChange.ChangeState.DELETED;
        }
    }

    private final Project project;
    private final Supplier<@Nullable Target> selection;
    private final AnAction compare;
    private final List<AnAction> fileMenu;

    /**
     * The actions of a tab whose selected file {@code selection} returns.
     */
    public WorkspaceFileActions(@NotNull Project project, @NotNull Supplier<@Nullable Target> selection) {
        this.project = project;
        this.selection = selection;
        this.compare = new TargetAction("Compare with Local", DataformIcons.DIFF,
                target -> !target.deleted() && DataformPaths.findInProject(project, target.path()) != null,
                target -> compareWithLocal(target.path()));
        AnAction showDiff = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON);
        if (showDiff != null) {
            compare.copyShortcutFrom(showDiff);
        }
        GcpRepositorySettings settings = GcpRepositorySettings.getInstance(project);
        this.fileMenu = List.of(
                compare,
                new TargetAction("Fetch to Local", DataformIcons.FETCH, target -> !target.deleted(),
                        target -> fetchToLocal(target.path())),
                Separator.getInstance(),
                new OpenInConsoleAction(settings::getActiveConfig, settings::getSelectedWorkspaceId, this::consoleFile),
                new TargetAction("Copy Path", DataformIcons.COPY, target -> true, target -> copyPath(target.path())));
    }

    /**
     * The menu of a file: Compare with Local, Fetch to Local, Open in Google Cloud Console (the file
     * in its workspace) and Copy Path.
     */
    public @NotNull List<AnAction> fileMenu() {
        return fileMenu;
    }

    /**
     * The menu of a folder of the workspace: Fetch to Local and Copy Path.
     */
    public @NotNull List<AnAction> directoryMenu(@NotNull String dirPath) {
        return List.of(
                DumbAwareAction.create("Fetch to Local", DataformIcons.FETCH, e -> fetchDirectory(dirPath)),
                Separator.getInstance(),
                DumbAwareAction.create("Copy Path", DataformIcons.COPY, e -> copyPath(dirPath)));
    }

    /**
     * Makes the shortcut of Compare with Local work in {@code component}.
     */
    public void installOn(@NotNull JComponent component, @NotNull Disposable parent) {
        compare.registerCustomShortcutSet(component, parent);
    }

    /**
     * Shows {@code actions} as a popup menu at the given point of {@code component}.
     */
    public static void showMenu(@NotNull JComponent component, int x, int y, @NotNull String place,
                                @NotNull List<AnAction> actions) {
        ActionManager.getInstance()
                .createActionPopupMenu(place, new DefaultActionGroup(actions))
                .getComponent()
                .show(component, x, y);
    }

    /**
     * Opens the project file at the workspace path; says so and returns false when the project has
     * no such file.
     */
    public boolean openLocal(@NotNull String path) {
        VirtualFile file = DataformPaths.findInProject(project, path);
        if (file == null || file.isDirectory()) {
            DataformNotifications.create(path + " is not in the project. Fetch it to local first.",
                            NotificationType.INFORMATION)
                    .notify(project);
            return false;
        }
        FileEditorManager.getInstance(project).openFile(file, true);
        return true;
    }

    /**
     * Opens the workspace version of the file in a read-only editor.
     */
    public void openReadOnly(@NotNull String path) {
        loadRemote("Loading " + nameOf(path) + " from the remote", () -> remoteContent(path),
                content -> showReadOnly(path, content));
    }

    /**
     * Shows the local file next to its workspace version.
     */
    public void compareWithLocal(@NotNull String path) {
        loadRemote("Loading " + nameOf(path) + " from the remote", () -> new RemoteVersion(remoteContent(path), remoteLabel()),
                remote -> showDiff(path, remote));
    }

    private @Nullable String consoleFile() {
        Target target = selection.get();
        return target == null || target.deleted() ? null : target.path();
    }

    private void fetchToLocal(@NotNull String path) {
        loadRemote("Fetching " + nameOf(path) + " from the remote", () -> remoteContent(path),
                content -> WriteCommandAction.runWriteCommandAction(project, "Fetch from remote: " + nameOf(path), null, () -> {
                    try {
                        writeLocalFile(path, content);
                    } catch (IOException ex) {
                        DataformNotifications.create("Failed to fetch file: " + ex.getMessage(), NotificationType.ERROR)
                                .notify(project);
                    }
                }));
    }

    private void fetchDirectory(@NotNull String dirPath) {
        String workspace = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        loadRemote("Fetching " + dirPath + " from the remote",
                () -> DataformGcpService.getInstance(project).fetchCode(workspace),
                allFiles -> writeDirectory(dirPath, allFiles));
    }

    private void writeDirectory(@NotNull String dirPath, @NotNull Map<String, String> allFiles) {
        String prefix = dirPath + "/";
        Map<String, String> matching = allFiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (matching.isEmpty()) {
            DataformNotifications.create("No files found under: " + dirPath, NotificationType.WARNING)
                    .notify(project);
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, "Fetch directory from remote: " + dirPath, null, () -> {
            int errors = 0;
            for (Map.Entry<String, String> e : matching.entrySet()) {
                try {
                    writeLocalFile(e.getKey(), e.getValue());
                } catch (IOException ex) {
                    errors++;
                }
            }
            if (errors > 0) {
                int failed = errors;
                ApplicationManager.getApplication().invokeLater(() ->
                        DataformNotifications.create(failed + " file(s) failed to fetch.", NotificationType.ERROR)
                                .notify(project));
            }
        });
    }

    private void writeLocalFile(@NotNull String relativePath, @NotNull String content) throws IOException {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) throw new IOException("Project base dir not found");
        DataformPaths.writeText(baseDir, relativePath, content);
    }

    private static void copyPath(@NotNull String path) {
        CopyPasteManager.getInstance().setContents(new StringSelection(path));
    }

    /**
     * The name of the remote version of a file, in a diff. May read the repository: must be called off
     * the EDT.
     */
    @NotNull String remoteLabel() {
        boolean noWorkspace = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId() == null;
        return noWorkspace && DataformGcpService.getInstance(project).mainBranchSource() instanceof MainBranchSource.GitRemote gitRemote
                ? gitRemote.branch().trackingName() + " (Git remote)"
                : "Remote (GCP)";
    }

    private @NotNull String remoteContent(@NotNull String path) {
        String workspaceId = GcpRepositorySettings.getInstance(project).getSelectedWorkspaceId();
        return DataformGcpService.getInstance(project).getFileContent(workspaceId, path);
    }

    private <T> void loadRemote(@NotNull String title, @NotNull Supplier<T> loader, @NotNull Consumer<T> onLoaded) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            private T result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                result = loader.get();
            }

            @Override
            public void onSuccess() {
                if (!project.isDisposed()) {
                    onLoaded.accept(result);
                }
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
                DataformNotifications.create(title + " failed: " + error.getMessage(), NotificationType.ERROR)
                        .notify(project);
            }
        });
    }

    private void showReadOnly(@NotNull String path, @NotNull String content) {
        String fileName = nameOf(path) + "(readonly)";
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        Arrays.stream(editorManager.getOpenFiles())
                .filter(f -> f.getName().equals(fileName))
                .forEach(editorManager::closeFile);
        LightVirtualFile virtualFile = new LightVirtualFile(fileName, fileTypeOf(path), content);
        virtualFile.setWritable(false);
        editorManager.openFile(virtualFile, true);
    }

    private void showDiff(@NotNull String path, @NotNull RemoteVersion remote) {
        FileType fileType = fileTypeOf(path);
        VirtualFile localFile = DataformPaths.findInProject(project, path);
        DiffContent local = localFile != null && localFile.exists()
                ? DiffContentFactory.getInstance().create(project, localFile)
                : DiffContentFactory.getInstance().create("", fileType);
        DiffContent workspace = DiffContentFactory.getInstance().create(remote.content(), fileType);
        DiffManager.getInstance().showDiff(project,
                new SimpleDiffRequest("Compare: " + path, local, workspace, "Local", remote.label()));
    }

    private static @NotNull FileType fileTypeOf(@NotNull String path) {
        return FileTypeManager.getInstance().getFileTypeByFileName(nameOf(path));
    }

    private static @NotNull String nameOf(@NotNull String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private record RemoteVersion(@NotNull String content, @NotNull String label) {
    }

    private final class TargetAction extends DumbAwareAction {

        private final Predicate<Target> enabled;
        private final Consumer<Target> perform;

        TargetAction(@NotNull String text, @NotNull Icon icon, @NotNull Predicate<Target> enabled,
                     @NotNull Consumer<Target> perform) {
            super(text, null, icon);
            this.enabled = enabled;
            this.perform = perform;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            Target target = selection.get();
            e.getPresentation().setEnabled(target != null && enabled.test(target));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Target target = selection.get();
            if (target != null && enabled.test(target)) {
                perform.accept(target);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
