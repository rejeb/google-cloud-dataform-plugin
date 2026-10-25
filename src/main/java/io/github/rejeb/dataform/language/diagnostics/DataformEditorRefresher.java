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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotifications;
import io.github.rejeb.dataform.language.SqlxFileType;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Repaints what shows Dataform diagnostics once they have changed: the annotations produced by the
 * daemon and the banners above the editors. Both live on the EDT, so callers can invoke this from
 * any thread.
 *
 * <p>One change of state reaches here from several listeners at once — a save, the compilation it
 * starts, the graph it publishes, the schemas extracted after it — so requests made while one is
 * waiting for the EDT are merged into it, and only the open Dataform files are highlighted again:
 * nothing another file shows depends on what changed.</p>
 *
 * <p>The daemon is left alone under a test. Nothing there watches the editor for a repaint: a test
 * that wants annotations asks for them, and running them itself is what it measures. The restart
 * would only ever arrive in the middle of that, where the platform refuses a model change and
 * fails whichever test happened to be highlighting — a file created by one test restarting the
 * daemon under the next.</p>
 */
public final class DataformEditorRefresher {

    private static final String RESTART_REASON = "Dataform editor refresh";
    private static final Key<AtomicBoolean> PENDING = Key.create("dataform.editorRefresh.pending");

    private DataformEditorRefresher() {
    }

    /**
     * Repaints every view of the Dataform diagnostics of the project: the inlays under the problems
     * as well as the annotations and the banners {@link #refresh} repaints.
     *
     * @param project the project whose editors to repaint
     */
    public static void refreshWithInlays(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        ValidationProblemInlayManager.getInstance(project).refreshAll();
        refresh(project);
    }

    /**
     * Restarts the daemon on the open Dataform files and refreshes the editor banners of the
     * project, once for every request made before the previous one reached the EDT.
     */
    public static void refresh(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        AtomicBoolean pending = ((UserDataHolderEx) project).putUserDataIfAbsent(PENDING, new AtomicBoolean());
        if (!pending.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            pending.set(false);
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
                restartDataformFiles(project);
            }
            EditorNotifications.getInstance(project).updateAllNotifications();
        }, project.getDisposed());
    }

    private static void restartDataformFiles(@NotNull Project project) {
        DaemonCodeAnalyzer daemon = DaemonCodeAnalyzer.getInstance(project);
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            if (!file.isValid() || !isDataformFile(file)) {
                continue;
            }
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
                daemon.restart(psiFile, RESTART_REASON);
            }
        }
    }

    private static boolean isDataformFile(@NotNull VirtualFile file) {
        return SqlxFileType.INSTANCE.equals(file.getFileType()) || DataformProjectLayout.isDataformSource(file);
    }
}
