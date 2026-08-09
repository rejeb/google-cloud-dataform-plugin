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
package io.github.rejeb.dataform.language.settings;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.diagnostics.ValidationProblemInlayManager;
import io.github.rejeb.dataform.language.folding.DataformMultilineFoldManager;
import io.github.rejeb.dataform.language.util.DataformProjects;

import javax.swing.*;

public class DataformToolsConfigurable implements Configurable {

    private DataformToolsSettingsPanel panel;

    @Override
    public String getDisplayName() {
        return "Dataform Tools";
    }

    @Override
    public JComponent createComponent() {
        panel = new DataformToolsSettingsPanel();
        return panel.getPanel();
    }

    @Override
    public boolean isModified() {
        DataformToolsSettings service = DataformToolsSettings.getInstance();
        return !panel.getCoreInstallPath().equals(service.getCoreInstallPath())
                || !panel.getSqlfluffExecutablePath().equals(service.getSqlfluffExecutablePath())
                || !panel.getSqlfluffConfigPath().equals(service.getSqlfluffConfigPath())
                || !panel.getSqlfluffExtraArgs().equals(service.getSqlfluffExtraArgs())
                || panel.isFoldTemplateExpressions() != service.isFoldTemplateExpressions()
                || panel.isShowInlineCompilationErrors() != service.isShowInlineCompilationErrors()
                || panel.isCompileOnSave() != service.isCompileOnSave();
    }

    @Override
    public void apply() {
        DataformToolsSettings settings = DataformToolsSettings.getInstance();
        boolean foldChanged = panel.isFoldTemplateExpressions() != settings.isFoldTemplateExpressions();
        settings.update(
                panel.getCoreInstallPath(),
                panel.getSqlfluffExecutablePath(),
                panel.getSqlfluffConfigPath(),
                panel.getSqlfluffExtraArgs()
        );
        settings.setFoldTemplateExpressions(panel.isFoldTemplateExpressions());
        boolean inlineErrorsChanged =
                panel.isShowInlineCompilationErrors() != settings.isShowInlineCompilationErrors();
        settings.setShowInlineCompilationErrors(panel.isShowInlineCompilationErrors());
        settings.setCompileOnSave(panel.isCompileOnSave());
        if (foldChanged) {
            refreshFoldingInOpenEditors();
        }
        if (inlineErrorsChanged) {
            refreshHighlightingInOpenProjects();
        }
    }

    private static void refreshHighlightingInOpenProjects() {
        DataformProjects.forEachOpen(project -> {
            DataformEditorRefresher.refresh(project);
            ValidationProblemInlayManager.getInstance(project).refreshAll();
        });
    }

    private static void refreshFoldingInOpenEditors() {
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            Project project = editor.getProject();
            if (project == null || project.isDisposed()) {
                continue;
            }
            DataformMultilineFoldManager.clear(editor);
            CodeFoldingManager.getInstance(project).scheduleAsyncFoldingUpdate(editor);
        }
    }

    @Override
    public void reset() {
        DataformToolsSettings service = DataformToolsSettings.getInstance();
        panel.setCoreInstallPath(service.getCoreInstallPath());
        panel.setSqlfluffExecutablePath(service.getSqlfluffExecutablePath());
        panel.setSqlfluffConfigPath(service.getSqlfluffConfigPath());
        panel.setSqlfluffExtraArgs(service.getSqlfluffExtraArgs());
        panel.setFoldTemplateExpressions(service.isFoldTemplateExpressions());
        panel.setShowInlineCompilationErrors(service.isShowInlineCompilationErrors());
        panel.setCompileOnSave(service.isCompileOnSave());
    }
}
