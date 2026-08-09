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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import io.github.rejeb.dataform.language.validation.SqlxValidationProblem;
import io.github.rejeb.dataform.language.validation.SqlxValidationService;
import io.github.rejeb.dataform.language.util.SqlxEditors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link ValidationProblemInlayManager}, rebuilding chips from the diagnostic service.
 */
public final class ValidationProblemInlayManagerImpl implements ValidationProblemInlayManager {

    private static final Key<List<Inlay<?>>> CHIPS =
            Key.create("dataform.compilation.error.chips");

    private final Project project;

    public ValidationProblemInlayManagerImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void refreshAll() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (supports(editor) && (editor.getProject() == null
                        || editor.getProject() == project)) {
                    refreshOnEdt(editor);
                }
            }
        });
    }

    @Override
    public void refresh(@NotNull Editor editor) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && !editor.isDisposed() && supports(editor)) {
                refreshOnEdt(editor);
            }
        });
    }

    private static boolean supports(@NotNull Editor editor) {
        return SqlxEditors.isHost(editor);
    }

    /**
     * Starts a refresh for one editor. Validation resolves references, which reads indexes and the
     * virtual file system, so it runs in a background read action; only the inlays themselves are
     * touched on the EDT. Existing chips are kept until the new ones are ready, so a refresh does
     * not make them blink.
     */
    private void refreshOnEdt(@NotNull Editor editor) {
        if (!DataformToolsSettings.getInstance().isShowInlineCompilationErrors()) {
            clear(editor);
            return;
        }
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            clear(editor);
            return;
        }

        ReadAction.nonBlocking(() -> SqlxValidationService.getInstance(project).validate(psiFile))
                .expireWith(project)
                .coalesceBy(this, editor)
                .finishOnUiThread(ModalityState.defaultModalityState(),
                        problems -> applyProblems(editor, problems))
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void applyProblems(@NotNull Editor editor,
                              @NotNull List<SqlxValidationProblem> problems) {
        if (project.isDisposed() || editor.isDisposed()) {
            return;
        }
        clear(editor);
        if (problems.isEmpty()) {
            return;
        }

        Document document = editor.getDocument();
        List<Inlay<?>> added = new ArrayList<>();
        for (SqlxValidationProblem problem : problems) {
            if (problem.range().getEndOffset() > document.getTextLength()) {
                continue;
            }
            int line = document.getLineNumber(problem.range().getStartOffset());
            List<String> lines = CompilationErrorChipText.wrap(problem.message());
            ValidationProblemInlayRenderer renderer = new ValidationProblemInlayRenderer(lines);
            int offset = document.getLineEndOffset(line);

            Inlay<?> inlay = lines.size() > 1
                    ? editor.getInlayModel()
                        .addBlockElement(offset, true, false, 0, renderer)
                    : editor.getInlayModel()
                        .addAfterLineEndElement(offset, true, renderer);
            if (inlay != null) {
                added.add(inlay);
            }
        }
        editor.putUserData(CHIPS, added);
    }

    private static void clear(@NotNull Editor editor) {
        List<Inlay<?>> existing = editor.getUserData(CHIPS);
        if (existing == null) {
            return;
        }
        for (Inlay<?> inlay : existing) {
            if (inlay.isValid()) {
                com.intellij.openapi.util.Disposer.dispose(inlay);
            }
        }
        editor.putUserData(CHIPS, null);
    }
}
