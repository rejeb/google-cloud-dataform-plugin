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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import io.github.rejeb.dataform.language.gcp.settings.GcpRepositorySettings;
import io.github.rejeb.dataform.language.ui.ReadOnlyEditors;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

import static io.github.rejeb.dataform.language.util.Utils.formatBytes;
import static io.github.rejeb.dataform.language.util.Utils.formatSql;

public class ActionDetailsTabPanel extends JPanel implements Disposable {

    private static final Logger LOG = Logger.getInstance(ActionDetailsTabPanel.class);


    private static final String CARD_LOADING = "loading";
    private static final String CARD_CONTENT = "content";
    private static final String CARD_EMPTY = "empty";

    private static final int MAX_SQL_HEIGHT = JBUIScale.scale(120);
    private static final int MIN_ROW_HEIGHT = JBUIScale.scale(28);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JPanel contentPanel;
    private final DataformGcpService service;
    private final Project project;

    private final List<EditorEx> sqlEditors = new ArrayList<>();
    private int loadGeneration = 0;

    public ActionDetailsTabPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.service = DataformGcpService.getInstance(project);

        JPanel loadingCard = new JPanel(new GridBagLayout());
        loadingCard.setBackground(UIUtil.getPanelBackground());
        loadingCard.add(new JBLabel(AnimatedIcon.Big.INSTANCE));
        cards.add(loadingCard, CARD_LOADING);

        JPanel emptyCard = new JPanel(new GridBagLayout());
        emptyCard.setBackground(UIUtil.getPanelBackground());
        emptyCard.add(new JBLabel("No details for this action."));
        cards.add(emptyCard, CARD_EMPTY);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIUtil.getPanelBackground());
        cards.add(new JBScrollPane(contentPanel), CARD_CONTENT);

        cardLayout.show(cards, CARD_EMPTY);
        add(cards, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {
        releaseSqlEditors();
    }

    private void releaseSqlEditors() {
        sqlEditors.forEach(ReadOnlyEditors::release);
        sqlEditors.clear();
    }

    public void load(@NotNull InvocationActionResult action) {
        if (action.jobId() == null || action.jobProject() == null) {
            cardLayout.show(cards, CARD_EMPTY);
            return;
        }
        int gen = ++loadGeneration;
        cardLayout.show(cards, CARD_LOADING);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String location = resolveLocation(action);
            BigQueryJobDetails details;
            try {
                details = service.getJobDetails(action.jobId(), action.jobProject(), location);
            } catch (Exception e) {
                LOG.warn("Failed to load BigQuery job details for jobId=" + action.jobId()
                        + " project=" + action.jobProject() + " location=" + location, e);
                details = null;
            }
            BigQueryJobDetails result = details;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (gen != loadGeneration) return;
                render(preFormatSql(result));
            });
        });
    }

    @Nullable
    private String resolveLocation(@NotNull InvocationActionResult action) {
        if (action.jobProject() != null && action.jobDataset() != null) {
            String datasetLocation =
                    service.resolveDatasetLocation(action.jobProject(), action.jobDataset());
            if (datasetLocation != null) return datasetLocation;
        }
        if (action.jobLocation() != null && !action.jobLocation().isBlank()) return action.jobLocation();
        String configured = GcpRepositorySettings.getInstance(project).getLocation();
        if (configured != null && !configured.isBlank()) return configured;
        LOG.warn("No location could be resolved for action " + action.target()
                + ", letting BigQuery resolve it.");
        return null;
    }

    @Nullable
    private BigQueryJobDetails preFormatSql(@Nullable BigQueryJobDetails details) {
        if (details == null) return null;
        List<BigQueryChildJob> formattedJobs = details.childJobs().stream()
                .map(this::formatChildJobSql)
                .toList();
        return details.withChildJobs(formattedJobs);
    }

    @NotNull
    private BigQueryChildJob formatChildJobSql(@NotNull BigQueryChildJob job) {
        if (job.query() == null) return job;
        return job.withQuery(formatSql(project, job.query()));
    }

    private void render(@Nullable BigQueryJobDetails details) {
        releaseSqlEditors();
        contentPanel.removeAll();
        if (details == null) {
            cardLayout.show(cards, CARD_EMPTY);
            cards.revalidate();
            cards.repaint();
            return;
        }
        contentPanel.add(buildMetaPanel(details));
        contentPanel.add(Box.createVerticalStrut(8));
        if (!details.childJobs().isEmpty()) {
            JBLabel childTitle = new JBLabel("Jobs");
            childTitle.setFont(childTitle.getFont().deriveFont(Font.BOLD));
            childTitle.setBorder(JBUI.Borders.empty(8, 12, 4, 0));
            childTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(childTitle);
            contentPanel.add(buildChildJobsPanel(details.childJobs()));
        }
        contentPanel.add(Box.createVerticalStrut(20));
        cardLayout.show(cards, CARD_CONTENT);
        cards.revalidate();
        cards.repaint();
    }

    private JPanel buildMetaPanel(@NotNull BigQueryJobDetails d) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        panel.setBorder(JBUI.Borders.empty(8, 12));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));

        String urlText = d.jobId();
        String bqUrl = RunConfigUiUtils.bigQueryJobUrl(d.project(), d.location(), d.jobId());

        String[][] rows = {
                {"Job ID", d.jobId()},
                {"Status", d.status()},
                {"Failure reason", d.errorMessage()},
                {"Project", d.project()},
                {"Location", d.location()},
                {"Bytes processed", formatBytes(d.bytesProcessed())},
                {"Bytes billed", formatBytes(d.bytesBilled())},
                {"Duration", RunConfigUiUtils.formatDuration(d.startTime(), d.endTime())},
                {"Statements processed", d.statementsProcessed() != null
                        ? String.valueOf(d.statementsProcessed()) : "—"},
        };

        Component[] values = new Component[rows.length];
        JBLabel link = new JBLabel();
        RunConfigUiUtils.setLink(link, urlText, bqUrl);
        values[0] = link;
        for (int i = 1; i < rows.length; i++) {
            values[i] = RunConfigUiUtils.selectableValue(rows[i][1]);
        }
        RunConfigUiUtils.addKeyValueRows(panel, Arrays.stream(rows).map(row -> row[0]).toArray(String[]::new),
                values, true);
        return panel;
    }

    private JPanel buildChildJobsPanel(@NotNull List<BigQueryChildJob> jobs) {
        String[] columns = {"", "Start date", "End date", "SQL Query", "Bytes processed"};

        List<EditorEx> rowEditors = jobs.stream()
                .map(j -> createSqlEditor(j.query() != null ? j.query() : "", project))
                .toList();
        sqlEditors.addAll(rowEditors);

        Object[][] data = jobs.stream().map(j -> new Object[]{
                j.status(),
                j.startTime() != null ? RunConfigUiUtils.DATE_TIME.format(j.startTime()) : "—",
                j.endTime() != null ? RunConfigUiUtils.DATE_TIME.format(j.endTime()) : "—",
                j.query() != null ? j.query() : "",
                formatBytes(j.bytesProcessed())
        }).toArray(Object[][]::new);

        DefaultTableModel model = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return c != 0;
            }
        };

        JBTable table = new JBTable(model) {
            private final TableCellEditor readOnlyEditor = ReadOnlyTextFields.cellEditor(this);

            @Override
            public TableCellRenderer getCellRenderer(int row, int col) {
                if (col == 0) return new StatusIconRenderer();
                if (col == 3) return new SqlEditorRenderer(rowEditors.get(row));
                return super.getCellRenderer(row, col);
            }

            @Override
            public TableCellEditor getCellEditor(int row, int col) {
                if (col == 3) return new SqlScrollEditor(rowEditors.get(row), this, row);
                return readOnlyEditor;
            }
        };

        table.setShowGrid(true);
        table.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        table.setGridColor(UIUtil.getTableGridColor());
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setRowHeight(MIN_ROW_HEIGHT);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(false);
        table.setStriped(false);
        table.setSurrendersFocusOnKeystroke(true);

        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (col == 3 && row >= 0) {
                    if (!table.isEditing() || table.getEditingRow() != row) {
                        if (table.isEditing()) table.removeEditor();
                        table.editCellAt(row, 3);
                    }
                } else {
                    if (table.isEditing() && table.getEditingColumn() == 3) {
                        table.removeEditor();
                    }
                }
            }
        });

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        sizeFixedColumns(table, columns.length, 3);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        wrapper.add(table.getTableHeader(), BorderLayout.NORTH);
        wrapper.add(table, BorderLayout.CENTER);

        wrapper.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int totalWidth = wrapper.getWidth();
                if (totalWidth <= 0) return;
                int fixedWidth = 0;
                for (int col = 0; col < table.getColumnCount(); col++) {
                    if (col != 3) fixedWidth += table.getColumnModel().getColumn(col).getWidth();
                }
                int sqlWidth = totalWidth - fixedWidth - table.getIntercellSpacing().width * (table.getColumnCount() - 1);
                if (sqlWidth > 0) {
                    TableColumn sqlCol = table.getColumnModel().getColumn(3);
                    sqlCol.setPreferredWidth(sqlWidth);
                    sqlCol.setWidth(sqlWidth);
                }
                SwingUtilities.invokeLater(() -> computeRowHeights(table, rowEditors));
            }
        });

        return wrapper;
    }

    private static void computeRowHeights(@NotNull JBTable table,
                                          @NotNull List<EditorEx> editors) {
        int colWidth = table.getColumnModel().getColumn(3).getWidth();
        if (colWidth <= 0) return;
        for (int row = 0; row < editors.size(); row++) {
            EditorEx editor = editors.get(row);
            String sql = editor.getDocument().getText();
            Font editorFont = editor.getColorsScheme()
                    .getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
            JTextArea probe = new JTextArea(sql);
            probe.setFont(editorFont);
            probe.setLineWrap(true);
            probe.setWrapStyleWord(true);
            probe.setSize(colWidth - JBUIScale.scale(8), Integer.MAX_VALUE);
            int natural = probe.getPreferredSize().height + JBUIScale.scale(8);
            table.setRowHeight(row, Math.clamp(natural, MIN_ROW_HEIGHT, MAX_SQL_HEIGHT));
        }
    }

    private static void sizeFixedColumns(@NotNull JBTable table, int colCount, int expandCol) {
        for (int col = 0; col < colCount; col++) {
            if (col == expandCol) continue;
            TableColumn tc = table.getColumnModel().getColumn(col);
            TableCellRenderer hr = table.getTableHeader().getDefaultRenderer();
            int max = hr.getTableCellRendererComponent(
                            table, tc.getHeaderValue(), false, false, -1, col)
                    .getPreferredSize().width + JBUIScale.scale(16);
            for (int row = 0; row < table.getRowCount(); row++) {
                Component c = table.getCellRenderer(row, col)
                        .getTableCellRendererComponent(
                                table, table.getValueAt(row, col), false, false, row, col);
                max = Math.max(max, c.getPreferredSize().width + JBUIScale.scale(16));
            }
            tc.setPreferredWidth(max);
            tc.setMinWidth(max);
            tc.setMaxWidth(max);
        }
    }

    private static final class StatusIconRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int col) {
            Component base = super.getTableCellRendererComponent(
                    table, "", selected, focused, row, col);
            JBLabel label = new JBLabel();
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(((JComponent) base).getBackground());
            String status = value != null ? value.toString() : "";
            label.setIcon(switch (status) {
                case "DONE" -> AllIcons.RunConfigurations.TestPassed;
                case "RUNNING" -> AnimatedIcon.Default.INSTANCE;
                default -> AllIcons.RunConfigurations.TestError;
            });
            label.setToolTipText(status);
            return label;
        }
    }

    private static final class SqlEditorRenderer implements TableCellRenderer {

        private final EditorEx editor;

        SqlEditorRenderer(@NotNull EditorEx editor) {
            this.editor = editor;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int col) {
            return editor.getComponent();
        }
    }

    private static final class SqlScrollEditor extends AbstractCellEditor
            implements TableCellEditor {

        private final EditorEx editor;
        private final JBTable table;
        private final int targetRow;

        SqlScrollEditor(@NotNull EditorEx editor, @NotNull JBTable table, int targetRow) {
            this.editor = editor;
            this.table = table;
            this.targetRow = targetRow;
        }

        @Override
        public Object getCellEditorValue() {
            return editor.getDocument().getText();
        }

        @Override
        public boolean isCellEditable(java.util.EventObject e) {
            return true;
        }

        @Override
        public boolean shouldSelectCell(java.util.EventObject e) {
            return false;
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable t, Object value, boolean selected, int row, int col) {
            int rowHeight = table.getRowHeight(targetRow);
            int colWidth = table.getColumnModel().getColumn(3).getWidth();
            boolean needsScroll = needsVerticalScroll(
                    editor.getDocument().getText(), editor, colWidth, rowHeight);

            JBScrollPane sp = new JBScrollPane(editor.getComponent());
            sp.setBorder(JBUI.Borders.empty());
            sp.setVerticalScrollBarPolicy(needsScroll
                    ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                    : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            return sp;
        }

        private static boolean needsVerticalScroll(@NotNull String sql,
                                                   @NotNull EditorEx editor,
                                                   int colWidth, int rowHeight) {
            Font editorFont = editor.getColorsScheme()
                    .getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
            JTextArea probe = new JTextArea(sql);
            probe.setFont(editorFont);
            probe.setLineWrap(true);
            probe.setWrapStyleWord(true);
            probe.setSize(colWidth > 0 ? colWidth : 400, Integer.MAX_VALUE);
            return probe.getPreferredSize().height > rowHeight;
        }
    }

    @NotNull
    private static EditorEx createSqlEditor(@NotNull String sql, @NotNull Project project) {
        EditorEx editor = ReadOnlyEditors.compactSql(project, sql, false);
        editor.setBorder(JBUI.Borders.empty());
        return editor;
    }
}
