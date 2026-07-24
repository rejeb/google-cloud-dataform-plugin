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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.column.ColumnEdge;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paints the per-node column lists and the column-level lineage edges between them, and owns
 * the state they need: row bounds for hit testing, per-node scroll offsets, hover row and the
 * stable colour slot of each selected column.
 */
final class ColumnListRenderer {

    static final int COLUMN_ROW_H = 16;
    private static final int MAX_COLUMN_LIST_ROWS = 12;

    private final JComponent host;
    private final LineageModel model;
    private final CanvasViewport viewport;

    private final Map<String, Rectangle> rowBounds = new LinkedHashMap<>();
    private final Map<String, Rectangle> listBoundsByNode = new LinkedHashMap<>();
    private final Map<String, Integer> scrollByNode = new LinkedHashMap<>();
    private final Map<String, Integer> colorIndexByColumn = new LinkedHashMap<>();
    private @Nullable String hoverColumnId;

    ColumnListRenderer(@NotNull JComponent host, @NotNull LineageModel model,
                       @NotNull CanvasViewport viewport) {
        this.host = host;
        this.model = model;
        this.viewport = viewport;
    }

    @NotNull Map<String, Rectangle> listBoundsByNode() {
        return listBoundsByNode;
    }

    void clearRowBounds() {
        rowBounds.clear();
    }

    @Nullable String columnAt(double worldX, double worldY) {
        return CanvasHitTest.columnAt(rowBounds, worldX, worldY);
    }

    @Nullable String listNodeAt(double worldX, double worldY) {
        for (Map.Entry<String, Rectangle> entry : listBoundsByNode.entrySet()) {
            if (entry.getValue().contains(worldX, worldY)) return entry.getKey();
        }
        return null;
    }

    void scrollBy(@NotNull String nodeId, int amount) {
        scrollByNode.merge(nodeId, amount, Integer::sum);
    }

    /** Sets the hovered row; returns {@code true} when it changed and a repaint is needed. */
    boolean setHoverColumn(@Nullable String columnId) {
        if (java.util.Objects.equals(hoverColumnId, columnId)) return false;
        hoverColumnId = columnId;
        return true;
    }

    int columnCountFor(@NotNull String tableNodeId) {
        ColumnLineageGraph cg = model.columnGraph();
        return cg == null ? 0 : cg.columnsForTable(tableNodeId).size();
    }

    /**
     * Vertical space to reserve below a single node for its column list, so the node beneath it
     * stays clear. Only reserved when a column is selected (the scoped list is visible), and
     * sized to the number of participating columns actually shown on that node.
     */
    int reservedHeightFor(@NotNull String nodeId) {
        ColumnLineageGraph cg = model.columnGraph();
        if (cg == null || model.selectedColumnId() == null) return 0;
        ColumnRef selectedRef = cg.column(model.selectedColumnId());
        String selectedNode = selectedRef != null ? selectedRef.tableNodeId() : null;
        int count;
        if (nodeId.equals(selectedNode)) {
            count = columnCountFor(nodeId);
        } else {
            count = 0;
            for (String columnId : model.highlightColumnLineage()) {
                ColumnRef ref = cg.column(columnId);
                if (ref != null && ref.tableNodeId().equals(nodeId)) count++;
            }
        }
        return count > 0 ? visibleRowCount(count) * COLUMN_ROW_H + 6 : 0;
    }

    /**
     * World-space bounds of a node's column list. Opens below the node when the viewport has
     * room; otherwise opens above so the list is not clipped at the bottom of the screen.
     */
    @NotNull Rectangle listWorldBounds(@NotNull NodePosition pos, int count, int nodeH) {
        int listHeight = visibleRowCount(count) * COLUMN_ROW_H;
        int x = (int) Math.round(pos.x());
        int top = opensAbove(pos, count, nodeH)
                ? (int) Math.round(pos.y()) - 2 - listHeight
                : (int) Math.round(pos.y()) + nodeH + 2;
        return new Rectangle(x, top, pos.width(), Math.max(listHeight, 1));
    }

    private boolean opensAbove(@NotNull NodePosition pos, int count, int nodeH) {
        double zoom = viewport.zoom();
        double listScreenHeight = visibleRowCount(count) * COLUMN_ROW_H * zoom;
        double spaceBelow = host.getHeight() - (viewport.offsetY() + (pos.y() + nodeH) * zoom);
        double spaceAbove = viewport.offsetY() + pos.y() * zoom;
        if (spaceBelow >= listScreenHeight) return false;
        return spaceAbove > spaceBelow;
    }

    private static int visibleRowCount(int count) {
        return Math.min(count, MAX_COLUMN_LIST_ROWS);
    }

    /**
     * Paints the column lists of the nodes that participate in the current selection, or of the
     * node whose columns were revealed by hover, plus the coloured lineage edges between rows.
     */
    void paint(@NotNull Graphics2D g2, @NotNull LayoutResult layout, @Nullable String hoverExpandedNodeId) {
        ColumnLineageGraph cg = model.columnGraph();
        if (cg == null) return;

        Map<String, List<ColumnRef>> byTable = columnsByTable(cg, hoverExpandedNodeId);
        if (byTable.isEmpty()) return;

        List<String> selectedIds = new ArrayList<>(model.selectedColumnIds());
        reconcileSelectionColors(selectedIds);
        Map<String, Set<String>> lineageBySelected = new LinkedHashMap<>();
        Map<String, Color> colorBySelected = new LinkedHashMap<>();
        for (String selectedId : selectedIds) {
            Set<String> lineage = new LinkedHashSet<>();
            lineage.add(selectedId);
            lineage.addAll(cg.upstream(selectedId));
            lineage.addAll(cg.downstream(selectedId));
            lineageBySelected.put(selectedId, lineage);
            colorBySelected.put(selectedId, LineageTheme.selectionColor(colorIndexByColumn.get(selectedId)));
        }

        listBoundsByNode.clear();
        for (Map.Entry<String, List<ColumnRef>> entry : byTable.entrySet()) {
            NodePosition pos = layout.positions().get(entry.getKey());
            if (pos == null) continue;
            paintList(g2, entry.getKey(), entry.getValue(), pos, layout.nodeH(),
                    selectedIds, lineageBySelected, colorBySelected);
        }

        for (ColumnEdge edge : cg.edges()) {
            Rectangle from = rowBounds.get(edge.from().id());
            Rectangle to = rowBounds.get(edge.to().id());
            if (from == null || to == null) continue;
            Color color = edgeOwnerColor(edge.from().id(), edge.to().id(),
                    selectedIds, lineageBySelected, colorBySelected);
            if (color == null) continue;
            drawColumnEdge(g2, from, to, color);
        }
    }

    /**
     * Columns to show per node: when a column is selected, every column on its lineage plus the
     * full list of the selected column's own table; otherwise the hovered node's full list.
     */
    private @NotNull Map<String, List<ColumnRef>> columnsByTable(@NotNull ColumnLineageGraph cg,
                                                                 @Nullable String hoverExpandedNodeId) {
        Set<String> highlight = model.highlightColumnLineage();
        Map<String, List<ColumnRef>> byTable = new LinkedHashMap<>();
        if (model.selectedColumnId() != null && !highlight.isEmpty()) {
            ColumnRef selectedRef = cg.column(model.selectedColumnId());
            String selectedNode = selectedRef != null ? selectedRef.tableNodeId() : null;
            for (String colId : highlight) {
                ColumnRef ref = cg.column(colId);
                if (ref == null || ref.tableNodeId().equals(selectedNode)) continue;
                byTable.computeIfAbsent(ref.tableNodeId(), k -> new ArrayList<>()).add(ref);
            }
            if (selectedNode != null) {
                byTable.computeIfAbsent(selectedNode, k -> new ArrayList<>())
                        .addAll(cg.columnsForTable(selectedNode));
            }
        } else if (hoverExpandedNodeId != null) {
            List<ColumnRef> columns = cg.columnsForTable(hoverExpandedNodeId);
            if (!columns.isEmpty()) {
                byTable.computeIfAbsent(hoverExpandedNodeId, k -> new ArrayList<>()).addAll(columns);
            }
        }
        return byTable;
    }

    private void paintList(@NotNull Graphics2D g2, @NotNull String nodeId,
                           @NotNull List<ColumnRef> columns, @NotNull NodePosition pos, int nodeH,
                           @NotNull List<String> selectedIds,
                           @NotNull Map<String, Set<String>> lineageBySelected,
                           @NotNull Map<String, Color> colorBySelected) {
        int total = columns.size();
        int visibleRows = visibleRowCount(total);
        int maxScroll = Math.max(0, total - visibleRows);
        int scroll = Math.max(0, Math.min(scrollByNode.getOrDefault(nodeId, 0), maxScroll));
        scrollByNode.put(nodeId, scroll);

        Rectangle listBounds = listWorldBounds(pos, total, nodeH);
        listBoundsByNode.put(nodeId, listBounds);

        Shape oldClip = g2.getClip();
        g2.clip(listBounds);
        for (int row = 0; row < visibleRows; row++) {
            ColumnRef ref = columns.get(scroll + row);
            Rectangle rect = new Rectangle(listBounds.x, listBounds.y + row * COLUMN_ROW_H,
                    pos.width(), COLUMN_ROW_H - 2);
            rowBounds.put(ref.id(), rect);
            Color highlightColor = ownerColor(ref.id(), selectedIds, lineageBySelected, colorBySelected);
            drawColumnRow(g2, ref, rect, highlightColor, ref.id().equals(hoverColumnId));
        }
        g2.setClip(oldClip);

        if (total > visibleRows) {
            drawScrollbar(g2, listBounds, scroll, visibleRows, total);
        }
    }

    /**
     * Keeps each selected column's colour index stable across selection changes: deselected
     * columns free their slot; a newly selected column takes the lowest unused slot without
     * disturbing the colours of columns that stay selected.
     */
    private void reconcileSelectionColors(@NotNull List<String> selectedIds) {
        colorIndexByColumn.keySet().retainAll(selectedIds);
        Set<Integer> used = new HashSet<>(colorIndexByColumn.values());
        for (String id : selectedIds) {
            if (!colorIndexByColumn.containsKey(id)) {
                int index = 0;
                while (used.contains(index)) index++;
                colorIndexByColumn.put(id, index);
                used.add(index);
            }
        }
    }

    /** Colour of the first selected column whose lineage contains this column, or {@code null}. */
    private @Nullable Color ownerColor(@NotNull String columnId, @NotNull List<String> selectedIds,
                                       @NotNull Map<String, Set<String>> lineageBySelected,
                                       @NotNull Map<String, Color> colorBySelected) {
        for (String selectedId : selectedIds) {
            if (lineageBySelected.get(selectedId).contains(columnId)) {
                return colorBySelected.get(selectedId);
            }
        }
        return null;
    }

    private @Nullable Color edgeOwnerColor(@NotNull String from, @NotNull String to,
                                           @NotNull List<String> selectedIds,
                                           @NotNull Map<String, Set<String>> lineageBySelected,
                                           @NotNull Map<String, Color> colorBySelected) {
        for (String selectedId : selectedIds) {
            Set<String> lineage = lineageBySelected.get(selectedId);
            if (lineage.contains(from) && lineage.contains(to)) {
                return colorBySelected.get(selectedId);
            }
        }
        return null;
    }

    private void drawColumnRow(@NotNull Graphics2D g2, @NotNull ColumnRef ref,
                               @NotNull Rectangle rect, @Nullable Color highlightColor, boolean hover) {
        boolean lit = highlightColor != null;
        Color accent = lit ? highlightColor : LineageTheme.accentColor();
        Color background = lit ? LineageTheme.translucent(accent, 40)
                : hover ? LineageTheme.translucent(LineageTheme.accentColor(), 22)
                : LineageTheme.nodeBackground();
        g2.setColor(background);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6);
        g2.setColor(lit || hover ? accent : LineageTheme.nodeBorder());
        g2.setStroke(new BasicStroke(lit || hover ? 1.4f : 1f));
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 6, 6);

        g2.setColor(UIUtil.getLabelForeground());
        g2.setFont(LineageTheme.monospace(10f));
        var fm = g2.getFontMetrics();
        int ty = rect.y + (rect.height + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(LineageTheme.clip(g2, ref.columnName(), rect.width - 12), rect.x + 6, ty);
    }

    private void drawScrollbar(@NotNull Graphics2D g2, @NotNull Rectangle bounds,
                               int scroll, int visibleRows, int total) {
        int barW = 3;
        int x = bounds.x + bounds.width - barW - 1;
        g2.setColor(LineageTheme.translucent(LineageTheme.edgeColor(), 60));
        g2.fillRoundRect(x, bounds.y, barW, bounds.height, barW, barW);
        int thumbH = Math.max(6, (int) Math.round(bounds.height * (double) visibleRows / total));
        int thumbY = bounds.y + (int) Math.round(
                (bounds.height - thumbH) * (double) scroll / Math.max(1, total - visibleRows));
        g2.setColor(LineageTheme.edgeColor());
        g2.fillRoundRect(x, thumbY, barW, thumbH, barW, barW);
    }

    private void drawColumnEdge(@NotNull Graphics2D g2, @NotNull Rectangle from,
                                @NotNull Rectangle to, @NotNull Color color) {
        double x1 = from.x + from.width;
        double y1 = from.y + from.height / 2.0;
        double x2 = to.x;
        double y2 = to.y + to.height / 2.0;
        double cx = (x1 + x2) / 2.0;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x1, y1);
        path.curveTo(cx, y1, cx, y2, x2, y2);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                1f, new float[]{4f, 3f}, 0f));
        g2.draw(path);
        g2.setStroke(new BasicStroke(1f));
        EdgeRenderer.drawArrowHead(g2, x2, y2, Direction.LR);
    }
}
