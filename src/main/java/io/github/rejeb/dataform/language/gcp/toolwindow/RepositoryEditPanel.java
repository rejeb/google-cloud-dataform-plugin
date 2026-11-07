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
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.common.DataformLocations;
import io.github.rejeb.dataform.language.gcp.common.GcpApiErrors;
import io.github.rejeb.dataform.language.gcp.common.GcpApiException;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.ui.FilteringComboBox;
import io.github.rejeb.dataform.language.ui.StatusLabel;
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
    private final FilteringComboBox locationField = new FilteringComboBox(DataformLocations.all());
    private final ServiceAccountComboBox serviceAccountField;
    private @Nullable String loadedConfigId;

    private final JButton testButton = new JButton("Test Connection");
    private final ActionLink createLink = new ActionLink("Create in Google Cloud");
    private final StatusLabel statusLabel = new StatusLabel();

    /**
     * Called on the EDT once the repository was created in GCP; the owning dialog flushes and
     * persists the config.
     */
    private Runnable onCreateSuccess = () -> {
    };

    public RepositoryEditPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.serviceAccountField = new ServiceAccountComboBox(project, () -> projectIdField.getText().trim());

        testButton.addActionListener(e -> runAction(false));
        createLink.addActionListener(e -> runAction(true));
        createLink.setVisible(false);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionRow.add(testButton);
        actionRow.add(Box.createHorizontalStrut(JBUI.scale(8)));
        actionRow.add(statusLabel);

        JPanel form = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator("Repository"))
                .addLabeledComponent("Label:", labelField)
                .addLabeledComponent("GCP Project ID:", projectIdField)
                .addLabeledComponent("Repository ID:", repositoryIdField)
                .addLabeledComponent("Location:", locationField)
                .addComponentToRightColumn(comment("The region of the Dataform repository, such as europe-west1."))
                .addComponent(new TitledSeparator("Authentication"), 12)
                .addLabeledComponent("Service Account:", serviceAccountField)
                .addComponentToRightColumn(comment("Optional. Workflow runs started from the IDE execute as this "
                        + "account. Also set on the repository when it is created from here."))
                .addComponent(actionRow, 12)
                .addComponent(createLink)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        form.setBorder(JBUI.Borders.empty(8, 12));

        add(form, BorderLayout.CENTER);
        setEnabled(false);
    }

    private static @NotNull JComponent comment(@NotNull String text) {
        return ComponentPanelBuilder.createCommentComponent(text, true);
    }

    /**
     * Registers the callback run once the repository was created in GCP.
     */
    public void setOnCreateSuccess(@NotNull Runnable callback) {
        this.onCreateSuccess = callback;
    }

    public void load(@NotNull DataformRepositoryConfig config) {
        fill(config.repositoryConfigId(), config.label(), config.projectId(), config.repositoryId(),
                config.location(), config.serviceAccount());
        setEnabled(true);
    }

    public void clear() {
        fill(null, "", "", "", "", "");
        setEnabled(false);
    }

    private void fill(@Nullable String configId, String label, String projectId, String repositoryId,
                      String location, String serviceAccount) {
        loadedConfigId = configId;
        labelField.setText(label);
        projectIdField.setText(projectId);
        repositoryIdField.setText(repositoryId);
        locationField.setText(location);
        serviceAccountField.setText(serviceAccount);
        statusLabel.clear();
        createLink.setVisible(false);
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
        if (locationText().isBlank())
            return new ValidationInfo("Location is required.", locationField);
        return null;
    }

    @NotNull
    public DataformRepositoryConfig buildConfig(@NotNull String labelFallback) {
        String label = labelField.getText().trim();
        return new DataformRepositoryConfig(
                loadedConfigId != null ? loadedConfigId : UUID.randomUUID().toString(),
                label.isEmpty() ? labelFallback : label,
                projectIdField.getText().trim(),
                repositoryIdField.getText().trim(),
                locationText(),
                serviceAccountField.getText().trim()
        );
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        labelField.setEnabled(enabled);
        projectIdField.setEnabled(enabled);
        repositoryIdField.setEnabled(enabled);
        locationField.setEnabled(enabled);
        serviceAccountField.setEnabled(enabled);
        setButtonsEnabled(enabled);
    }

    @NotNull
    StatusLabel status() {
        return statusLabel;
    }

    @NotNull
    ActionLink createLink() {
        return createLink;
    }

    /**
     * Shows the outcome of a connection test or of a creation; a test that found no repository
     * offers to create it.
     */
    void showResult(boolean creation, @Nullable GcpApiException failure) {
        if (failure == null) {
            statusLabel.ok(creation ? "Repository created in Google Cloud" : "Connection successful");
            createLink.setVisible(false);
            return;
        }
        statusLabel.error(failure.getMessage() != null ? failure.getMessage() : "Request failed");
        createLink.setVisible(!creation && GcpApiErrors.isNotFound(failure));
    }

    private @NotNull String locationText() {
        return locationField.getText();
    }

    private void runAction(boolean creation) {
        ValidationInfo validation = validationInfo();
        if (validation != null) {
            statusLabel.error(validation.message);
            return;
        }

        DataformRepositoryConfig config = buildConfig("temp");
        setButtonsEnabled(false);
        String taskTitle = creation ? "Creating Dataform repository in GCP…" : "Testing Dataform connection…";
        statusLabel.progress(creation ? "Creating repository…" : "Testing connection…");

        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, taskTitle) {
                    private @Nullable GcpApiException failure;

                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        try {
                            DataformGcpService service = DataformGcpService.getInstance(project);
                            if (creation) {
                                service.createGcpRepository(config);
                            } else {
                                service.testConnection(config);
                            }
                        } catch (GcpApiException e) {
                            failure = e;
                        }
                    }

                    @Override
                    public void onSuccess() {
                        showResult(creation, failure);
                        if (failure == null && creation) {
                            onCreateSuccess.run();
                        }
                    }

                    @Override
                    public void onThrowable(@NotNull Throwable error) {
                        statusLabel.error(error.getMessage() != null ? error.getMessage() : "Request failed");
                    }

                    @Override
                    public void onFinished() {
                        setButtonsEnabled(true);
                    }
                }
        );
    }

    private void setButtonsEnabled(boolean enabled) {
        testButton.setEnabled(enabled);
        createLink.setEnabled(enabled);
    }
}
