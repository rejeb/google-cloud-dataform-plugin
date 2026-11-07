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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview;

import com.intellij.database.data.types.BaseConversionGraph;
import com.intellij.database.datagrid.*;
import com.intellij.database.extractors.BaseObjectFormatter;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.run.ui.TableResultPanel;
import com.intellij.database.run.ui.grid.GridMainPanel;
import com.intellij.database.run.ui.grid.editors.*;
import com.intellij.database.run.ui.grid.renderers.*;
import com.intellij.database.settings.DatabaseSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionWrapper;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobStats;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryPagedResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRunState;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.grid.BqDataHookUp;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.RunConfigUiUtils;
import io.github.rejeb.dataform.language.gcp.pricing.BigQueryPricingService;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.ui.Banners;
import io.github.rejeb.dataform.language.ui.DetailsForm;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import io.github.rejeb.dataform.language.ui.Toolbars;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static io.github.rejeb.dataform.language.util.Utils.formatBytes;

/**
 * The detail of a query run in the Services view: a summary line, then the Results, Job Info and
 * Query tabs. Disposing the panel releases its data grid and its query editor.
 */
public class QueryExecutionPanel extends JPanel implements Disposable {

    private static final String PLACE = "Dataform.QueryResult";
    private static final String UNKNOWN_ERROR = "Unknown error";
    private static final String NO_VALUE = "—";
    private static final String SELECT = "SELECT";

    private final SimpleColoredComponent summary = new SimpleColoredComponent();
    private final JBTabbedPane tabs = new JBTabbedPane();
    private final JComponent results;
    private final JComponent jobInfo;
    private final SqlViewer queryViewer;
    private final @Nullable AnAction cancelAction;
    private @Nullable InlineBanner failureBanner;

    public QueryExecutionPanel(@NotNull Project project, @NotNull QueryRun run) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());
        BigQueryJobResult result = run.result();
        BigQueryJobStats stats = result != null ? result.stats() : null;
        CostText cost = stats != null
                ? JobInfoCost.of(stats, BigQueryPricingService.getInstance().quote(stats.location()))
                : null;
        cancelAction = run.state() == QueryRunState.RUNNING && run.cancel() != null ? new CancelAction(run.cancel()) : null;
        showSummary(run, stats, cost);
        results = buildResults(project, run, stats);
        jobInfo = buildJobInfo(run, stats, cost);
        queryViewer = new SqlViewer(project, SqlViewerOptions.fill());
        queryViewer.setText(run.sql());
        tabs.setTabComponentInsets(JBUI.emptyInsets());
        tabs.addTab("Results", results);
        tabs.addTab("Job Info", scrollable(jobInfo));
        tabs.addTab("Query", padded(queryViewer));
        tabs.setSelectedIndex(0);
        add(header(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * The actions of the pager under the rows: the paging of the data grid with the prototype icons,
     * then its export.
     */
    static @NotNull DefaultActionGroup pagerActions() {
        DefaultActionGroup group = new DefaultActionGroup();
        addWithIcon(group, "Console.TableResult.FirstPage", DataformIcons.PAGE_FIRST);
        addWithIcon(group, "Console.TableResult.PreviousPage", DataformIcons.PAGE_PREVIOUS);
        addAsIs(group, "Console.TableResult.ChangePageSize");
        addAsIs(group, "Console.TableResult.CountRows");
        addWithIcon(group, "Console.TableResult.NextPage", DataformIcons.PAGE_NEXT);
        addWithIcon(group, "Console.TableResult.LastPage", DataformIcons.PAGE_LAST);
        AnAction export = ActionManager.getInstance().getAction("Console.TableResult.ShowDumpDialogAction");
        if (export != null) {
            group.addSeparator();
            group.add(Toolbars.withText(new PrototypeIconAction(export, DataformIcons.DOWNLOAD, "Export")));
        }
        return group;
    }

    private static void addWithIcon(@NotNull DefaultActionGroup group, @NotNull String id, @NotNull Icon icon) {
        AnAction action = ActionManager.getInstance().getAction(id);
        if (action != null) {
            group.add(new PrototypeIconAction(action, icon, null));
        }
    }

    private static void addAsIs(@NotNull DefaultActionGroup group, @NotNull String id) {
        AnAction action = ActionManager.getInstance().getAction(id);
        if (action != null) {
            group.add(action);
        }
    }

    @NotNull
    SimpleColoredComponent summary() {
        return summary;
    }

    @NotNull
    JBTabbedPane tabs() {
        return tabs;
    }

    @NotNull
    JComponent results() {
        return results;
    }

    @NotNull
    JComponent jobInfo() {
        return jobInfo;
    }

    @NotNull
    SqlViewer queryViewer() {
        return queryViewer;
    }

    @Nullable
    InlineBanner failureBanner() {
        return failureBanner;
    }

    @Nullable
    AnAction cancelAction() {
        return cancelAction;
    }

    private @NotNull JComponent header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(6, 12));
        header.add(summary, BorderLayout.CENTER);
        if (cancelAction != null) {
            ActionToolbar toolbar = Toolbars.horizontal(PLACE, this, cancelAction);
            header.add(toolbar.getComponent(), BorderLayout.EAST);
        }
        return header;
    }

    private void showSummary(@NotNull QueryRun run, @Nullable BigQueryJobStats stats, @Nullable CostText cost) {
        summary.setOpaque(false);
        summary.setIcon(QueryRunIcons.of(run.state()));
        switch (run.state()) {
            case RUNNING -> summary.append("Running " + run.shortName() + "…");
            case CANCELLED -> summary.append("Cancelled", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            case FAILED -> {
                summary.append("Query failed", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                if (stats != null) {
                    appendGrayed(duration(stats));
                }
            }
            case SUCCEEDED -> {
                if (stats == null || cost == null) {
                    summary.append("Query succeeded", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    return;
                }
                summary.append(rows(stats.totalRows()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                appendGrayed(duration(stats), formatBytes(stats.bytesProcessed()) + " processed", cost.value(),
                        stats.cacheHit() ? "cache hit" : "");
            }
        }
    }

    private void appendGrayed(@NotNull String... parts) {
        for (String part : parts) {
            if (!part.isBlank()) {
                summary.append(UiText.SEPARATOR + part, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }

    private static @NotNull String rows(long count) {
        return count == 1 ? "1 row" : String.format(Locale.ROOT, "%,d rows", count);
    }

    private static @NotNull String duration(@NotNull BigQueryJobStats stats) {
        return NlsMessages.formatDuration(stats.endTime() - stats.startTime());
    }

    private @NotNull JComponent buildResults(@NotNull Project project, @NotNull QueryRun run,
                                             @Nullable BigQueryJobStats stats) {
        BigQueryJobResult result = run.result();
        return switch (run.state()) {
            case RUNNING -> new JBLabel("Waiting for BigQuery…", DataformIcons.SPINNER, SwingConstants.CENTER);
            case CANCELLED -> emptyState("The query was cancelled.", null);
            case FAILED -> buildFailurePanel(project, run, errorOf(result));
            case SUCCEEDED -> result != null && result.pagedResult() != null && result.pagedResult().getTotalRows() > 0
                    ? buildResultsPanel(project, result.pagedResult())
                    : noRows(stats);
        };
    }

    private static @NotNull JComponent noRows(@Nullable BigQueryJobStats stats) {
        boolean statement = stats != null && stats.statementType() != null && !SELECT.equals(stats.statementType());
        return statement
                ? emptyState("Statement executed", "The statement returned no rows.")
                : emptyState("No rows", "The query returned no rows.");
    }

    private static @NotNull JComponent emptyState(@NotNull String primary, @Nullable String secondary) {
        JBPanelWithEmptyText panel = new JBPanelWithEmptyText();
        if (secondary != null) {
            EmptyStates.show(panel.getEmptyText(), primary, secondary);
        } else {
            EmptyStates.show(panel.getEmptyText(), primary);
        }
        return panel;
    }

    private @NotNull JComponent buildFailurePanel(@NotNull Project project, @NotNull QueryRun run, @NotNull String message) {
        InlineBanner banner = Banners.error("BigQuery error", message);
        banner.setIcon(DataformIcons.STATUS_FAILED);
        if (run.source() != null) {
            banner.addAction("Show in editor", () -> new OpenFileDescriptor(project, run.source()).navigate(true));
        }
        banner.addAction("Copy error", () -> CopyPasteManager.getInstance().setContents(new StringSelection(message)));
        failureBanner = banner;
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(12, 16));
        panel.add(banner, BorderLayout.NORTH);
        return panel;
    }

    private JComponent buildJobInfo(@NotNull QueryRun run, @Nullable BigQueryJobStats stats, @Nullable CostText cost) {
        DetailsForm job = DetailsForm.create().group("Job")
                .row("Job ID", value(stats != null ? stats.jobId() : null));
        if (stats != null) {
            job.row("GCP console", consoleLink(stats));
        }
        job.row("Location", value(stats != null ? stats.location() : null))
                .row("State", value(stateOf(run.state())))
                .row("Created", value(formatTs(stats != null && stats.creationTime() > 0
                        ? stats.creationTime() : run.createdAt())))
                .row("Duration", value(stats != null ? duration(stats) : null));
        DetailsForm statistics = DetailsForm.create().group("Statistics")
                .row("Bytes processed", value(stats != null ? formatBytes(stats.bytesProcessed()) : null))
                .row("Bytes billed", value(stats != null ? formatBytes(stats.bytesBilled()) : null))
                .row("Estimated cost", cost != null ? costRow(cost) : value(null))
                .row("Slot time", value(stats != null ? NlsMessages.formatDuration(stats.slotMillis()) : null))
                .row("Cache hit", value(stats != null ? (stats.cacheHit() ? "Yes" : "No") : null));
        return DetailsForm.columns(job.build(), statistics.build());
    }

    private static @NotNull JComponent padded(@NotNull JComponent content) {
        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(JBUI.Borders.empty(14, 16));
        padded.add(content, BorderLayout.CENTER);
        return padded;
    }

    private static @NotNull String stateOf(@NotNull QueryRunState state) {
        return switch (state) {
            case RUNNING -> "RUNNING";
            case SUCCEEDED -> "DONE";
            case FAILED -> "DONE (error)";
            case CANCELLED -> "CANCELLED";
        };
    }

    private static @NotNull BrowserLink consoleLink(@NotNull BigQueryJobStats stats) {
        String url = RunConfigUiUtils.bigQueryJobUrl(stats.projectId(), stats.location(), stats.jobId());
        BrowserLink link = new BrowserLink(AllIcons.Ide.External_link_arrow, "Open in Google Cloud console", url, url);
        link.setHorizontalTextPosition(SwingConstants.LEFT);
        return link;
    }

    private static JComponent scrollable(@NotNull JComponent content) {
        JPanel padded = new JPanel(new BorderLayout());
        padded.setBorder(JBUI.Borders.empty(14, 16));
        padded.add(content, BorderLayout.NORTH);
        JBScrollPane scroll = new JBScrollPane(padded);
        scroll.setBorder(JBUI.Borders.empty());
        return scroll;
    }

    private static JComponent costRow(@NotNull CostText cost) {
        JBTextField value = value(cost.value());
        JBLabel note = new JBLabel();
        JPanel row = RunConfigUiUtils.costRow(value, note);
        RunConfigUiUtils.showCost(value, note, cost);
        return row;
    }

    private static JBTextField value(@Nullable String text) {
        return ReadOnlyTextFields.singleLine(text != null && !text.isBlank() ? text : NO_VALUE);
    }

    private JComponent buildResultsPanel(@NotNull Project project, @NotNull BigQueryPagedResult pagedResult) {
        BqDataHookUp hookUp = new BqDataHookUp(project, pagedResult);
        TableResultPanel panel = new TableResultPanel(
                project,
                hookUp,
                ActionGroup.EMPTY_GROUP,
                this::configureGridAppearance
        );
        hookUp.getLoader().loadFirstPage(
                new GridRequestSource(new GridRequestSource.RequestPlace() {
                }));

        Disposer.register(this, panel);
        GridMainPanel mainPanel = panel.getPanel();
        mainPanel.setBorder(JBUI.Borders.empty());
        ActionToolbar pager = Toolbars.horizontal(PLACE + ".Pager", mainPanel.getComponent(), pagerActions().getChildActionsOrStubs());
        pager.getComponent().setBorder(JBUI.Borders.compound(JBUI.Borders.customLineTop(JBColor.border()),
                JBUI.Borders.empty(2, 6)));
        mainPanel.setBottomHeaderComponent(pager.getComponent());
        return mainPanel;
    }

    @Override
    public void dispose() {
        queryViewer.dispose();
    }

    private static @NotNull String errorOf(@Nullable BigQueryJobResult result) {
        return result != null && result.errorMessage() != null ? result.errorMessage() : UNKNOWN_ERROR;
    }

    private void configureGridAppearance(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance) {

        GridUtil.putSettings(grid, ApplicationManager.getApplication().getService(DatabaseSettings.class));
        GridHelperImpl helper = new GridHelperImpl();

        GridHelper.set(grid, helper);
        GridCellEditorHelper.set(grid, new GridCellEditorHelperImpl());
        GridCellEditorFactoryProvider.set(grid, GridCellEditorFactoryImpl.getInstance());

        List<GridCellRendererFactory> renderers = List.of(
                new DefaultBooleanRendererFactory(grid),
                new DefaultNumericRendererFactory(grid),
                new DefaultTextRendererFactory(grid)
        );
        GridCellRendererFactories.set(grid, new GridCellRendererFactories(renderers));

        BaseObjectFormatter formatter = new BaseObjectFormatter();
        grid.setObjectFormatterProvider(g -> formatter);
        BaseConversionGraph.set(grid, new BaseConversionGraph(
                new FormatsCache(),
                FormatterCreator.get(grid),
                grid::getObjectFormatter
        ));

        appearance.setResultViewShowRowNumbers(true);
        appearance.setResultViewStriped(true);
    }

    private String formatTs(long ts) {
        if (ts == 0) return NO_VALUE;
        return RunConfigUiUtils.DATE_TIME.format(Instant.ofEpochMilli(ts));
    }

    private static final class CancelAction extends DumbAwareAction {

        private final Runnable cancel;

        CancelAction(@NotNull Runnable cancel) {
            super("Cancel", "Cancel the query on BigQuery", DataformIcons.STOP);
            this.cancel = cancel;
            Toolbars.withText(this);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            cancel.run();
            e.getPresentation().setEnabled(false);
        }
    }

    private static final class PrototypeIconAction extends AnActionWrapper {

        private final Icon icon;
        private final @Nullable String text;

        PrototypeIconAction(@NotNull AnAction action, @NotNull Icon icon, @Nullable String text) {
            super(action);
            this.icon = icon;
            this.text = text;
            getTemplatePresentation().setIcon(icon);
            if (text != null) {
                getTemplatePresentation().setText(text);
            }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            e.getPresentation().setIcon(icon);
            e.getPresentation().setDisabledIcon(null);
            if (text != null) {
                e.getPresentation().setText(text);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return getDelegate().getActionUpdateThread();
        }
    }
}
