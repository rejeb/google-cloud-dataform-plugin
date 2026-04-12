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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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

    private final TextFieldWithBrowseButton coreInstallField    = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton sqlfluffExecutableField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton sqlfluffConfigField = new TextFieldWithBrowseButton();
    private final JTextField sqlfluffArgsField = new JTextField();
    private final JButton    installButton    = new JButton("Install Dataform CLI & Core");
    private final JTextPane  statusPane       = buildStatusPane();
    private final JScrollPane statusScrollPane = buildStatusScrollPane();
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

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.add(installButton);

        panel = FormBuilder.createFormBuilder()
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Dataform Core directory"), new JSeparator())
                .addComponent(coreInstallField, 10)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("SQLFluff executable"), new JSeparator())
                .addComponent(sqlfluffExecutableField, 10)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("SQLFluff config file (optional)"), new JSeparator())
                .addComponent(sqlfluffConfigField, 10)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("SQLFluff extra args"), new JSeparator())
                .addComponent(sqlfluffArgsField, 10)
                .addVerticalGap(10)
                .addComponent(buttonRow)
                .addVerticalGap(5)
                .addComponent(statusScrollPane)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        refreshNodeJsState();
    }

    // ── Status pane ───────────────────────────────────────────────────────

    private static JTextPane buildStatusPane() {
        JTextPane pane = new JTextPane();
        pane.setContentType("text/plain");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        return pane;
    }

    private JScrollPane buildStatusScrollPane() {
        JScrollPane scroll = new JBScrollPane(statusPane);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(400, 0));
        scroll.setVisible(false);
        return scroll;
    }

    private void updateStatusPaneHeight() {
        statusPane.setSize(new Dimension(statusScrollPane.getViewport().getWidth(), Integer.MAX_VALUE));
        int preferredHeight = statusPane.getPreferredSize().height;
        int height = Math.min(preferredHeight + 4, 250);
        statusScrollPane.setPreferredSize(new Dimension(400, height));
        statusScrollPane.revalidate();
    }

    private void showInfo(@NotNull String text) {
        statusPane.setForeground(UIUtil.getLabelForeground());
        statusPane.setFont(JBUI.Fonts.smallFont());
        statusPane.setText(text);
        statusScrollPane.setVisible(true);
        updateStatusPaneHeight();
    }

    private void showWarning(@NotNull String text) {
        statusPane.setForeground(UIUtil.getErrorForeground());
        statusPane.setFont(JBUI.Fonts.smallFont());
        statusPane.setText("⚠ " + text);
        statusScrollPane.setVisible(true);
        updateStatusPaneHeight();
    }

    private void showError(@NotNull String text) {
        statusPane.setForeground(UIUtil.getErrorForeground());
        statusPane.setFont(JBUI.Fonts.smallFont());
        statusPane.setText("❌ " + text);
        statusScrollPane.setVisible(true);
        updateStatusPaneHeight();
    }

    private void showSuccess(@NotNull String text) {
        statusPane.setForeground(JBUI.CurrentTheme.NotificationInfo.foregroundColor());
        statusPane.setFont(JBUI.Fonts.smallFont());
        statusPane.setText("✅ " + text);
        statusScrollPane.setVisible(true);
        updateStatusPaneHeight();
    }

    // ── Node.js state ─────────────────────────────────────────────────────

    private void refreshNodeJsState() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        boolean nodeConfigured = openProjects.length > 0
                && NodeInterpreterManager.getInstance(openProjects[0]).npmExecutable() != null;

        installButton.setEnabled(nodeConfigured);

        if (nodeConfigured) {
            NodeInterpreterManager nim = NodeInterpreterManager.getInstance(openProjects[0]);
            showInfo("Packages will be installed globally into the current " +
                    "Node.js installation: " + nim.nodeInstallDir());
        } else {
            showWarning("Node.js is not configured. Please set it up in " +
                    "Settings → Languages & Frameworks → Node.js " +
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
        installButton.setText("Installing…");
        statusScrollPane.setVisible(false);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {

            InstallResult cliResult = NodeJsNpmUtils.installNodeJsLib(
                    "@dataform/cli",
                    nim.npmExecutable().toFile(),
                    nim.nodeBinDir().toFile(),
                    nim.nodeInstallDir().toFile());

            if (!cliResult.success()) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    installButton.setEnabled(true);
                    installButton.setText("Install Dataform CLI & Core");
                    showError("@dataform/cli installation failed " +
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

            ApplicationManager.getApplication().invokeLater(() -> {
                installButton.setEnabled(true);
                installButton.setText("Install Dataform CLI & Core");

                if (!coreResult.success()) {
                    showError("@dataform/core installation failed:\n" +
                            coreResult.errorMessage());
                    return;
                }

                NodeInterpreterManager nimRefreshed = NodeInterpreterManager.getInstance(project);
                Optional<Path> dataformRoot = DataformInstaller.findDataformLibRootDir(nimRefreshed);
                dataformRoot.ifPresent(root -> {
                    coreInstallField.setText(root.resolve("core").toAbsolutePath().toString());
                });

                showSuccess("Dataform CLI and Core installed successfully.");

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
}
