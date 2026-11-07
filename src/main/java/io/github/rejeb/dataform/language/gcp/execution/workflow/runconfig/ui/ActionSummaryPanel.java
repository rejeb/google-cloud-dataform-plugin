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

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.pricing.BigQueryPricingService;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import io.github.rejeb.dataform.language.ui.DetailsForm;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static io.github.rejeb.dataform.language.util.Utils.formatBytes;
import static io.github.rejeb.dataform.language.util.Utils.formatSql;

/**
 * The Summary tab of an action of a workflow run, as the prototype lays it out: Execution and
 * BigQuery side by side; and the Executed SQL tab, a code block with a Copy button.
 */
public class ActionSummaryPanel extends JPanel {

    private static final String LOADING = "…";
    private static final String NO_VALUE = "—";

    private final Project project;
    private final Map<String, JBTextField> values = new HashMap<>();
    private final BrowserLink jobIdLink = IdLinks.create(NO_VALUE, null, "Open the job in the BigQuery console");
    private final JBTextField costLabel = ReadOnlyTextFields.singleLine(NO_VALUE);
    private final JBLabel costNoteLabel = new JBLabel();
    private final SqlViewer sqlViewer;
    private final JPanel executedSql;

    public ActionSummaryPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.sqlViewer = new SqlViewer(project, SqlViewerOptions.fill());
        setOpaque(false);
        DetailsForm execution = DetailsForm.create().group("Execution");
        for (String key : new String[]{"Status", "Type", "Target", "Started", "Ended", "Duration"}) {
            values.put(key, execution.row(key));
        }
        DetailsForm bigQuery = DetailsForm.create().group("BigQuery").row("Job ID", jobIdLink);
        values.put("Location", bigQuery.row("Location"));
        values.put("Bytes processed", bigQuery.row("Bytes processed"));
        values.put("Bytes billed", bigQuery.row("Bytes billed"));
        bigQuery.row("Estimated cost", RunConfigUiUtils.costRow(costLabel, costNoteLabel));
        values.put("Estimated cost", costLabel);
        values.put("Slot time", bigQuery.row("Slot time"));
        JPanel body = DetailsForm.columns(execution.build(), bigQuery.build());
        body.setBorder(JBUI.Borders.empty(14, 0));
        JBScrollPane scroll = new JBScrollPane(body);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        executedSql = buildExecutedSql();
    }

    /**
     * The SQL the action executed, shown in its own tab.
     */
    @NotNull
    public JComponent executedSql() {
        return executedSql;
    }

    /**
     * The text of a row of the summary, {@code null} when it has no such row.
     */
    @Nullable
    String value(@NotNull String key) {
        JBTextField field = values.get(key);
        return field != null ? field.getText() : null;
    }

    /**
     * Populates the panel with the given action's data. Must be called on the EDT.
     *
     * @param type the Dataform type of the action, {@code null} when the compiled graph does not know it
     */
    public void show(@NotNull WorkflowInvocationProgress progress, @NotNull InvocationActionResult action,
                     @Nullable String type) {
        InvocationSummary summary = progress.summary();
        String invocationName = summary != null ? summary.invocationName() : progress.invocationName();
        set("Status", RunText.state(action.state()));
        set("Type", type);
        set("Target", action.target());
        set("Started", time(action.startTime()));
        set("Ended", time(action.endTime()));
        set("Duration", RunText.duration(action.startTime(), action.endTime()));
        set("Location", action.jobLocation());
        if (action.jobId() != null) {
            showLink(jobIdLink, RunConfigUiUtils.shortName(action.jobId()), buildBigQueryJobUrl(action.jobId(), invocationName));
        } else {
            showLink(jobIdLink, NO_VALUE, null);
        }
        boolean hasJob = action.jobId() != null && action.jobProject() != null;
        String pending = hasJob ? LOADING : NO_VALUE;
        showJobValues(pending, pending, new CostText(pending, "", true), pending);
        sqlViewer.setText(action.sqlScript() != null ? formatSql(project, action.sqlScript()) : null);
    }

    /**
     * Shows the bytes, cost and slot time of the action's BigQuery job, or dashes when it has none.
     * Must be called on the EDT.
     */
    public void showJobDetails(@Nullable BigQueryJobDetails details) {
        if (details == null) {
            showJobValues(NO_VALUE, NO_VALUE, new CostText(NO_VALUE, "", true), NO_VALUE);
            return;
        }
        PriceQuote quote = BigQueryPricingService.getInstance().quote(details.location());
        set("Location", details.location());
        showJobValues(formatBytes(details.bytesProcessed()), formatBytes(details.bytesBilled()),
                ActionCost.of(details, quote),
                details.slotMillis() != null ? RunText.duration(Duration.ofMillis(details.slotMillis())) : NO_VALUE);
    }

    /**
     * Releases the IntelliJ editor. Must be called when this panel is disposed.
     */
    public void release() {
        sqlViewer.dispose();
    }

    private void showJobValues(@NotNull String processed, @NotNull String billed, @NotNull CostText cost,
                               @NotNull String slotTime) {
        set("Bytes processed", processed);
        set("Bytes billed", billed);
        set("Slot time", slotTime);
        RunConfigUiUtils.showCost(costLabel, costNoteLabel, cost);
    }

    private void set(@NotNull String key, @Nullable String value) {
        values.get(key).setText(value != null && !value.isBlank() ? value : NO_VALUE);
    }

    @Nullable
    private static String time(@Nullable Instant instant) {
        return instant != null ? RunConfigUiUtils.DATE_TIME.format(instant) : null;
    }

    private static void showLink(@NotNull BrowserLink link, @NotNull String text, @Nullable String url) {
        link.setText(text);
        link.setUrl(url != null ? url : "");
        link.setEnabled(url != null && !url.isBlank());
    }

    @NotNull
    private JPanel buildExecutedSql() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(8, 0, 14, 0));
        panel.add(sqlViewer, BorderLayout.CENTER);
        return panel;
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
