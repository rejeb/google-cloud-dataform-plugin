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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.folding.DataformMultilineFoldManager;
import io.github.rejeb.dataform.language.gcp.pricing.BigQueryPricingService;
import io.github.rejeb.dataform.language.gcp.pricing.PriceListStatus;
import io.github.rejeb.dataform.language.util.DataformProjects;

import javax.swing.*;
import java.time.ZoneId;

public class DataformToolsConfigurable implements Configurable {

    private DataformToolsSettingsPanel panel;

    @Override
    public String getDisplayName() {
        return "Dataform Tools";
    }

    @Override
    public JComponent createComponent() {
        panel = new DataformToolsSettingsPanel();
        BigQueryCostSettingsPanel costPanel = panel.getCostSettingsPanel();
        costPanel.setPriceListStatus(describe(BigQueryPricingService.getInstance().status()));
        costPanel.onRefresh(() -> {
            costPanel.setPriceListStatus("Price list: downloading…");
            BigQueryPricingService.getInstance().refresh().thenAccept(status ->
                    ApplicationManager.getApplication().invokeLater(
                            () -> costPanel.setPriceListStatus(describe(status)), ModalityState.any()));
        });
        return panel.getPanel();
    }

    private static String describe(PriceListStatus status) {
        return status.describe(ZoneId.systemDefault());
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
                || panel.getCostSettingsPanel().isModified(service);
    }

    @Override
    public void apply() throws ConfigurationException {
        DataformToolsSettings settings = DataformToolsSettings.getInstance();
        panel.getCostSettingsPanel().apply(settings);
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
        if (foldChanged) {
            refreshFoldingInOpenEditors();
        }
        if (inlineErrorsChanged) {
            refreshHighlightingInOpenProjects();
        }
    }

    private static void refreshHighlightingInOpenProjects() {
        DataformProjects.forEachOpen(project -> {
            DataformEditorRefresher.refreshWithInlays(project);
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
        panel.getCostSettingsPanel().reset(service);
    }
}
