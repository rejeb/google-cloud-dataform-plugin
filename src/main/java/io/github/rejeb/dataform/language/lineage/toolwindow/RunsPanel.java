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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.RunConfigUiUtils;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessRun;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Runs tab: the latest runs that wrote the selected table, or the runs of the selected link,
 * read from Knowledge Catalog off the event dispatch thread. A newer selection discards the answer
 * of an older one.
 */
public final class RunsPanel extends JPanel {

    private static final String LOADING = "Loading…";
    private static final int[] WIDTHS = {28, 130, 80, 180, 0, 130};

    private final Supplier<DataplexLineageService> service;
    private final Executor executor;
    private final Runnable showCatalog;
    private final Consumer<String> urlOpener;
    private final RunsTableModel rows;
    private final JBTable table;
    private final JBLabel subject = new JBLabel();
    private final JBLabel range = new JBLabel();
    private final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private @NotNull LineageToolWindowState state = LineageToolWindowState.NO_EDITOR;
    private @Nullable DataplexView shownView;
    private @Nullable LinkRef shownLink;
    private @Nullable String shownNode;
    private long generation;

    /**
     * @param service     reads the recorded runs
     * @param executor    runs those reads off the event dispatch thread
     * @param clock       the current time and the zone the start times are shown in
     * @param showCatalog opens the lineage editor on the Knowledge Catalog source
     * @param urlOpener   opens a page of the Google Cloud console
     */
    public RunsPanel(@NotNull Supplier<DataplexLineageService> service, @NotNull Executor executor, @NotNull Clock clock,
                     @NotNull Runnable showCatalog, @NotNull Consumer<String> urlOpener) {
        super(new BorderLayout());
        this.service = service;
        this.executor = executor;
        this.showCatalog = showCatalog;
        this.urlOpener = urlOpener;
        this.rows = new RunsTableModel(clock);
        this.table = new JBTable(rows);

        range.setFont(JBFont.medium());
        range.setForeground(UIUtil.getContextHelpForeground());
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(6, 4));
        header.add(subject);
        header.add(range);
        header.setVisible(false);

        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setStriped(false);
        table.getTableHeader().setReorderingAllowed(false);
        for (int column = 0; column < WIDTHS.length; column++) {
            if (WIDTHS[column] == 0) continue;
            TableColumn sized = table.getColumnModel().getColumn(column);
            sized.setPreferredWidth(JBUI.scale(WIDTHS[column]));
            sized.setMaxWidth(JBUI.scale(column == RunsTableModel.STATE ? WIDTHS[column] : WIDTHS[column] * 2));
        }
        table.getColumnModel().getColumn(RunsTableModel.STATE).setCellRenderer(new StateRenderer());
        table.getColumnModel().getColumn(RunsTableModel.JOB).setCellRenderer(new JobRenderer());
        JobLinkListener links = new JobLinkListener();
        table.addMouseListener(links);
        table.addMouseMotionListener(links);

        add(header, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);
        show(null);
    }

    /**
     * Shows the runs of what is selected in the model, or why there is none to show.
     *
     * @param model the model of the lineage editor the tool window follows, {@code null} without one
     */
    public void show(@Nullable LineageModel model) {
        LineageToolWindowState asked = LineageToolWindowState.of(model, null);
        DataplexView view = model == null ? null : model.dataplexView();
        if (asked != LineageToolWindowState.READY || view == null) {
            forget();
            state = asked;
            LineageToolWindowEmptyStates.show(table.getEmptyText(), asked, showCatalog);
            return;
        }
        LinkRef link = model.selectedLink();
        String nodeId = model.selectedId();
        if (view == shownView && Objects.equals(link, shownLink) && Objects.equals(nodeId, shownNode)) return;
        forget();
        shownView = view;
        shownLink = link;
        shownNode = nodeId;
        state = LineageToolWindowState.READY;
        String period = view.range().label().toLowerCase(Locale.ROOT);
        String title;
        String none;
        if (link != null) {
            String from = nameOf(model, link.fromId());
            String to = nameOf(model, link.toId());
            title = "Runs of the link " + from + " → " + to;
            none = "No run read " + from + " to write " + to + " in the " + period;
        } else {
            String name = nameOf(model, Objects.requireNonNull(nodeId));
            title = "Runs that wrote " + name;
            none = "No run wrote " + name + " in the " + period;
        }
        subject.setText(title);
        range.setText(view.range().label());
        header.setVisible(true);
        EmptyStates.show(table.getEmptyText(), LOADING);
        read(model, view, link, nodeId, none);
    }

    /**
     * @return what the tab shows
     */
    public @NotNull LineageToolWindowState state() {
        return state;
    }

    /**
     * @return the line above the table naming whose runs are listed, empty while none is
     */
    public @NotNull String header() {
        return header.isVisible() ? subject.getText() : "";
    }

    /**
     * @return the time range shown next to the header, empty while no run is listed
     */
    public @NotNull String rangeText() {
        return header.isVisible() ? range.getText() : "";
    }

    /**
     * @return the rows of the table
     */
    public @NotNull RunsTableModel rows() {
        return rows;
    }

    /**
     * @return the text shown in place of the rows
     */
    public @NotNull StatusText emptyStatus() {
        return table.getEmptyText();
    }

    /**
     * Opens the latest BigQuery job of the process of a row, when it reports one.
     */
    public void openJob(int row) {
        DataplexView view = shownView;
        String jobId = row < 0 || row >= rows.getRowCount() ? null : rows.row(row).jobId();
        if (view == null || jobId == null) return;
        urlOpener.accept(RunConfigUiUtils.bigQueryJobUrl(view.scope().projectId(), view.scope().location(), jobId));
    }

    private void read(@NotNull LineageModel model, @NotNull DataplexView view, @Nullable LinkRef link,
                      @Nullable String nodeId, @NotNull String none) {
        long asked = ++generation;
        executor.execute(() -> {
            List<ProcessRun> runs = List.of();
            RuntimeException failure = null;
            try {
                runs = link != null ? service.get().runsOfLink(view, link.fromId(), link.toId())
                        : service.get().runsWriting(view, Objects.requireNonNull(nodeId));
            } catch (RuntimeException e) {
                failure = e;
            }
            List<ProcessRun> answer = runs;
            RuntimeException failed = failure;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (asked != generation) return;
                if (failed != null) {
                    state = LineageToolWindowState.of(model, LineageToolWindowState.kindOf(failed));
                    header.setVisible(false);
                    EmptyStates.show(table.getEmptyText(), LineageToolWindowState.cannotAnswer(failed));
                    return;
                }
                rows.setRows(answer.stream().map(RunRow::of).toList());
                EmptyStates.show(table.getEmptyText(), none);
            }, ModalityState.nonModal());
        });
    }

    private void forget() {
        generation++;
        shownView = null;
        shownLink = null;
        shownNode = null;
        header.setVisible(false);
        if (rows.getRowCount() > 0) rows.setRows(List.of());
    }

    private static @NotNull String nameOf(@NotNull LineageModel model, @NotNull String nodeId) {
        LineageNode node = model.graph().node(nodeId);
        return node == null ? nodeId : node.name();
    }

    private static @NotNull Icon iconOf(@NotNull RecordedRun.State state) {
        return switch (state) {
            case COMPLETED -> DataformIcons.STATUS_SUCCEEDED;
            case FAILED -> DataformIcons.STATUS_FAILED;
            case ABORTED -> DataformIcons.STATUS_CANCELLED;
            case STARTED -> DataformIcons.STATUS_PENDING;
            case UNKNOWN -> DataformIcons.STATUS_SKIPPED;
        };
    }

    private static @NotNull String textOf(@NotNull RecordedRun.State state) {
        return switch (state) {
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
            case ABORTED -> "Aborted";
            case STARTED -> "Running";
            case UNKNOWN -> "Unknown state";
        };
    }

    private static final class StateRenderer extends ColoredTableCellRenderer {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected,
                                             boolean hasFocus, int row, int column) {
            if (!(value instanceof RecordedRun.State run)) return;
            setIcon(iconOf(run));
            setToolTipText(textOf(run));
            getAccessibleContext().setAccessibleName(textOf(run));
        }
    }

    private static final class JobRenderer extends ColoredTableCellRenderer {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected,
                                             boolean hasFocus, int row, int column) {
            if (value == null) return;
            append(value.toString(), selected ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES);
        }
    }

    private final class JobLinkListener extends MouseAdapter {
        @Override
        public void mouseClicked(@NotNull MouseEvent e) {
            int row = linkRowAt(e.getPoint());
            if (row >= 0 && e.getButton() == MouseEvent.BUTTON1) openJob(row);
        }

        @Override
        public void mouseMoved(@NotNull MouseEvent e) {
            table.setCursor(linkRowAt(e.getPoint()) >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
        }

        @Override
        public void mouseExited(@NotNull MouseEvent e) {
            table.setCursor(Cursor.getDefaultCursor());
        }

        private int linkRowAt(@NotNull Point point) {
            int row = table.rowAtPoint(point);
            int column = table.columnAtPoint(point);
            if (row < 0 || column < 0 || table.convertColumnIndexToModel(column) != RunsTableModel.JOB) return -1;
            int modelRow = table.convertRowIndexToModel(row);
            return rows.row(modelRow).jobId() == null ? -1 : modelRow;
        }
    }
}
