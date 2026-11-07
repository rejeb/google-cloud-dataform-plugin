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
package io.github.rejeb.dataform.language.projectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.clone.DataformCloneDialogs;
import io.github.rejeb.dataform.language.setup.DataformCoreVersionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class DataformModuleWizardStep extends ModuleWizardStep {

    private final DataformModuleBuilder builder;
    private final JBTextField gcpProjectIdField = new JBTextField();
    private final JBTextField defaultSchemaField = new JBTextField("dataform");
    private final JBTextField defaultLocationField = new JBTextField("US");
    private final JBTextField dataformCoreVersionField =
            new JBTextField(DataformCoreVersionProvider.getInstance().knownLatestVersion());
    private final @Nullable ActionLink existingRepositoryLink;
    private final JPanel mainPanel;

    /**
     * @param builder the builder of the new project
     * @param project the project the wizard was opened from, {@code null} when it creates a new one
     */
    public DataformModuleWizardStep(DataformModuleBuilder builder, @Nullable Project project) {
        this(builder, project, DataformCloneDialogs::open);
    }

    DataformModuleWizardStep(DataformModuleBuilder builder, @Nullable Project project,
                             @NotNull Consumer<@Nullable Project> existingRepositoryDialog) {
        this.builder = builder;
        existingRepositoryLink = DataformCloneDialogs.isAvailable()
                ? new ActionLink("Get it from Google Cloud",
                (ActionListener) e -> leaveForExistingRepository(project, existingRepositoryDialog))
                : null;

        FormBuilder form = FormBuilder.createFormBuilder()
                .addLabeledComponent("GCP Project ID:", gcpProjectIdField)
                .addLabeledComponent("Default schema:", defaultSchemaField)
                .addLabeledComponent("Default location:", defaultLocationField)
                .addLabeledComponent("Dataform core version",dataformCoreVersionField);
        if (existingRepositoryLink != null) {
            form.addComponent(existingRepositoryRow(existingRepositoryLink), JBUI.scale(12));
        }
        mainPanel = form.addComponentFillVertically(new JPanel(), 0).getPanel();
        proposeCoreVersion();
    }

    private static @NotNull JComponent existingRepositoryRow(@NotNull ActionLink link) {
        JBLabel question = new JBLabel("Already have a Dataform repository? ");
        question.setForeground(UIUtil.getContextHelpForeground());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(question);
        row.add(link);
        return row;
    }

    private void leaveForExistingRepository(@Nullable Project project, @NotNull Consumer<@Nullable Project> dialog) {
        DialogWrapper wizard = DialogWrapper.findInstance(mainPanel);
        if (wizard != null) {
            wizard.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
        ApplicationManager.getApplication().invokeLater(() -> dialog.accept(project), ModalityState.nonModal());
    }

    @Nullable ActionLink existingRepositoryLink() {
        return existingRepositoryLink;
    }

    private void proposeCoreVersion() {
        String proposed = dataformCoreVersionField.getText();
        DataformCoreVersionProvider versions = DataformCoreVersionProvider.getInstance();
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                versions.installedCliVersion().or(versions::fetchLatestVersion).ifPresent(latest ->
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (proposed.equals(dataformCoreVersionField.getText())) {
                                dataformCoreVersionField.setText(latest);
                            }
                        }, ModalityState.any())));
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void updateDataModel() {
        DataformProjectSettings settings = builder.getSettings();
        settings.setGcpProjectId(gcpProjectIdField.getText().trim());
        settings.setDefaultSchema(defaultSchemaField.getText().trim());
        settings.setDefaultLocation(defaultLocationField.getText().trim());
        settings.setDataformCoreVersion(dataformCoreVersionField.getText().trim());
    }

    @Override
    public boolean validate() throws ConfigurationException {
        require(gcpProjectIdField, "GCP Project ID");
        require(defaultSchemaField, "Default Schema");
        require(defaultLocationField, "Default Location");
        require(dataformCoreVersionField, "Dataform core version");
        return true;
    }

    private static void require(@NotNull JTextField field, @NotNull String label) throws ConfigurationException {
        if (field.getText().trim().isEmpty()) {
            throw new ConfigurationException(label + " is required");
        }
    }
}
