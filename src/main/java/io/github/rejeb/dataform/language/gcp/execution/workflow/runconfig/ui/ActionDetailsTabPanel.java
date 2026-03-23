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
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static io.github.rejeb.dataform.language.util.Utils.formatSql;

public class ActionDetailsTabPanel extends JPanel implements Disposable {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String CARD_LOADING = "loading";
    private static final String CARD_CONTENT = "content";
    private static final String CARD_EMPTY = "empty";

    private static final int MAX_SQL_HEIGHT = JBUI.scale(120);
    private static final int MIN_ROW_HEIGHT = JBUI.scale(28);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JPanel contentPanel;
    private final DataformGcpService service;
    private final Project project;

    private final List<EditorEx> sqlEditors = new ArrayList<>();

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
        sqlEditors.forEach(e -> {
            if (!e.isDisposed()) EditorFactory.getInstance().releaseEditor(e);
        });
        sqlEditors.clear();
    }


    public void load(@NotNull InvocationActionResult action) {
        if (action.jobId() == null || action.jobProject() == null) {
            cardLayout.show(cards, CARD_EMPTY);
            return;
        }
        cardLayout.show(cards, CARD_LOADING);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            BigQueryJobDetails details = service.getJobDetails(
                    action.jobId(),
                    action.jobProject(),
                    action.jobLocation() != null ? action.jobLocation() : "US"
            );
            BigQueryJobDetails formatted = preFormatSql(details);
            SwingUtilities.invokeLater(() -> render(formatted));
        });
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
        String[] result = {job.query()};
        ApplicationManager.getApplication().invokeAndWait(
                () -> result[0] = formatSql(project, job.query())
        );
        return job.withQuery(result[0]);
    }

    private void render(@Nullable BigQueryJobDetails details) {
        releaseSqlEditors();
        contentPanel.removeAll();
        if (details == null) {
            cardLayout.show(cards, CARD_EMPTY);
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
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel buildMetaPanel(@NotNull BigQueryJobDetails d) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        panel.setBorder(JBUI.Borders.empty(8, 12));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));

        String urlText = d.jobId();
        String bqUrl = buildBigQueryJobUrl(d.project(), d.location(), d.jobId());

        String[][] rows = {
                {"Job ID", d.jobId()},
                {"Status", d.status()},
                {"Failure reason", d.errorMessage()},
                {"Project", d.project()},
                {"Location", d.location()},
                {"Bytes processed", formatBytes(d.bytesProcessed())},
                {"Bytes billed", formatBytes(d.bytesBilled())},
                {"Duration", formatDuration(d.startTime(), d.endTime())},
                {"Statements processed", d.statementsProcessed() != null
                        ? String.valueOf(d.statementsProcessed()) : "—"},
        };

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

        for (int i = 0; i < rows.length; i++) {
            kc.gridy = vc.gridy = i;
            kc.gridx = 0;
            vc.gridx = 1;
            JBLabel key = new JBLabel(rows[i][0] + ":");
            key.setForeground(UIUtil.getLabelDisabledForeground());
            panel.add(key, kc);
            if ("Job ID".equals(rows[i][0])) {
                JBLabel link = new JBLabel("<html><a href=''>" + urlText + "</a></html>");
                link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                link.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        openUrl(bqUrl);
                    }
                });
                panel.add(link, vc);
            } else {
                panel.add(new JBLabel(rows[i][1] != null ? rows[i][1] : "—"), vc);
            }
        }

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = rows.length;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;
        panel.add(new JPanel(), filler);
        return panel;
    }

    private JPanel buildChildJobsPanel(@NotNull List<BigQueryChildJob> jobs) {
        String[] columns = {"", "Start date", "End date", "SQL Query", "Bytes processed"};

        // Un EditorEx par ligne — partagé entre renderer et editor hover
        List<EditorEx> rowEditors = jobs.stream()
                .map(j -> createSqlEditor(j.query() != null ? j.query() : "", project))
                .toList();
        sqlEditors.addAll(rowEditors);

        Object[][] data = jobs.stream().map(j -> new Object[]{
                j.status(),
                j.startTime() != null ? FMT.format(j.startTime()) : "—",
                j.endTime() != null ? FMT.format(j.endTime()) : "—",
                j.query() != null ? j.query() : "",
                formatBytes(j.bytesProcessed())
        }).toArray(Object[][]::new);

        DefaultTableModel model = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return c == 3;
            }
        };

        JBTable table = new JBTable(model) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int col) {
                if (col == 0) return new StatusIconRenderer();
                if (col == 3) return new SqlEditorRenderer(rowEditors.get(row));
                return super.getCellRenderer(row, col);
            }

            @Override
            public TableCellEditor getCellEditor(int row, int col) {
                if (col == 3) return new SqlScrollEditor(rowEditors.get(row), this, row);
                return super.getCellEditor(row, col);
            }
        };

        table.setShowGrid(true);
        table.setGridColor(UIUtil.getTableGridColor());
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setRowHeight(MIN_ROW_HEIGHT);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
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

        sizeFixedColumns(table, columns.length, 3);

        table.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && table.isShowing()) {
                SwingUtilities.invokeLater(() -> computeRowHeights(table, rowEditors));
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(table.getTableHeader(), BorderLayout.NORTH);
        wrapper.add(table, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Utilise un JTextArea probe (identique à l'original) pour un calcul
     * fiable de la hauteur, indépendamment du layout de l'EditorEx.
     */
    private static void computeRowHeights(@NotNull JBTable table,
                                          @NotNull List<EditorEx> editors) {
        int colWidth = table.getColumnModel().getColumn(3).getWidth();
        if (colWidth <= 0) return;
        for (int row = 0; row < editors.size(); row++) {
            String sql = editors.get(row).getDocument().getText();
            JTextArea probe = new JTextArea(sql);
            probe.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            probe.setLineWrap(true);
            probe.setWrapStyleWord(true);
            probe.setSize(colWidth - JBUI.scale(8), Integer.MAX_VALUE);
            int natural = probe.getPreferredSize().height + JBUI.scale(8);
            table.setRowHeight(row, Math.max(MIN_ROW_HEIGHT, Math.min(natural, MAX_SQL_HEIGHT)));
        }
    }

    private static void sizeFixedColumns(@NotNull JBTable table, int colCount, int expandCol) {
        for (int col = 0; col < colCount; col++) {
            if (col == expandCol) continue;
            TableColumn tc = table.getColumnModel().getColumn(col);
            TableCellRenderer hr = table.getTableHeader().getDefaultRenderer();
            int max = hr.getTableCellRendererComponent(
                            table, tc.getHeaderValue(), false, false, -1, col)
                    .getPreferredSize().width + JBUI.scale(16);
            for (int row = 0; row < table.getRowCount(); row++) {
                Component c = table.getCellRenderer(row, col)
                        .getTableCellRendererComponent(
                                table, table.getValueAt(row, col), false, false, row, col);
                max = Math.max(max, c.getPreferredSize().width + JBUI.scale(16));
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

    /**
     * Renderer : affiche l'EditorEx directement — coloration visible en permanence.
     */
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

    /**
     * Editor hover : enveloppe le même EditorEx dans un JBScrollPane interactif.
     * Pas de création d'un nouvel éditeur — réutilise celui du renderer.
     */
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
                    editor.getDocument().getText(), colWidth, rowHeight);

            JBScrollPane sp = new JBScrollPane(editor.getComponent());
            sp.setBorder(JBUI.Borders.empty());
            sp.setVerticalScrollBarPolicy(needsScroll
                    ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                    : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            return sp;
        }

        private static boolean needsVerticalScroll(@NotNull String sql,
                                                   int colWidth, int rowHeight) {
            JTextArea probe = new JTextArea(sql);
            probe.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            probe.setLineWrap(true);
            probe.setWrapStyleWord(true);
            probe.setSize(colWidth > 0 ? colWidth : 400, Integer.MAX_VALUE);
            return probe.getPreferredSize().height > rowHeight;
        }
    }

    @NotNull
    private static EditorEx createSqlEditor(@NotNull String sql, @NotNull Project project) {
        var document = EditorFactory.getInstance().createDocument(sql);
        var fileType = FileTypeManager.getInstance().getFileTypeByExtension("sql");
        EditorEx editor = (EditorEx) EditorFactory.getInstance()
                .createEditor(document, project, fileType, true);
        EditorSettings s = editor.getSettings();
        s.setLineNumbersShown(false);
        s.setFoldingOutlineShown(false);
        s.setLineMarkerAreaShown(false);
        s.setIndentGuidesShown(false);
        s.setVirtualSpace(false);
        s.setUseSoftWraps(true);
        editor.setHorizontalScrollbarVisible(false);
        editor.setVerticalScrollbarVisible(false);
        return editor;
    }

    @NotNull
    private static String buildBigQueryJobUrl(
            @NotNull String project, @NotNull String location, @NotNull String jobId) {
        return "https://console.cloud.google.com/bigquery"
                + "?project=" + project
                + "&j=bq:" + location + ":" + jobId
                + "&page=queryresults";
    }

    @NotNull
    private static String formatBytes(@Nullable Long bytes) {
        if (bytes == null || bytes < 0) return "—";
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824) return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    @NotNull
    private static String formatDuration(@Nullable java.time.Instant start,
                                         @Nullable java.time.Instant end) {
        if (start == null || end == null) return "—";
        java.time.Duration d = java.time.Duration.between(start, end);
        long h = d.toHours(), m = d.toMinutesPart(), s = d.toSecondsPart();
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    private static void openUrl(@NotNull String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
        }
    }
}