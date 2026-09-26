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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationProvider;
import io.github.rejeb.dataform.language.diagnostics.compile.CompilationProblemsService;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryDiagnosticsService;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Shows a banner listing the compilation errors and the BigQuery errors that could not be placed in
 * the file.
 */
public final class UnlocatedErrorsNotificationProvider
        implements EditorNotificationProvider, DumbAware {

    private static final int MAX_VISIBLE_LINES = 1;

    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project,
            @NotNull VirtualFile file
    ) {
        if (!DataformToolsSettings.getInstance().isShowInlineCompilationErrors()) {
            return null;
        }
        List<String> messages = messages(project, file);
        if (messages.isEmpty()) {
            return null;
        }
        String text = bannerText(messages);
        return fileEditor -> new WrappingEditorNotificationPanel(fileEditor, text, MAX_VISIBLE_LINES);
    }

    /**
     * The compilation errors of a file that could not be placed in it, then its BigQuery errors
     * that could not be. While indexes are built nothing is placed, so every compilation error is
     * listed.
     */
    public static @NotNull List<String> messages(@NotNull Project project, @NotNull VirtualFile file) {
        PsiFile psiFile = DumbService.isDumb(project) ? null : PsiManager.getInstance(project).findFile(file);
        List<String> messages = new ArrayList<>();
        if (psiFile == null) {
            for (CompilationDiagnostic diagnostic : CompilationDiagnosticService.getInstance(project).getDiagnostics(file)) {
                messages.add(diagnostic.message());
            }
            return messages;
        }
        messages.addAll(CompilationProblemsService.getInstance(project).diagnose(psiFile).unlocated());
        messages.addAll(BigQueryDiagnosticsService.getInstance(project).diagnose(psiFile).unlocated());
        return messages;
    }

    /**
     * Renders the compilation error messages as plain text. Line breaks are left to the banner,
     * which shows it on a single line and expands to full size on hover.
     */
    static String bannerText(@NotNull List<String> messages) {
        return BannerText.of("Dataform:", messages, "Compilation error");
    }
}
