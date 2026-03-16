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
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RepositoryEditPanel extends JPanel {

    private final Project project;
    private final JBTextField labelField        = new JBTextField(30);
    private final JBTextField projectIdField    = new JBTextField(30);
    private final JBTextField repositoryIdField = new JBTextField(30);
    private final JBTextField locationField     = new JBTextField(30);
    private final JButton     testButton        = new JButton("Test Connection");
    private final JLabel      testResultLabel   = new JBLabel();

    public RepositoryEditPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;

        testButton.addActionListener(e -> testConnection());

        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testRow.add(testButton);
        testRow.add(Box.createHorizontalStrut(8));
        testRow.add(testResultLabel);

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Label:"), labelField, 1, false)
                .addLabeledComponent(new JBLabel("GCP Project ID:"), projectIdField, 1, false)
                .addLabeledComponent(new JBLabel("Repository Name:"), repositoryIdField, 1, false)
                .addLabeledComponent(new JBLabel("Location:"), locationField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .addComponent(testRow)
                .getPanel();
        form.setBorder(JBUI.Borders.empty(8, 12));

        add(form, BorderLayout.CENTER);
        setEnabled(false);
    }

    /**
     * Loads the given config into the form fields.
     */
    public void load(@NotNull DataformRepositoryConfig config) {
        labelField.setText(config.label() != null ? config.label() : "");
        projectIdField.setText(config.projectId());
        repositoryIdField.setText(config.repositoryId());
        locationField.setText(config.location());
        testResultLabel.setText("");
        setEnabled(true);
    }

    /** Clears all fields and disables the form. */
    public void clear() {
        labelField.setText("");
        projectIdField.setText("");
        repositoryIdField.setText("");
        locationField.setText("");
        testResultLabel.setText("");
        setEnabled(false);
    }

    /**
     * @return a {@link ValidationInfo} if any required field is invalid, otherwise {@code null}
     */
    @Nullable
    public ValidationInfo validationInfo() {
        if (projectIdField.getText().isBlank())
            return new ValidationInfo("GCP Project ID is required.", projectIdField);
        if (repositoryIdField.getText().isBlank())
            return new ValidationInfo("Repository Name is required.", repositoryIdField);
        if (locationField.getText().isBlank())
            return new ValidationInfo("Location is required.", locationField);
        return null;
    }

    /**
     * Builds a config from the current field values.
     * If label is blank, the provided fallback is used.
     */
    @NotNull
    public DataformRepositoryConfig buildConfig(@NotNull String labelFallback) {
        String label = labelField.getText().trim();
        return new DataformRepositoryConfig(
                label.isEmpty() ? labelFallback : label,
                projectIdField.getText().trim(),
                repositoryIdField.getText().trim(),
                locationField.getText().trim()
        );
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        labelField.setEnabled(enabled);
        projectIdField.setEnabled(enabled);
        repositoryIdField.setEnabled(enabled);
        locationField.setEnabled(enabled);
        testButton.setEnabled(enabled);
    }

    public void focusLabel() {
        labelField.requestFocusInWindow();
        labelField.selectAll();
    }

    private void testConnection() {
        ValidationInfo validation = validationInfo();
        if (validation != null) {
            testResultLabel.setForeground(Color.RED);
            testResultLabel.setText(validation.message);
            return;
        }

        testButton.setEnabled(false);
        testResultLabel.setForeground(Color.GRAY);
        testResultLabel.setText("Testing…");

        DataformRepositoryConfig config = buildConfig("test");

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
}
