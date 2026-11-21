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
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageService;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.dataplex.TraceEntry;
import io.github.rejeb.dataform.language.lineage.dataplex.TraceGroups;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.LatestRequest;
import io.github.rejeb.dataform.language.ui.SegmentedSwitch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The Trace tab: everything recorded upstream or downstream of the selected table, up to five
 * levels away, whether it is on the graph or not, grouped by project. A click on an entity selects
 * it on the graph, loading its lineage first when it is not there. The trace is read off the event
 * dispatch thread; a newer selection discards the answer of an older one.
 */
public final class TracePanel extends JPanel {

    private static final String LOADING = "Loading…";
    private static final String HINT = "Up to 5 levels, including what is not on the graph yet";

    private final Supplier<DataplexLineageService> service;
    private final LatestRequest reads;
    private final Runnable showCatalog;
    private final CollectionListModel<Row> rows = new CollectionListModel<>();
    private final JBList<Row> list = new JBList<>(rows);
    private final JBLabel subject = new JBLabel();
    private final JBLabel hint = new JBLabel(HINT);
    private final SegmentedSwitch<Direction> directionSwitch;
    private final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private @NotNull LineageToolWindowState state = LineageToolWindowState.NO_EDITOR;
    private @Nullable LineageModel shownModel;
    private @Nullable DataplexView shownView;
    private @Nullable String shownNode;
    private @Nullable Direction shownDirection;

    /**
     * @param service     reads the recorded lineage
     * @param executor    runs those reads off the event dispatch thread
     * @param showCatalog opens the lineage editor on the Knowledge Catalog source
     */
    public TracePanel(@NotNull Supplier<DataplexLineageService> service, @NotNull Executor executor,
                      @NotNull Runnable showCatalog) {
        super(new BorderLayout());
        this.service = service;
        this.reads = new LatestRequest(executor,
                update -> ApplicationManager.getApplication().invokeLater(update, ModalityState.nonModal()));
        this.showCatalog = showCatalog;
        directionSwitch = new SegmentedSwitch<>(List.of(Direction.UPSTREAM, Direction.DOWNSTREAM),
                direction -> direction == Direction.UPSTREAM ? "Upstream" : "Downstream", Direction.UPSTREAM,
                direction -> show(shownModel));
        directionSwitch.setCompact(true);
        directionSwitch.getAccessibleContext().setAccessibleName("Trace direction");

        hint.setFont(JBFont.medium());
        hint.setForeground(UIUtil.getContextHelpForeground());
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(4));
        header.add(subject);
        header.add(directionSwitch);
        header.add(hint);
        header.setVisible(false);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new RowRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(@NotNull MouseEvent e) {
                int row = list.locationToIndex(e.getPoint());
                if (e.getButton() == MouseEvent.BUTTON1 && row >= 0 && list.getCellBounds(row, row).contains(e.getPoint())) {
                    open(row);
                }
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "trace.open");
        list.getActionMap().put("trace.open", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                open(list.getSelectedIndex());
            }
        });

        add(header, BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER);
        show(null);
    }

    /**
     * Shows the trace of the table selected in the model, or of the table the selected link writes,
     * or why there is none to show.
     *
     * @param model the model of the lineage editor the tool window follows, {@code null} without one
     */
    public void show(@Nullable LineageModel model) {
        shownModel = model;
        LineageToolWindowState asked = LineageToolWindowState.of(model, null);
        DataplexView view = model == null ? null : model.dataplexView();
        if (asked != LineageToolWindowState.READY || view == null) {
            forget();
            state = asked;
            LineageToolWindowEmptyStates.show(list.getEmptyText(), asked, showCatalog);
            return;
        }
        LinkRef link = model.selectedLink();
        String nodeId = link != null ? link.toId() : model.selectedId();
        Direction direction = directionSwitch.selected();
        if (nodeId == null || view == shownView && nodeId.equals(shownNode) && direction == shownDirection) return;
        forget();
        shownView = view;
        shownNode = nodeId;
        shownDirection = direction;
        state = LineageToolWindowState.READY;
        LineageNode node = model.graph().node(nodeId);
        String name = node == null ? nodeId : node.name();
        subject.setText("Trace of " + name);
        header.setVisible(true);
        EmptyStates.show(list.getEmptyText(), LOADING);
        String none = "Nothing " + (direction == Direction.UPSTREAM ? "upstream" : "downstream") + " of " + name
                + " was recorded in the " + view.range().label().toLowerCase(Locale.ROOT);
        read(model, view, nodeId, direction, none);
    }

    /**
     * @return what the tab shows
     */
    public @NotNull LineageToolWindowState state() {
        return state;
    }

    /**
     * @return the line above the list naming the traced table, empty while none is
     */
    public @NotNull String header() {
        return header.isVisible() ? subject.getText() : "";
    }

    /**
     * @return the note next to the direction switch
     */
    public @NotNull String hint() {
        return hint.getText();
    }

    /**
     * @return the Upstream | Downstream switch
     */
    public @NotNull SegmentedSwitch<Direction> directionSwitch() {
        return directionSwitch;
    }

    /**
     * @return the rows as shown, a group as "project (count)" and an entity as "name · container · Level n"
     */
    public @NotNull List<String> rowTexts() {
        return rows.getItems().stream().map(Row::text).toList();
    }

    /**
     * @return the text shown in place of the rows
     */
    public @NotNull StatusText emptyStatus() {
        return list.getEmptyText();
    }

    /**
     * Selects the entity of a row on the graph, asking for its lineage when it is not there. The
     * row of a group does nothing.
     */
    public void open(int row) {
        LineageModel model = shownModel;
        if (model == null || row < 0 || row >= rows.getSize()) return;
        TraceEntry entry = rows.getElementAt(row).entry();
        if (entry != null) model.requestNode(EntityNames.nodeIdOf(entry.fqn()));
    }

    private void read(@NotNull LineageModel model, @NotNull DataplexView view, @NotNull String nodeId,
                      @NotNull Direction direction, @NotNull String none) {
        reads.submit(() -> service.get().trace(view, nodeId, direction),
                answer -> {
                    List<Row> shown = new ArrayList<>();
                    TraceGroups.group(answer, view.scope().projectId()).forEach((group, grouped) -> {
                        shown.add(new Row(group, grouped.size(), null));
                        grouped.forEach(entry -> shown.add(new Row(group, 0, entry)));
                    });
                    rows.replaceAll(shown);
                    EmptyStates.show(list.getEmptyText(), none);
                },
                RuntimeException.class,
                failed -> {
                    state = LineageToolWindowState.of(model, LineageToolWindowState.kindOf(failed));
                    header.setVisible(false);
                    EmptyStates.show(list.getEmptyText(), LineageToolWindowState.cannotAnswer(failed));
                });
    }

    private void forget() {
        reads.cancel();
        shownView = null;
        shownNode = null;
        shownDirection = null;
        header.setVisible(false);
        if (!rows.isEmpty()) rows.removeAll();
    }

    private record Row(@NotNull String group, int count, @Nullable TraceEntry entry) {

        private @NotNull String text() {
            if (entry == null) return group + " (" + count + ")";
            return EntityNames.shortNameOf(entry.fqn()) + " · " + EntityNames.containerOf(entry.fqn())
                    + " · Level " + entry.level();
        }
    }

    private static final class RowRenderer extends ColoredListCellRenderer<Row> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends Row> list, Row row, int index, boolean selected,
                                             boolean hasFocus) {
            TraceEntry entry = row.entry();
            if (entry == null) {
                append(row.group(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                append("  " + row.count(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                return;
            }
            setIpad(JBUI.insetsLeft(16));
            append(EntityNames.shortNameOf(entry.fqn()));
            append("  " + EntityNames.containerOf(entry.fqn()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("  Level " + entry.level(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
        }
    }
}
