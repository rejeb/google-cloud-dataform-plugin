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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
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
import java.util.UUID;

public class RepositoryEditPanel extends JPanel {

    private final Project project;
    private final JBTextField labelField = new JBTextField(30);
    private final JBTextField projectIdField = new JBTextField(30);
    private final JBTextField repositoryIdField = new JBTextField(30);
    private final JBTextField locationField = new JBTextField(30);

    private final JButton testButton = new JButton("Test Connection");
    private final JButton createGcpButton = new JButton("Create in GCP");
    private final JLabel statusLabel = new JBLabel();

    /**
     * Appelé sur l'EDT après une création GCP réussie.
     * Le dialog parent doit flusher + persister la config.
     */
    private Runnable onCreateSuccess = () -> {
    };

    public RepositoryEditPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;

        testButton.addActionListener(e -> runAction(ActionKind.TEST));
        createGcpButton.addActionListener(e -> runAction(ActionKind.CREATE_GCP));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actionRow.add(testButton);
        actionRow.add(createGcpButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(statusLabel);

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Label:"), labelField, 1, false)
                .addLabeledComponent(new JBLabel("GCP Project ID:"), projectIdField, 1, false)
                .addLabeledComponent(new JBLabel("Repository ID:"), repositoryIdField, 1, false)
                .addLabeledComponent(new JBLabel("Location:"), locationField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .addComponent(actionRow)
                .getPanel();
        form.setBorder(JBUI.Borders.empty(8, 12));

        add(form, BorderLayout.CENTER);
        setEnabled(false);
    }

    /**
     * Enregistre le callback appelé après une création GCP réussie.
     */
    public void setOnCreateSuccess(@NotNull Runnable callback) {
        this.onCreateSuccess = callback;
    }

    public void load(@NotNull DataformRepositoryConfig config) {
        labelField.setText(config.label() );
        projectIdField.setText(config.projectId());
        repositoryIdField.setText(config.repositoryId());
        locationField.setText(config.location());
        statusLabel.setText("");
        setEnabled(true);
    }

    public void clear() {
        labelField.setText("");
        projectIdField.setText("");
        repositoryIdField.setText("");
        locationField.setText("");
        statusLabel.setText("");
        setEnabled(false);
    }

    public void focusLabel() {
        labelField.requestFocusInWindow();
        labelField.selectAll();
    }

    @Nullable
    public ValidationInfo validationInfo() {
        if (labelField.getText().isBlank())
            return new ValidationInfo("Label is required.", labelField);
        if (projectIdField.getText().isBlank())
            return new ValidationInfo("GCP Project ID is required.", projectIdField);
        if (repositoryIdField.getText().isBlank())
            return new ValidationInfo("Repository ID is required.", repositoryIdField);
        if (locationField.getText().isBlank())
            return new ValidationInfo("Location is required.", locationField);
        return null;
    }

    @NotNull
    public DataformRepositoryConfig buildConfig(@NotNull String labelFallback) {
        String label = labelField.getText().trim();
        return new DataformRepositoryConfig(
                UUID.randomUUID().toString(),
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
        createGcpButton.setEnabled(enabled);
    }

    // -------------------------------------------------------------------------

    private enum ActionKind {TEST, CREATE_GCP}

    private void runAction(@NotNull ActionKind kind) {
        ValidationInfo validation = validationInfo();
        if (validation != null) {
            setStatus(false, validation.message);
            return;
        }

        DataformRepositoryConfig config = buildConfig("temp");
        setButtonsEnabled(false);

        String taskTitle = switch (kind) {
            case TEST -> "Testing Dataform connection…";
            case CREATE_GCP -> "Creating Dataform repository in GCP…";
        };

        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, taskTitle) {
                    private boolean success;
                    private String message;

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            switch (kind) {
                                case TEST -> DataformGcpService.getInstance(project)
                                        .testConnection(config);
                                case CREATE_GCP -> DataformGcpService.getInstance(project)
                                        .createGcpRepository(config);
                            }
                            success = true;
                            message = switch (kind) {
                                case TEST -> "✓ Connection successful";
                                case CREATE_GCP -> "✓ Repository created in GCP";
                            };
                        } catch (GcpApiException e) {
                            success = false;
                            message = "✗ " + e.getMessage();
                        }
                    }

                    @Override
                    public void onSuccess() {
                        setButtonsEnabled(true);
                        setStatus(success, message);
                        if (success && kind == ActionKind.CREATE_GCP) {
                            onCreateSuccess.run();
                        }
                    }
                }
        );
    }

    private void setButtonsEnabled(boolean enabled) {
        testButton.setEnabled(enabled);
        createGcpButton.setEnabled(enabled);
    }

    private void setStatus(boolean success, @NotNull String text) {
        statusLabel.setForeground(success ? JBColor.GREEN : JBColor.RED);
        statusLabel.setText(text);
    }
}
