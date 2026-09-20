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
package io.github.rejeb.dataform.language.folding;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.concurrency.ThreadingAssertions;
import io.github.rejeb.dataform.language.injection.SqlxInjectionRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Makes newly computed expression values visible in the editors of a file.
 *
 * <p>The platform only honours {@code isCollapsedByDefault} on the first folding pass of an editor,
 * so the regions are collapsed explicitly here — every one of them, expanded or not. A pass runs
 * when the file is opened or comes back into focus, and a value expanded to edit the code behind
 * it is done with by the time the person leaves and returns.</p>
 *
 * <p>New values change neither the document nor the PSI, and the platform keeps both the folding
 * and the injected SQL it computed for that state — the folding for as long as the document and
 * the dependencies of its descriptors stand still, and a hole without a value has no descriptor to
 * depend on anything. So the caches are dropped first, on the event thread, and the folding is
 * computed only once that is done: computed any earlier it would be the folding of the file
 * without its values, and the hole would never fold.</p>
 */
public final class DataformFoldingRefresher {


    private DataformFoldingRefresher() {
    }

    /**
     * Recomputes and collapses the Dataform value folds of every editor showing the given file.
     *
     * <p>Folding is computed on the calling background thread, because folding builders read indices,
     * which is forbidden on the EDT. Only the resulting fold operations run on the EDT.</p>
     */
    public static void refresh(@NotNull Project project, @NotNull VirtualFile file) {
        ThreadingAssertions.assertBackgroundThread();
        if (project.isDisposed() || !file.isValid()) {
            return;
        }
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document == null) {
            return;
        }

        List<Editor> editors = editorsOf(project, document);
        if (editors.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().invokeAndWait(() -> {
            SqlxInjectionRefresher.refresh(project, file);
            for (Editor editor : editors) {
                if (!editor.isDisposed()) {
                    CodeFoldingManager.getInstance(project).scheduleAsyncFoldingUpdate(editor);
                }
            }
        }, ModalityState.nonModal());
        MultilineSnapshot snapshot = ReadAction.nonBlocking(
                        () -> new MultilineSnapshot(
                                DataformMultilineValues.of(project, file, document),
                                document.getModificationStamp()))
                .executeSynchronously();

        for (Editor editor : editors) {
            FoldingSnapshot folding = ReadAction.nonBlocking(
                            () -> new FoldingSnapshot(foldingUpdate(project, editor, document),
                                    document.getModificationStamp()))
                    .executeSynchronously();
            ApplicationManager.getApplication().invokeLater(() -> {
                boolean unchanged = document.getModificationStamp() == folding.documentStamp();
                applyAndCollapse(editor, unchanged ? folding.applyFolding() : null);
                if (!editor.isDisposed()
                        && document.getModificationStamp() == snapshot.documentStamp()) {
                    DataformMultilineFoldManager.apply(editor, snapshot.values());
                }
            }, ModalityState.nonModal(), project.getDisposed());
        }
    }

    /**
     * Collapses the Dataform value folds of every editor showing the file, without recomputing
     * anything: the values did not change, so the injections and the fold regions the platform
     * holds are the right ones already. This is what a file coming back into focus needs.
     */
    public static void collapse(@NotNull Project project, @NotNull VirtualFile file) {
        ThreadingAssertions.assertBackgroundThread();
        if (project.isDisposed() || !file.isValid()) {
            return;
        }
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document == null) {
            return;
        }
        List<Editor> editors = editorsOf(project, document);
        if (editors.isEmpty()) {
            return;
        }
        MultilineSnapshot snapshot = ReadAction.nonBlocking(
                        () -> new MultilineSnapshot(
                                DataformMultilineValues.of(project, file, document),
                                document.getModificationStamp()))
                .executeSynchronously();
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Editor editor : editors) {
                applyAndCollapse(editor, null);
                if (!editor.isDisposed()
                        && document.getModificationStamp() == snapshot.documentStamp()) {
                    DataformMultilineFoldManager.apply(editor, snapshot.values());
                }
            }
        }, ModalityState.nonModal(), project.getDisposed());
    }

    /**
     * A pending folding update together with the document stamp it was computed against. The
     * platform rejects an update whose document changed in between, so an edit landing before the
     * update reaches the EDT discards it; the daemon restarted above recomputes the regions.
     */
    private record FoldingSnapshot(@Nullable Runnable applyFolding, long documentStamp) {
    }

    /**
     * Multiline values together with the document stamp they were computed against. The values are
     * only applied when the document is still at that stamp, since custom fold regions are placed
     * by line numbers and would be painted at stale positions otherwise.
     */
    private record MultilineSnapshot(@NotNull List<DataformMultilineFoldManager.MultilineValue> values,
                                     long documentStamp) {
    }

    /**
     * Computes the folding update of an editor, or {@code null} when the document has been edited
     * since the last commit. The platform requires a committed document: it folds the PSI, which
     * still holds the previous text, so the regions would carry offsets the document no longer has.
     * The check belongs inside the read action, since an edit may land right after it otherwise.
     * The daemon restarted above rebuilds the regions once the commit happens.
     */
    private static @Nullable Runnable foldingUpdate(@NotNull Project project,
                                                    @NotNull Editor editor,
                                                    @NotNull Document document) {
        if (!PsiDocumentManager.getInstance(project).isCommitted(document)) {
            return null;
        }
        return CodeFoldingManager.getInstance(project).updateFoldRegionsAsync(editor, false);
    }

    private static List<Editor> editorsOf(@NotNull Project project, @NotNull Document document) {
        return ReadAction.nonBlocking(
                        () -> List.of(EditorFactory.getInstance().getEditors(document, project)))
                .executeSynchronously();
    }

    private static void applyAndCollapse(@NotNull Editor editor, @Nullable Runnable applyFolding) {
        if (editor.isDisposed()) {
            return;
        }
        if (applyFolding != null) {
            applyFolding.run();
        }

        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
                if (DataformFoldingPlaceholder.isDataformRegion(region.getGroup())) {
                    region.setExpanded(false);
                }
            }
        });
    }
}
