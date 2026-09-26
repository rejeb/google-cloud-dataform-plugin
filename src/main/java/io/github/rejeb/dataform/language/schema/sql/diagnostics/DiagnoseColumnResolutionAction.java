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
package io.github.rejeb.dataform.language.schema.sql.diagnostics;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a report of how the plugin resolves the Dataform column under the caret, step by step, so
 * that a resolution failure on a project that cannot be shared can still be located.
 */
public class DiagnoseColumnResolutionAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        event.getPresentation().setEnabledAndVisible(event.getData(CommonDataKeys.EDITOR) != null
                && file != null && file.getName().endsWith(".sqlx"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (project == null || editor == null || file == null) return;
        int offset = editor.getCaretModel().getOffset();

        new Task.Backgroundable(project, "Diagnosing Dataform column resolution…", true) {
            private String report;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                report = ReadAction.computeBlocking(() -> ColumnResolutionReport.build(file, offset));
            }

            @Override
            public void onSuccess() {
                if (report == null || project.isDisposed()) return;
                LightVirtualFile reportFile = new LightVirtualFile(
                        "dataform-column-resolution.txt", PlainTextFileType.INSTANCE, report);
                FileEditorManager.getInstance(project).openFile(reportFile, true);
            }
        }.queue();
    }
}
