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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static io.github.rejeb.dataform.language.util.Utils.formatSql;

public class ActionSummaryPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Project project;

    private final JLabel urlLabel = new JBLabel("—");
    private final JLabel jobIdLabel = new JBLabel("—");
    private final JTextField startLabel = RunConfigUiUtils.selectableValue("—");
    private final JTextField statusLabel = RunConfigUiUtils.selectableValue("—");
    private final JTextField errorLabel = RunConfigUiUtils.selectableValue("—");
    private final JTextField durationLabel = RunConfigUiUtils.selectableValue("—");

    private Editor sqlEditor;
    private final JPanel sqlContainer = new JPanel(new BorderLayout());

    public ActionSummaryPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIUtil.getPanelBackground());
        contentPanel.add(buildMetaPanel());
        contentPanel.add(sqlContainer);

        add(new JBScrollPane(contentPanel), BorderLayout.CENTER);
    }

    private JPanel buildMetaPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        panel.setBorder(JBUI.Borders.empty(8, 12));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] keys = {"Execution URL", "Job ID", "Status", "Failure reason", "Start time", "Duration"};
        Component[] values = {urlLabel, jobIdLabel, statusLabel, errorLabel, startLabel, durationLabel};

        GridBagConstraints kc = new GridBagConstraints();
        kc.anchor = GridBagConstraints.NORTHWEST;
        kc.insets = JBUI.insets(2, 0, 2, 12);
        kc.fill = GridBagConstraints.NONE;

        GridBagConstraints vc = new GridBagConstraints();
        vc.anchor = GridBagConstraints.NORTHWEST;
        vc.insets = JBUI.insets(2, 0);
        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.weightx = 1.0;
        vc.gridwidth = GridBagConstraints.REMAINDER;

        for (int i = 0; i < keys.length; i++) {
            kc.gridy = vc.gridy = i;
            kc.gridx = 0;
            vc.gridx = 1;
            JLabel key = new JBLabel(keys[i] + ":");
            key.setForeground(UIUtil.getLabelDisabledForeground());
            panel.add(key, kc);
            panel.add(values[i], vc);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        return panel;
    }

    /**
     * Populates the panel with the given action's data. Must be called on the EDT.
     */
    public void show(@NotNull WorkflowInvocationProgress progress,
                     @NotNull InvocationActionResult action) {
        InvocationSummary summary = progress.summary();
        String invName = summary != null ? summary.invocationName() : "";
        setLink(urlLabel, shortName(invName), buildWorkflowConsoleUrl(invName));

        startLabel.setText(action.startTime() != null
                ? TIME_FMT.format(action.startTime()) : "—");

        if (action.startTime() != null && action.endTime() != null) {
            durationLabel.setText(RunConfigUiUtils.formatDuration(Duration.between(action.startTime(), action.endTime())));
        } else {
            durationLabel.setText("—");
        }

        if (action.jobId() != null) {
            String bqUrl = buildBigQueryJobUrl(action.jobId(), invName);
            if (bqUrl != null) {
                setLink(jobIdLabel, shortJobId(action.jobId()), bqUrl);
            } else {
                jobIdLabel.setText(action.jobId());
            }
        } else {
            jobIdLabel.setText("—");
        }

        statusLabel.setText(action.state().name());

        if(action.failureReason() != null){
            errorLabel.setText(action.failureReason());
        } else {
            errorLabel.setText("—");
        }

        updateSqlEditor(action.sqlScript());
    }

    private void updateSqlEditor(@Nullable String sql) {
        if (sqlEditor != null) {
            EditorFactory.getInstance().releaseEditor(sqlEditor);
            sqlEditor = null;
        }
        sqlContainer.removeAll();

        String content = sql != null ? formatSql(project, sql) : "";
        var document = EditorFactory.getInstance().createDocument(content);
        var fileType = FileTypeManager.getInstance().getFileTypeByExtension("sql");
        EditorEx editor = (EditorEx) EditorFactory.getInstance()
                .createEditor(document, project, fileType, true);

        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(true);
        settings.setFoldingOutlineShown(false);
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(false);
        settings.setVirtualSpace(false);
        settings.setUseSoftWraps(true);          // wrap → pas de scroll horizontal
        editor.setHorizontalScrollbarVisible(false);
        editor.setVerticalScrollbarVisible(false); // scroll géré par le parent
        sqlEditor = editor;

        sqlContainer.add(buildSqlTitleLabel(), BorderLayout.NORTH);
        sqlContainer.add(editor.getComponent(), BorderLayout.CENTER);
        sqlContainer.revalidate();
        sqlContainer.repaint();
    }

    @NotNull
    private static JBLabel buildSqlTitleLabel() {
        JBLabel title = new JBLabel("Executed code");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(JBUI.Borders.empty(6, 8, 4, 0));
        title.setForeground(UIUtil.getLabelDisabledForeground());
        return title;
    }

    /**
     * Releases the IntelliJ editor. Must be called when this panel is disposed.
     */
    public void release() {
        if (sqlEditor != null) {
            EditorFactory.getInstance().releaseEditor(sqlEditor);
            sqlEditor = null;
        }
    }

    /**
     * Builds the GCP Console URL for the workflow invocation.
     * invocationName = projects/{project}/locations/{location}/repositories/{repo}/workflowInvocations/{id}
     */
    @NotNull
    private static String buildWorkflowConsoleUrl(@NotNull String invocationName) {
        String[] parts = invocationName.split("/");
        if (parts.length < 8) return "https://console.cloud.google.com/";
        String project = parts[1];
        String location = parts[3];
        String repo = parts[5];
        String id = parts[7];
        return "https://console.cloud.google.com/bigquery/dataform/locations/"
                + location + "/repositories/" + repo
                + "/workflows/" + id
                + "?project=" + project;
    }

    /**
     * Builds the BigQuery Console URL for a job.
     * Handles multiple jobId formats returned by the GCP SDK:
     * - "projects/{project}/jobs/{id}"  (full resource name)
     * - "{project}:{location}.{id}"     (BigQuery native format)
     * - bare "{id}"                     (fallback, uses project/location from invocationName)
     */
    @Nullable
    private static String buildBigQueryJobUrl(@NotNull String jobId, @NotNull String invocationName) {
        String[] invParts = invocationName.split("/");
        String invProject = invParts.length >= 2 ? invParts[1] : null;
        String invLocation = invParts.length >= 4 ? invParts[3] : "US";

        // Format 1 : "projects/{project}/jobs/{id}"
        String[] parts = jobId.split("/");
        if (parts.length >= 4 && "projects".equals(parts[0]) && "jobs".equals(parts[2])) {
            return buildBqUrl(parts[1], invLocation, parts[3]);
        }

        // Format 2 : "{project}:{location}.{id}"
        if (jobId.contains(":") && jobId.contains(".")) {
            int colonIdx = jobId.indexOf(':');
            int dotIdx = jobId.indexOf('.', colonIdx);
            if (colonIdx > 0 && dotIdx > colonIdx) {
                String proj = jobId.substring(0, colonIdx);
                String loc = jobId.substring(colonIdx + 1, dotIdx);
                String id = jobId.substring(dotIdx + 1);
                return buildBqUrl(proj, loc, id);
            }
        }

        // Format 3 : bare ID — utilise project/location de l'invocation
        if (invProject != null && !jobId.contains("/")) {
            return buildBqUrl(invProject, invLocation, jobId);
        }

        return null;
    }

    @NotNull
    private static String buildBqUrl(@NotNull String project,
                                     @NotNull String location,
                                     @NotNull String jobId) {
        return "https://console.cloud.google.com/bigquery"
                + "?project=" + project
                + "&j=bq:" + location + ":" + jobId
                + "&page=queryresults";
    }

    /**
     * Returns the last path segment of a GCP resource name.
     */
    @NotNull
    private static String shortName(@NotNull String fullName) {
        int idx = fullName.lastIndexOf('/');
        return idx >= 0 ? fullName.substring(idx + 1) : fullName;
    }

    /**
     * Returns a human-readable job ID from a full GCP job resource name.
     */
    @NotNull
    private static String shortJobId(@NotNull String jobId) {
        return shortName(jobId);
    }

    private void setLink(@NotNull JLabel label, @NotNull String text, @NotNull String url) {
        label.setText("<html><a href=''>" + text + "</a></html>");
        for (var listener : label.getMouseListeners()) {
            label.removeMouseListener(listener);
        }
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception ignored) {
                }
            }
        });
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

}