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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.ui.ReadOnlyEditors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Duration;
import javax.swing.*;

import static io.github.rejeb.dataform.language.util.Utils.formatSql;

public class ActionSummaryPanel extends JPanel {


    private final Project project;

    private final JLabel urlLabel = new JBLabel("—");
    private final JLabel jobIdLabel = new JBLabel("—");
    private final JBTextField startLabel = RunConfigUiUtils.selectableValue("—");
    private final JBTextField statusLabel = RunConfigUiUtils.selectableValue("—");
    private final JBTextField errorLabel = RunConfigUiUtils.selectableValue("—");
    private final JBTextField durationLabel = RunConfigUiUtils.selectableValue("—");

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

        RunConfigUiUtils.addKeyValueRows(panel, keys, values, false);
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
        RunConfigUiUtils.setLink(urlLabel, RunConfigUiUtils.shortName(invName),
                summary != null ? summary.gcpConsoleUrl() : InvocationSummary.CONSOLE_URL);

        startLabel.setText(action.startTime() != null
                ? RunConfigUiUtils.DATE_TIME.format(action.startTime()) : "—");

        if (action.startTime() != null && action.endTime() != null) {
            durationLabel.setText(RunConfigUiUtils.formatDuration(Duration.between(action.startTime(), action.endTime())));
        } else {
            durationLabel.setText("—");
        }

        if (action.jobId() != null) {
            String bqUrl = buildBigQueryJobUrl(action.jobId(), invName);
            if (bqUrl != null) {
                RunConfigUiUtils.setLink(jobIdLabel, RunConfigUiUtils.shortName(action.jobId()), bqUrl);
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
        ReadOnlyEditors.release(sqlEditor);
        sqlEditor = null;
        sqlContainer.removeAll();

        String content = sql != null ? formatSql(project, sql) : "";
        EditorEx editor = ReadOnlyEditors.compactSql(project, content, true);
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
        ReadOnlyEditors.release(sqlEditor);
        sqlEditor = null;
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

        String[] parts = jobId.split("/");
        if (parts.length >= 4 && "projects".equals(parts[0]) && "jobs".equals(parts[2])) {
            return RunConfigUiUtils.bigQueryJobUrl(parts[1], invLocation, parts[3]);
        }

        if (jobId.contains(":") && jobId.contains(".")) {
            int colonIdx = jobId.indexOf(':');
            int dotIdx = jobId.indexOf('.', colonIdx);
            if (colonIdx > 0 && dotIdx > colonIdx) {
                String proj = jobId.substring(0, colonIdx);
                String loc = jobId.substring(colonIdx + 1, dotIdx);
                String id = jobId.substring(dotIdx + 1);
                return RunConfigUiUtils.bigQueryJobUrl(proj, loc, id);
            }
        }

        if (invProject != null && !jobId.contains("/")) {
            return RunConfigUiUtils.bigQueryJobUrl(invProject, invLocation, jobId);
        }

        return null;
    }
}
