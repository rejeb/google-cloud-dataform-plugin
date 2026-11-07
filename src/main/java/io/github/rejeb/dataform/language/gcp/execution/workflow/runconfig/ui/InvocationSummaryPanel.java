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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.DetailsForm;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.github.rejeb.dataform.language.util.Utils.formatBytes;

/**
 * The detail of the invocation of a workflow run, shown when its root is selected, as the prototype
 * draws it: a "Workflow invocation" header with its id, then the Invocation, Actions, Repository and
 * BigQuery groups in two columns.
 */
public class InvocationSummaryPanel extends JPanel implements Disposable {

    private static final String TITLE = "Workflow invocation";
    private static final String NO_VALUE = "—";
    private static final String PLACE = "Dataform.RunConsoleInvocation";

    private final String runConfiguration;
    private final DetailHeader header;
    private final JBLoadingPanel loadingPanel;
    private final InlineBanner failureBanner = Banners.error("");
    private final Map<String, JBTextField> values = new HashMap<>();
    private final JBLabel totalProcessedNote = new JBLabel();
    private final JBTextField totalCostLabel = ReadOnlyTextFields.singleLine(NO_VALUE);
    private final JBLabel totalCostNote = new JBLabel();
    private final Color valueForeground;
    private @Nullable String invocationId;

    /**
     * @param runConfiguration the name of the run configuration that started the run
     */
    public InvocationSummaryPanel(@NotNull String runConfiguration) {
        super(new BorderLayout());
        this.runConfiguration = runConfiguration;
        setOpaque(false);
        header = new DetailHeader(PLACE, true, new CopyInvocationIdAction());
        header.show(DataformIcons.STATUS_PENDING, TITLE, null);
        totalProcessedNote.setForeground(UIUtil.getContextHelpForeground());
        JPanel columns = DetailsForm.columns(left(), right());
        valueForeground = values.get("Failed").getForeground();
        failureBanner.setVisible(false);
        JPanel content = new JPanel(new BorderLayout(0, JBUI.scale(12)));
        content.setOpaque(false);
        content.setBorder(JBUI.Borders.empty(14, 16));
        content.add(failureBanner, BorderLayout.NORTH);
        content.add(columns, BorderLayout.CENTER);
        JBScrollPane scroll = new JBScrollPane(content);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.setOpaque(false);
        loadingPanel.add(scroll, BorderLayout.CENTER);
        loadingPanel.startLoading();
        add(header, BorderLayout.NORTH);
        add(loadingPanel, BorderLayout.CENTER);
    }

    @NotNull
    private JComponent left() {
        DetailsForm form = DetailsForm.create().group("Invocation");
        rows(form, "State", "Started", "Duration", "Run configuration", "Included actions");
        form.group("Actions");
        rows(form, "Succeeded", "Failed", "Skipped", "Cancelled", "Remaining");
        return form.build();
    }

    @NotNull
    private JComponent right() {
        DetailsForm form = DetailsForm.create().group("Repository");
        rows(form, "Repository", "Project", "Location", "Compilation", "Service account");
        JBTextField processed = ReadOnlyTextFields.singleLine(NO_VALUE);
        values.put("Bytes processed", processed);
        form.group("BigQuery")
                .row("Bytes processed", RunConfigUiUtils.costRow(processed, totalProcessedNote));
        values.put("Bytes billed", form.row("Bytes billed"));
        form.row("Estimated cost", RunConfigUiUtils.costRow(totalCostLabel, totalCostNote));
        values.put("Estimated cost", totalCostLabel);
        return form.build();
    }

    private void rows(@NotNull DetailsForm form, @NotNull String... keys) {
        for (String key : keys) {
            values.put(key, form.row(key));
        }
    }

    @Override
    public void dispose() {
    }

    boolean isLoading() {
        return loadingPanel.isLoading();
    }

    @NotNull
    InlineBanner failureBanner() {
        return failureBanner;
    }

    @NotNull
    DetailHeader header() {
        return header;
    }

    /**
     * The text of a row, {@code null} when there is no such row.
     */
    @Nullable
    String value(@NotNull String key) {
        JBTextField field = values.get(key);
        return field != null ? field.getText() : null;
    }

    @NotNull
    JBTextField valueField(@NotNull String key) {
        return values.get(key);
    }

    /**
     * Refreshes all fields from the given progress. Must be called on the EDT.
     * Shows a loading spinner until the first non-null summary is received.
     */
    public void update(@NotNull WorkflowInvocationProgress progress) {
        InvocationSummary summary = progress.summary();
        boolean failed = progress.errorMessage() != null && !progress.errorMessage().isBlank();
        if (failed) Banners.setMessage(failureBanner, progress.errorMessage());
        failureBanner.setVisible(failed);
        ActionCounts counts = ActionCounts.of(progress.actions());
        set("State", RunText.state(progress.state()));
        set("Run configuration", runConfiguration);
        set("Included actions", String.valueOf(counts.total()));
        set("Succeeded", String.valueOf(counts.succeeded()));
        set("Failed", String.valueOf(counts.failed()));
        set("Skipped", String.valueOf(counts.skipped()));
        set("Cancelled", String.valueOf(counts.cancelled()));
        set("Remaining", String.valueOf(counts.remaining()));
        values.get("Failed").setForeground(counts.failed() > 0 ? RunStatusHeader.FAILED_COUNT.getFgColor() : valueForeground);
        if (summary == null) {
            header.show(ActionStateIcons.of(progress.state()), TITLE, null);
            if (failed) loadingPanel.stopLoading();
            return;
        }
        loadingPanel.stopLoading();
        invocationId = RunConfigUiUtils.shortName(summary.invocationName());
        header.show(ActionStateIcons.of(progress.state()), TITLE, invocationId,
                InvocationSummary.workflowConsoleUrl(summary.invocationName()),
                "Open the workflow invocation in the Google Cloud console");
        set("Started", RunConfigUiUtils.DATE_TIME.format(summary.startTime()));
        Duration elapsed = RunText.elapsed(summary);
        set("Duration", elapsed != null ? RunText.duration(elapsed) : null);
        set("Repository", summary.repository());
        set("Project", summary.project());
        set("Location", summary.location());
        set("Compilation", summary.compilationResultId());
        set("Service account", summary.serviceAccount());
    }

    /**
     * Shows the bytes and cost of the actions whose job details are known. Must be called on the
     * EDT.
     */
    public void updateTotals(@NotNull RunCostTotals totals) {
        if (totals.withJobs() == 0) {
            set("Bytes processed", null);
            totalProcessedNote.setText("");
            set("Bytes billed", null);
            RunConfigUiUtils.showCost(totalCostLabel, totalCostNote, new CostText(NO_VALUE, "", true));
            return;
        }
        set("Bytes processed", formatBytes(totals.bytesProcessed()));
        totalProcessedNote.setText(totals.coverageNote());
        set("Bytes billed", formatBytes(totals.bytesBilled()));
        RunConfigUiUtils.showCost(totalCostLabel, totalCostNote, totals.costText());
    }

    private void set(@NotNull String key, @Nullable String value) {
        values.get(key).setText(value != null && !value.isBlank() ? value : NO_VALUE);
    }

    private final class CopyInvocationIdAction extends DumbAwareAction {

        private CopyInvocationIdAction() {
            super("Copy Invocation ID", "Copy the ID of the workflow invocation", DataformIcons.COPY);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            if (invocationId != null) CopyPasteManager.getInstance().setContents(new StringSelection(invocationId));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(invocationId != null);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }
}
