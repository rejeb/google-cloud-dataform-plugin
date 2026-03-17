/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.gcp.execution.serviceview;

import com.intellij.database.data.types.BaseConversionGraph;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridHelperImpl;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extractors.BaseObjectFormatter;
import com.intellij.database.extractors.FormatterCreator;
import com.intellij.database.run.ui.FloatingPagingManager;
import com.intellij.database.run.ui.TableResultPanel;
import com.intellij.database.run.ui.grid.GridMainPanel;
import com.intellij.database.run.ui.grid.editors.*;
import com.intellij.database.run.ui.grid.renderers.*;
import com.intellij.database.settings.DatabaseSettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryJobStats;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryPagedResult;
import io.github.rejeb.dataform.language.gcp.execution.grid.BqDataHookUp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class QueryExecutionPanel extends JPanel {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DecimalFormat BYTES_FMT = new DecimalFormat("#,###");

    public QueryExecutionPanel(@NotNull Project project, @NotNull BigQueryJobResult result) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        JBTabbedPane tabs = new JBTabbedPane();
        tabs.setTabComponentInsets(JBUI.emptyInsets());
        tabs.addTab("Job Info", buildJobInfoPanel(result));
        JComponent mainPanel = buildResultsPanel(project, result);
        tabs.addTab("Results", mainPanel);
        add(tabs, BorderLayout.CENTER);
    }

    private record InfoRow(String label, String value) {
    }

    private JComponent buildJobInfoPanel(@NotNull BigQueryJobResult result) {
        List<InfoRow> rows = new ArrayList<>();

        if (!result.isSuccess()) {
            rows.add(new InfoRow("Status", "FAILED"));
            rows.add(new InfoRow("Error", result.errorMessage() != null ? result.errorMessage() : "-"));
        } else {
            BigQueryJobStats s = result.stats();
            if (s != null) {
                long duration = s.endTime() - s.startTime();
                rows.add(new InfoRow("Status", "SUCCESS"));
                rows.add(new InfoRow("Job ID", s.jobId()));
                rows.add(new InfoRow("Project", s.projectId()));
                rows.add(new InfoRow("Location", s.location() != null ? s.location() : "-"));
                rows.add(new InfoRow("Statement type", s.statementType() != null ? s.statementType() : "-"));
                rows.add(new InfoRow("Cache hit", String.valueOf(s.cacheHit())));
                rows.add(new InfoRow("Bytes processed", BYTES_FMT.format(s.bytesProcessed()) + " B"));
                rows.add(new InfoRow("Duration", duration + " ms"));
                rows.add(new InfoRow("Created at", formatTs(s.creationTime())));
                rows.add(new InfoRow("Started at", formatTs(s.startTime())));
                rows.add(new InfoRow("Ended at", formatTs(s.endTime())));
                rows.add(new InfoRow("Rows (total)", String.valueOf(s.totalRows())));

            }
        }

        ColumnInfo<InfoRow, String> labelCol = new ColumnInfo<>("Property") {
            @Override
            public @Nullable String valueOf(InfoRow row) {
                return row.label();
            }

            @Override
            public int getWidth(JTable t) {
                return JBUI.scale(160);
            }
        };
        ColumnInfo<InfoRow, String> valueCol = new ColumnInfo<>("Value") {
            @Override
            public @Nullable String valueOf(InfoRow row) {
                return row.value();
            }
        };

        ListTableModel<InfoRow> model = new ListTableModel<>(
                new ColumnInfo[]{labelCol, valueCol}, rows);

        JBTable table = new JBTable(model);
        table.setShowGrid(false);
        table.setRowHeight(JBUI.scale(24));
        table.setFocusable(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Colonne label en gras + couleur secondaire
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                if (!sel) lbl.setForeground(UIUtil.getContextHelpForeground());
                lbl.setBorder(JBUI.Borders.emptyLeft(8));
                return lbl;
            }
        });

        // Colonne valeur
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean focus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                lbl.setBorder(JBUI.Borders.emptyLeft(8));
                return lbl;
            }
        });

        JBScrollPane scroll = new JBScrollPane(table);
        scroll.setBorder(JBUI.Borders.empty());
        return scroll;
    }

    // ── Results ───────────────────────────────────────────────────────────────

    private JComponent buildResultsPanel(@NotNull Project project, @NotNull BigQueryJobResult result) {
        if (!result.isSuccess()) {
            JLabel error = new JLabel("Execution failed — see Job Info tab.");
            error.setBorder(JBUI.Borders.empty(16));
            error.setForeground(UIUtil.getErrorForeground());
            return error;
        }

        BqDataHookUp hookUp = new BqDataHookUp(project, result.pagedResult());
        TableResultPanel panel = new TableResultPanel(
                project,
                hookUp,
                ActionGroup.EMPTY_GROUP,
                (grid, appearance) -> {
                    GridUtil.putSettings(grid, ApplicationManager.getApplication().getService(DatabaseSettings.class));
                    GridHelperImpl helper = new GridHelperImpl();

                    helper.setDefaultPageSize(BigQueryPagedResult.DEFAULT_PAGE_SIZE);
                    helper.setLimitDefaultPageSize(true);
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
        );

        ApplicationManager.getApplication().invokeLater(() -> {
            hookUp.getLoader().reloadCurrentPage(
                    new GridRequestSource(new GridRequestSource.RequestPlace() {})
            );
        });

        GridMainPanel mainPanel = panel.getPanel();
        mainPanel.setBorder(JBUI.Borders.empty());
        return mainPanel;
    }


    private String formatTs(long ts) {
        if (ts == 0) return "-";
        return DATE_FMT.format(Instant.ofEpochMilli(ts));
    }

}
