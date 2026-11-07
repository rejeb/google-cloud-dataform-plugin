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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ui.FormBuilder;
import io.github.rejeb.dataform.language.ui.StatusLabel;
import io.github.rejeb.dataform.language.util.InstallResult;
import io.github.rejeb.dataform.language.util.NodeJsNpmUtils;
import io.github.rejeb.dataform.language.setup.DataformInstaller;
import io.github.rejeb.dataform.language.setup.NodeInterpreterManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Optional;

public final class DataformToolsSettingsPanel {

    private static final String INSTALL_TEXT = "Install Dataform CLI & Core";

    private final TextFieldWithBrowseButton coreInstallField    = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton sqlfluffExecutableField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton sqlfluffConfigField = new TextFieldWithBrowseButton();
    private final ExpandableTextField sqlfluffArgsField = new ExpandableTextField();
    private final JBCheckBox foldTemplateExpressionsBox =
            new JBCheckBox("Fold Dataform expressions to their evaluated value");
    private final JBCheckBox showInlineCompilationErrorsBox =
            new JBCheckBox("Show Dataform compilation and BigQuery errors inline in the editor");
    private final JButton    installButton    = new JButton(INSTALL_TEXT);
    private final StatusLabel installStatus   = new StatusLabel();
    private final BigQueryCostSettingsPanel costSettingsPanel = new BigQueryCostSettingsPanel();
    private final JPanel     panel;

    public DataformToolsSettingsPanel() {

       coreInstallField.addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("Select Dataform Core Install Directory")
                        .withDescription("Choose the directory where Dataform Core is installed")
        );

        installButton.addActionListener(e -> onInstall());

        sqlfluffExecutableField.addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.singleFile()
                        .withTitle("Select SQLFluff Executable")
                        .withDescription("Choose the path to the sqlfluff executable")
        );

        sqlfluffConfigField.addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.singleFile()
                        .withTitle("Select SQLFluff Config")
                        .withDescription("Choose a .sqlfluff config file (optional)")
        );

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.add(installButton);

        panel = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator("Dataform CLI"))
                .addLabeledComponent("Dataform Core directory:", coreInstallField)
                .addComponentToRightColumn(buttonRow)
                .addComponentToRightColumn(installStatus)
                .addComponent(new TitledSeparator("SQLFluff formatter"), 12)
                .addLabeledComponent("Executable:", sqlfluffExecutableField)
                .addComponentToRightColumn(comment("Formats the SQL of SQLX files with sqlfluff fix --dialect bigquery."))
                .addLabeledComponent("Config file:", sqlfluffConfigField)
                .addComponentToRightColumn(comment("Optional. Passed as --config."))
                .addLabeledComponent("Extra arguments:", sqlfluffArgsField)
                .addComponentToRightColumn(comment("Added after the arguments above, separated by spaces."))
                .addComponent(new TitledSeparator("Editor"), 12)
                .addComponent(foldTemplateExpressionsBox)
                .addComponent(showInlineCompilationErrorsBox)
                .addComponent(costSettingsPanel.getComponent(), 12)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        refreshNodeJsState();
    }

    private static @NotNull JComponent comment(@NotNull String text) {
        return ComponentPanelBuilder.createCommentComponent(text, true);
    }

    // ── Node.js state ─────────────────────────────────────────────────────

    private void refreshNodeJsState() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        boolean nodeConfigured = openProjects.length > 0
                && NodeInterpreterManager.getInstance(openProjects[0]).npmExecutable() != null;

        installButton.setEnabled(nodeConfigured);

        if (nodeConfigured) {
            NodeInterpreterManager nim = NodeInterpreterManager.getInstance(openProjects[0]);
            installStatus.info("Packages will be installed globally into the current " +
                    "Node.js installation: " + nim.nodeInstallDir());
        } else {
            installStatus.warning("Node.js is not configured. Please set it up in " +
                    "Settings → Languages & Frameworks → JavaScript Runtime " +
                    "before installing Dataform packages.");
        }
    }

    // ── Install ───────────────────────────────────────────────────────────

    private void onInstall() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) return;
        Project project = openProjects[0];

        NodeInterpreterManager nim = NodeInterpreterManager.getInstance(project);
        if (nim.npmExecutable() == null) {
            refreshNodeJsState();
            return;
        }

        installButton.setEnabled(false);
        installStatus.progress("Installing @dataform/cli and @dataform/core…");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {

            InstallResult cliResult = NodeJsNpmUtils.installNodeJsLib(
                    "@dataform/cli",
                    nim.npmExecutable().toFile(),
                    nim.nodeBinDir().toFile(),
                    nim.nodeInstallDir().toFile());

            if (!cliResult.success()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    installButton.setEnabled(true);
                    installStatus.error("@dataform/cli installation failed " +
                            "(@dataform/core installation was skipped):\n" +
                            cliResult.errorMessage());
                }, ModalityState.any());
                return;
            }

            InstallResult coreResult = NodeJsNpmUtils.installNodeJsLib(
                    "@dataform/core",
                    nim.npmExecutable().toFile(),
                    nim.nodeBinDir().toFile(),
                    nim.nodeInstallDir().toFile());

            NodeInterpreterManager.invalidate();
            Optional<Path> dataformRoot = coreResult.success()
                    ? DataformInstaller.findDataformLibRootDir(NodeInterpreterManager.getInstance(project))
                    : Optional.empty();

            ApplicationManager.getApplication().invokeLater(() -> {
                installButton.setEnabled(true);

                if (!coreResult.success()) {
                    installStatus.error("@dataform/core installation failed:\n" +
                            coreResult.errorMessage());
                    return;
                }

                dataformRoot.ifPresent(root -> {
                    coreInstallField.setText(root.resolve("core").toAbsolutePath().toString());
                });

                installStatus.ok("Dataform CLI and Core installed successfully.");

            }, ModalityState.any());
        });
    }

    // ── Accesseurs ────────────────────────────────────────────────────────

    public JPanel  getPanel()             { return panel; }
    public String  getCoreInstallPath()   { return coreInstallField.getText().trim(); }
    public void    setCoreInstallPath(String path)   { coreInstallField.setText(path); }
    public String  getSqlfluffExecutablePath() { return sqlfluffExecutableField.getText().trim(); }
    public String  getSqlfluffConfigPath() { return sqlfluffConfigField.getText().trim(); }
    public String  getSqlfluffExtraArgs() { return sqlfluffArgsField.getText().trim(); }
    public void    setSqlfluffExecutablePath(String path) { sqlfluffExecutableField.setText(path); }
    public void    setSqlfluffConfigPath(String path) { sqlfluffConfigField.setText(path); }
    public void    setSqlfluffExtraArgs(String args) { sqlfluffArgsField.setText(args); }
    public boolean isFoldTemplateExpressions() { return foldTemplateExpressionsBox.isSelected(); }
    public void    setFoldTemplateExpressions(boolean value) { foldTemplateExpressionsBox.setSelected(value); }

    public boolean isShowInlineCompilationErrors() { return showInlineCompilationErrorsBox.isSelected(); }
    public void    setShowInlineCompilationErrors(boolean value) { showInlineCompilationErrorsBox.setSelected(value); }

    /**
     * Returns the "BigQuery cost" section of the page.
     */
    public BigQueryCostSettingsPanel getCostSettingsPanel() { return costSettingsPanel; }
}
