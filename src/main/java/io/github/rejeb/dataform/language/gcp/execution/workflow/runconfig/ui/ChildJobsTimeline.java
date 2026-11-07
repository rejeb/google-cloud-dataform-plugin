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

import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The statements of a script job as a timeline: one row per child job, with its status, number,
 * kind and table, a bar showing when it ran on the script's time range, and the bytes it processed.
 * Callers follow the selected statement through {@link #getSelectionModel()}.
 */
final class ChildJobsTimeline extends JBTable {

    /**
     * The number of rows the timeline is first sized to show.
     */
    static final int FITTED_ROWS = 8;

    private static final int STATEMENT = 0;
    private static final int TIMELINE = 1;
    private static final int BYTES = 2;
    private static final int ROW_HEIGHT = 28;
    private static final int MAX_STATEMENT_WIDTH = 320;
    private static final int COLUMN_PADDING = 8;
    private static final String FAILED = "FAILED";

    private final JobsModel jobs;
    private StatementTimeline timeline = StatementTimeline.of(null, null, List.of());
    private int @Nullable [] contentWidths;

    /**
     * Creates an empty timeline.
     */
    ChildJobsTimeline() {
        this(new JobsModel());
    }

    private ChildJobsTimeline(@NotNull JobsModel model) {
        super(model);
        this.jobs = model;
        getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowHeight(JBUI.scale(ROW_HEIGHT));
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setStriped(false);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setResizingAllowed(false);
        TableColumn statement = getColumnModel().getColumn(STATEMENT);
        statement.setCellRenderer(new StatementRenderer());
        statement.setHeaderRenderer(aligned(SwingConstants.LEFT));
        TableColumn track = getColumnModel().getColumn(TIMELINE);
        track.setCellRenderer(new TimelineCellRenderer(this));
        track.setHeaderRenderer(TimelineCellRenderer.header(this));
        TableColumn bytes = getColumnModel().getColumn(BYTES);
        bytes.setCellRenderer(new BytesRenderer());
        bytes.setHeaderRenderer(aligned(SwingConstants.RIGHT));
    }

    /**
     * Shows the statements of a script job. The statement selected before stays selected when it is
     * still listed; else the first failed statement is selected, else the last one.
     */
    void setJobs(@NotNull BigQueryJobDetails details) {
        BigQueryChildJob previous = selectedJob();
        timeline = StatementTimeline.of(details.startTime(), details.endTime(), details.childJobs());
        jobs.show(details.childJobs());
        contentWidths = null;
        int row = rowToSelect(previous != null ? previous.jobId() : null);
        if (row >= 0) setRowSelectionInterval(row, row);
        getTableHeader().repaint();
        revalidate();
        repaint();
    }

    /**
     * The statement of the selected row, {@code null} when no row is selected.
     */
    @Nullable
    BigQueryChildJob selectedJob() {
        int row = getSelectedRow();
        return row >= 0 && row < jobs.getRowCount() ? jobs.job(row) : null;
    }

    /**
     * The time range the bars are drawn on.
     */
    @NotNull
    StatementTimeline statementTimeline() {
        return timeline;
    }

    /**
     * The height showing the header and the rows, up to {@code maxRows} of them.
     */
    int fittedHeight(int maxRows) {
        return getTableHeader().getPreferredSize().height + Math.min(getRowCount(), maxRows) * getRowHeight();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        contentWidths = null;
    }

    @Override
    public void doLayout() {
        if (getColumnCount() == 3) {
            if (contentWidths == null) {
                contentWidths = new int[]{
                        Math.min(JBUI.scale(MAX_STATEMENT_WIDTH), contentWidth(STATEMENT)), contentWidth(BYTES)};
            }
            int statement = contentWidths[0];
            int bytes = contentWidths[1];
            preferWidth(STATEMENT, statement);
            preferWidth(BYTES, bytes);
            preferWidth(TIMELINE, Math.max(0, getWidth() - statement - bytes));
        }
        super.doLayout();
    }

    private void preferWidth(int column, int width) {
        TableColumn tableColumn = getColumnModel().getColumn(column);
        if (tableColumn.getPreferredWidth() != width) tableColumn.setPreferredWidth(width);
    }

    private int contentWidth(int column) {
        TableColumn tableColumn = getColumnModel().getColumn(column);
        TableCellRenderer headerRenderer = tableColumn.getHeaderRenderer();
        int width = headerRenderer == null ? 0 : headerRenderer
                .getTableCellRendererComponent(this, tableColumn.getHeaderValue(), false, false, -1, column)
                .getPreferredSize().width;
        for (int row = 0; row < getRowCount(); row++) {
            width = Math.max(width, prepareRenderer(getCellRenderer(row, column), row, column).getPreferredSize().width);
        }
        return width + JBUI.scale(COLUMN_PADDING);
    }

    private int rowToSelect(@Nullable String previousJobId) {
        int count = jobs.getRowCount();
        if (count == 0) return -1;
        for (int row = 0; previousJobId != null && row < count; row++) {
            if (previousJobId.equals(jobs.job(row).jobId())) return row;
        }
        for (int row = 0; row < count; row++) {
            if (FAILED.equals(jobs.job(row).status())) return row;
        }
        return count - 1;
    }

    @NotNull
    private TableCellRenderer aligned(int alignment) {
        return (table, value, selected, focused, row, column) -> {
            Component header = getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (header instanceof JLabel label) label.setHorizontalAlignment(alignment);
            return header;
        };
    }

    private static final class JobsModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"", "", "Bytes"};

        private List<BigQueryChildJob> jobs = List.of();

        private void show(@NotNull List<BigQueryChildJob> shown) {
            jobs = List.copyOf(shown);
            fireTableDataChanged();
        }

        @NotNull
        private BigQueryChildJob job(int row) {
            return jobs.get(row);
        }

        @Override
        public int getRowCount() {
            return jobs.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            BigQueryChildJob job = jobs.get(row);
            return column == BYTES ? Utils.formatBytes(job.bytesProcessed()) : job;
        }
    }

    private static final class StatementRenderer extends ColoredTableCellRenderer {

        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected,
                                             boolean hasFocus, int row, int column) {
            if (!(value instanceof BigQueryChildJob job)) return;
            setIpad(JBUI.insets(0, COLUMN_PADDING, 0, 0));
            setIcon(ActionStateIcons.ofJob(job.status()));
            append(String.valueOf(row + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("  ");
            append(RunText.statementKind(job.statementType(), job.query()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            String tableName = RunText.tableName(job.target());
            if (tableName != null) append("  " + tableName, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            String tooltip = String.join("\n", Stream.of(job.target(), job.errorMessage())
                    .filter(Objects::nonNull).filter(text -> !text.isBlank()).toList());
            setToolTipText(tooltip.isEmpty() ? null : UiText.htmlTooltip(tooltip));
        }
    }

    private static final class BytesRenderer extends ColoredTableCellRenderer {

        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected,
                                             boolean hasFocus, int row, int column) {
            setTextAlign(SwingConstants.RIGHT);
            setIpad(JBUI.insets(0, COLUMN_PADDING, 0, COLUMN_PADDING));
            append(String.valueOf(value), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
