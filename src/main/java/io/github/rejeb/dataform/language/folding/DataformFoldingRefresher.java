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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes newly computed expression values visible in the editors of a file.
 *
 * <p>The platform only honours {@code isCollapsedByDefault} on the first folding pass of an editor,
 * so regions created by a later pass are collapsed explicitly here. Each range is collapsed once,
 * which keeps a region the user expanded manually expanded.</p>
 */
public final class DataformFoldingRefresher {

    private static final Key<Set<TextRange>> COLLAPSED_RANGES = Key.create("dataform.folding.collapsedRanges");
    private static final String RESTART_REASON = "Dataform expression values updated";

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

        restartDaemon(project, file);
        MultilineSnapshot snapshot = ReadAction.nonBlocking(
                        () -> new MultilineSnapshot(
                                DataformMultilineValues.of(project, file, document),
                                document.getModificationStamp()))
                .executeSynchronously();

        for (Editor editor : editorsOf(project, document)) {
            Runnable applyFolding = ReadAction.nonBlocking(
                            () -> CodeFoldingManager.getInstance(project).updateFoldRegionsAsync(editor, false))
                    .executeSynchronously();
            ApplicationManager.getApplication().invokeLater(() -> {
                applyAndCollapse(editor, applyFolding);
                if (!editor.isDisposed()
                        && document.getModificationStamp() == snapshot.documentStamp()) {
                    DataformMultilineFoldManager.apply(editor, snapshot.values());
                }
            }, project.getDisposed());
        }
    }

    /**
     * Multiline values together with the document stamp they were computed against. The values are
     * only applied when the document is still at that stamp, since custom fold regions are placed
     * by line numbers and would be painted at stale positions otherwise.
     */
    private record MultilineSnapshot(@NotNull List<DataformMultilineFoldManager.MultilineValue> values,
                                     long documentStamp) {
    }

    private static List<Editor> editorsOf(@NotNull Project project, @NotNull Document document) {
        return ReadAction.nonBlocking(
                        () -> List.of(EditorFactory.getInstance().getEditors(document, project)))
                .executeSynchronously();
    }

    private static void restartDaemon(@NotNull Project project, @NotNull VirtualFile file) {
        PsiFile psiFile = ReadAction.nonBlocking(
                () -> PsiManager.getInstance(project).findFile(file)).executeSynchronously();
        if (psiFile != null) {
            ApplicationManager.getApplication().invokeLater(
                    () -> DaemonCodeAnalyzer.getInstance(project).restart(psiFile, RESTART_REASON),
                    project.getDisposed());
        }
    }

    private static void applyAndCollapse(@NotNull Editor editor, @Nullable Runnable applyFolding) {
        if (editor.isDisposed()) {
            return;
        }
        if (applyFolding != null) {
            applyFolding.run();
        }

        Set<TextRange> alreadyCollapsed = collapsedRanges(editor);
        Set<TextRange> current = new HashSet<>();
        editor.getFoldingModel().runBatchFoldingOperation(() -> {
            for (FoldRegion region : editor.getFoldingModel().getAllFoldRegions()) {
                if (!DataformFoldingPlaceholder.isDataformRegion(region.getGroup())) {
                    continue;
                }
                TextRange range = TextRange.create(region.getStartOffset(), region.getEndOffset());
                current.add(range);
                if (alreadyCollapsed.add(range)) {
                    region.setExpanded(false);
                }
            }
        });
        alreadyCollapsed.retainAll(current);
    }

    @NotNull
    private static Set<TextRange> collapsedRanges(@NotNull Editor editor) {
        Set<TextRange> ranges = editor.getUserData(COLLAPSED_RANGES);
        if (ranges == null) {
            ranges = new HashSet<>();
            editor.putUserData(COLLAPSED_RANGES, ranges);
        }
        return ranges;
    }
}
