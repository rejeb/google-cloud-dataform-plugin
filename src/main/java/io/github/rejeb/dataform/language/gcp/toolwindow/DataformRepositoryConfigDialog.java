/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DataformRepositoryConfigDialog extends DialogWrapper {

    private final Project project;
    private final JBTextField projectIdField    = new JBTextField(30);
    private final JBTextField repositoryIdField = new JBTextField(30);
    private final JBTextField locationField     = new JBTextField(30);
    private final JButton testButton            = new JButton("Test Connection");
    private final JLabel  testResultLabel       = new JBLabel();

    public DataformRepositoryConfigDialog(@NotNull Project project, @Nullable DataformRepositoryConfig existing) {
        super(project, true);
        this.project = project;
        setTitle(existing == null ? "Add Dataform Repository" : "Edit Dataform Repository");
        setOKButtonText("Save");

        if (existing != null) {
            projectIdField.setText(existing.projectId());
            repositoryIdField.setText(existing.repositoryId());
            locationField.setText(existing.location());
        }

        testButton.addActionListener(e -> testConnection());
        init();
    }

    /** @return the config built from the dialog fields, or {@code null} if dialog was cancelled */
    @Nullable
    public DataformRepositoryConfig getResultConfig() {
        return buildConfig().orNull();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testRow.add(testButton);
        testRow.add(Box.createHorizontalStrut(8));
        testRow.add(testResultLabel);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("GCP Project ID:"), projectIdField, 1, false)
                .addLabeledComponent(new JBLabel("Repository Name:"), repositoryIdField, 1, false)
                .addLabeledComponent(new JBLabel("Location:"), locationField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .addComponent(testRow)
                .getPanel();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (projectIdField.getText().isBlank())
            return new ValidationInfo("GCP Project ID is required.", projectIdField);
        if (repositoryIdField.getText().isBlank())
            return new ValidationInfo("Repository Name is required.", repositoryIdField);
        if (locationField.getText().isBlank())
            return new ValidationInfo("Location is required.", locationField);
        return null;
    }


    private void testConnection() {
        ValidationInfo validation = doValidate();
        if (validation != null) {
            testResultLabel.setForeground(Color.RED);
            testResultLabel.setText(validation.message);
            return;
        }

        testButton.setEnabled(false);
        testResultLabel.setForeground(Color.GRAY);
        testResultLabel.setText("Testing…");

        DataformRepositoryConfig config = buildConfig();

        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "Testing Dataform connection…") {
                    private boolean success;
                    private String errorMessage;

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            DataformGcpService.getInstance(project).testConnection(config);
                            success = true;
                        } catch (GcpApiException e) {
                            success = false;
                            errorMessage = e.getMessage();
                        }
                    }

                    @Override
                    public void onSuccess() {
                        testButton.setEnabled(true);
                        if (success) {
                            testResultLabel.setForeground(new Color(0, 128, 0));
                            testResultLabel.setText("✓ Connection successful");
                        } else {
                            testResultLabel.setForeground(Color.RED);
                            testResultLabel.setText("✗ " + errorMessage);
                        }
                    }
                }
        );
    }

    @NotNull
    private DataformRepositoryConfig buildConfig() {
        return new DataformRepositoryConfig(
                projectIdField.getText().trim(),
                repositoryIdField.getText().trim(),
                locationField.getText().trim()
        );
    }
}
