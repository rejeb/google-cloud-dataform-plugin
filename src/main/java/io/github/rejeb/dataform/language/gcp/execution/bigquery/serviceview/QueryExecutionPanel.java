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
import com.intellij.database.run.ui.FloatingPagingManager;
import com.intellij.database.run.ui.TableResultPanel;
import com.intellij.database.run.ui.grid.GridMainPanel;
import com.intellij.database.run.ui.grid.editors.*;
import com.intellij.database.run.ui.grid.renderers.*;
import com.intellij.database.settings.DatabaseSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobStats;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryPagedResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.grid.BqDataHookUp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static io.github.rejeb.dataform.language.util.Utils.formatBytes;

public class QueryExecutionPanel extends JPanel {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DecimalFormat BYTES_FMT = new DecimalFormat("#,###");

    public QueryExecutionPanel(@NotNull Project project, @NotNull BigQueryJobResult result) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());
        JComponent resultPanel = result.isSuccess() && result.pagedResult() != null ? buildResultsPanel(project, result.pagedResult()) : buildErrorPanel(result);
        JComponent jobInfoPanel = buildJobInfoPanel(result);
        JBTabbedPane tabs = new JBTabbedPane();
        tabs.setTabComponentInsets(JBUI.emptyInsets());
        tabs.addTab("Job Info", jobInfoPanel);
        tabs.addTab("Results", resultPanel);
        tabs.setSelectedIndex(1);
        add(tabs, BorderLayout.CENTER);
    }

    private JComponent buildJobInfoPanel(@NotNull BigQueryJobResult result) {
        FormBuilder builder = FormBuilder.createFormBuilder();

        if (!result.isSuccess()) {
            builder.addLabeledComponent("Status:", new JBLabel("FAILED"));
            builder.addLabeledComponent("Error:", new JBLabel(
                    result.errorMessage() != null ? result.errorMessage() : "-"));
        } else {
            BigQueryJobStats s = result.stats();
            if (s != null) {
                long duration = s.endTime() - s.startTime();
                String url = buildJobUrl(s.projectId(), s.location(), s.jobId());

                HyperlinkLabel gcpLink = new HyperlinkLabel(url);
                gcpLink.setHyperlinkTarget(url);

                builder
                        .addLabeledComponent("Status:", new JBLabel("SUCCESS"))
                        .addLabeledComponent("Job ID:", new JBLabel(s.jobId()))
                        .addLabeledComponent("GCP console:", gcpLink)
                        .addLabeledComponent("Project:", new JBLabel(s.projectId()))
                        .addLabeledComponent("Location:", new JBLabel(s.location() != null ? s.location() : "-"))
                        .addLabeledComponent("Statement type:", new JBLabel(s.statementType() != null ? s.statementType() : "-"))
                        .addLabeledComponent("Cache hit:", new JBLabel(String.valueOf(s.cacheHit())))
                        .addLabeledComponent("Bytes processed:", new JBLabel(formatBytes(s.bytesProcessed())))
                        .addLabeledComponent("Duration:", new JBLabel(duration + " ms"))
                        .addLabeledComponent("Created at:", new JBLabel(formatTs(s.creationTime())))
                        .addLabeledComponent("Started at:", new JBLabel(formatTs(s.startTime())))
                        .addLabeledComponent("Ended at:", new JBLabel(formatTs(s.endTime())))
                        .addLabeledComponent("Rows (total):", new JBLabel(String.valueOf(s.totalRows())));
            }
        }

        builder.addComponentFillVertically(new JPanel(), 0);
        JPanel panel = builder.getPanel();
        panel.setBorder(JBUI.Borders.empty(8));

        JBScrollPane scroll = new JBScrollPane(panel);
        scroll.setBorder(JBUI.Borders.empty());
        return scroll;
    }


    private static String buildJobUrl(@NotNull String projectId,
                                      @Nullable String location,
                                      @NotNull String jobId) {
        String loc = location != null ? location : "US";
        return String.format(
                "https://console.cloud.google.com/bigquery?project=%s&j=bq:%s:%s&page=queryresults",
                projectId, loc, jobId);
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

        GridMainPanel mainPanel = panel.getPanel();
        mainPanel.setBorder(JBUI.Borders.empty());
        return mainPanel;
    }

    private JComponent buildErrorPanel(@NotNull BigQueryJobResult result) {
        String message = result.errorMessage() != null ? result.errorMessage() : "Unknown error";

        JLabel titleLabel = new JLabel("Query execution failed", AllIcons.General.Error, SwingConstants.LEFT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, JBUI.scaleFontSize(13f)));
        titleLabel.setBorder(JBUI.Borders.empty(12, 12, 8, 12));

        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12f)));
        textArea.setForeground(UIUtil.getErrorForeground());
        textArea.setBackground(UIUtil.getPanelBackground());
        textArea.setBorder(JBUI.Borders.empty(0, 12, 12, 12));
        textArea.setCaretPosition(0);

        JBScrollPane scroll = new JBScrollPane(textArea);
        scroll.setBorder(JBUI.Borders.empty());

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(UIUtil.getPanelBackground());
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
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
        grid.putUserData(FloatingPagingManager.AVAILABLE_FOR_GRID_TYPE, true);

    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String formatTs(long ts) {
        if (ts == 0) return "-";
        return DATE_FMT.format(Instant.ofEpochMilli(ts));
    }
}
