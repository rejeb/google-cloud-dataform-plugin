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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationProvider;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shows a banner listing compilation errors that could not be mapped to a source line.
 */
public final class UnlocatedErrorsNotificationProvider
        implements EditorNotificationProvider, DumbAware {

    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project,
            @NotNull VirtualFile file
    ) {
        if (!DataformToolsSettings.getInstance().isShowInlineCompilationErrors()) {
            return null;
        }
        List<CompilationDiagnostic> errors =
                CompilationDiagnosticService.getInstance(project).getDiagnostics(file);
        if (errors.isEmpty()) {
            return null;
        }
        String tooltip = errors.stream()
                .map(CompilationDiagnostic::message)
                .collect(Collectors.joining("\n"));
        String text = bannerText(errors.stream().map(CompilationDiagnostic::message).toList());
        return fileEditor -> {
            WrappingEditorNotificationPanel panel =
                    new WrappingEditorNotificationPanel(fileEditor, text);
            panel.setToolTipText(tooltip);
            return panel;
        };
    }

    /**
     * Renders the compilation error messages as plain text. Line breaks are left to the banner,
     * which wraps to the editor width.
     */
    static String bannerText(@NotNull List<String> messages) {
        return BannerText.of("Dataform:", messages, "Compilation error");
    }
}
