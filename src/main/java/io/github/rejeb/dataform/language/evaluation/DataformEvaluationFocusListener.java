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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.rejeb.dataform.language.util.DataformProjectLayout;
import org.jetbrains.annotations.NotNull;

/**
 * Asks for the template values of a file when it is opened, when its tab is selected, and when
 * the IDE comes back to the front; lets them go when the file is closed.
 *
 * <p>These are the only moments a pass is asked for. Editing the file asks for nothing, so the
 * values a person expanded to reach their code stay out of the way while they type; what they
 * changed is evaluated the next time the file comes into focus.</p>
 */
public final class DataformEvaluationFocusListener
        implements FileEditorManagerListener, ApplicationActivationListener {

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        request(source.getProject(), file);
    }

    /** Values are held for open files only; the last editor of a file closing lets them go. */
    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (source.isFileOpen(file) || source.getProject().isDisposed()) return;
        DataformExpressionEvaluationService.getInstance(source.getProject()).invalidate(file);
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file != null) request(event.getManager().getProject(), file);
    }

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            requestForSelectedFiles(project);
        }
    }

    /**
     * Requests a pass for the files shown in the selected tabs of a project, from the event thread
     * or from a startup activity, which reaches editors restored before any listener saw them open.
     */
    public static void requestForSelectedFiles(@NotNull Project project) {
        if (project.isDisposed()) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            for (VirtualFile file : FileEditorManager.getInstance(project).getSelectedFiles()) {
                request(project, file);
            }
        }, project.getDisposed());
    }

    /**
     * Whether the file is one whose template expressions are evaluated: a SQLX file or an action
     * or include written in JavaScript, inside a Dataform project. Every other file of every other
     * project also comes through the listeners, and must cost nothing.
     */
    private static boolean hasTemplateExpressions(@NotNull VirtualFile file) {
        String extension = file.getExtension();
        if (!"sqlx".equalsIgnoreCase(extension) && !"js".equalsIgnoreCase(extension)) return false;
        return DataformProjectLayout.isDataformSource(file);
    }

    /**
     * Requests a pass for the file, which the service debounces and runs off this thread. Every
     * caller is on the event thread, where the PSI file may be looked up directly.
     */
    static void request(@NotNull Project project, @NotNull VirtualFile file) {
        if (project.isDisposed() || !file.isValid() || !hasTemplateExpressions(file)) return;
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
            DataformExpressionEvaluationService.getInstance(project).requestEvaluation(psiFile);
        }
    }
}
