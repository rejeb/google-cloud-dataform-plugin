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

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.DagLayout;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntConsumer;

import io.github.rejeb.dataform.language.lineage.model.LineageModel;

/**
 * Custom-painted canvas rendering the whole lineage DAG. It owns the layout, the pointer and
 * keyboard interaction and the paint order, and delegates the drawing itself to
 * {@link EdgeRenderer}, {@link NodeRenderer}, {@link ColumnListRenderer} and
 * {@link MinimapRenderer}. Positions come from {@link DagLayout}.
 */
public final class GraphCanvas extends JComponent {

    private static final int COLUMN_HOVER_DELAY_MS = 1000;

    private final LineageModel model;
    private final Project project;

    private final CanvasViewport viewport = new CanvasViewport(this::repaint);
    private final NodeRenderer nodeRenderer;
    private final EdgeRenderer edgeRenderer;
    private final ColumnListRenderer columnRenderer;
    private final MinimapRenderer minimapRenderer;

    private final Timer columnHoverTimer;
    private @Nullable String hoverExpandedNodeId;
    private @Nullable Point lastHoverPoint;

    private LayoutResult layout;
    private Object lastGraph;
    private String lastLayoutKey;
    private boolean lastColumnSelected;
    private boolean needsFit = true;
    private Point lastDragPoint;
    private boolean panning;

    public GraphCanvas(@NotNull Project project, @NotNull LineageModel model) {
        this.project = project;
        this.model = model;
        this.nodeRenderer = new NodeRenderer(this, model);
        this.edgeRenderer = new EdgeRenderer(model);
        this.columnRenderer = new ColumnListRenderer(this, model, viewport);
        this.minimapRenderer = new MinimapRenderer(this, model, viewport);
        setOpaque(true);
        setFocusable(true);
        columnHoverTimer = new Timer(COLUMN_HOVER_DELAY_MS, e -> {
            hoverExpandedNodeId = resolveHoverTarget(lastHoverPoint);
            repaint();
        });
        columnHoverTimer.setRepeats(false);
        installMouseHandlers();
        installKeyBindings();
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                collapseColumnList();
            }
        });
        model.addListener(m -> onModelChanged());
        onModelChanged();
    }

    public void setZoomListener(@NotNull IntConsumer listener) {
        viewport.setZoomListener(listener);
    }

    private void installKeyBindings() {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "lineage.escape");
        getActionMap().put("lineage.escape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (model.selectedColumnId() != null) {
                    model.clearColumnSelection();
                } else if (model.focusId() != null) {
                    model.exitFocus();
                    fitToView();
                } else if (model.selectedId() != null) {
                    model.select(null);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Model / layout lifecycle
    // ------------------------------------------------------------------

    private void onModelChanged() {
        Set<String> visible = model.visibleIds();
        String key = layoutKey(visible);
        boolean graphChanged = model.graph() != lastGraph;
        if (!key.equals(lastLayoutKey) || layout == null) {
            lastLayoutKey = key;
            needsFit = true;
            relayout(visible);
        }
        if (graphChanged) {
            lastGraph = model.graph();
            needsFit = true;
            maybeFit();
        }
        boolean columnSelected = model.selectedColumnId() != null;
        if (columnSelected != lastColumnSelected) {
            lastColumnSelected = columnSelected;
            needsFit = true;
            maybeFit();
        }
        repaint();
    }

    private @NotNull String layoutKey(@NotNull Set<String> visible) {
        return System.identityHashCode(model.graph()) + "|"
                + System.identityHashCode(model.columnGraph()) + "|"
                + new TreeSet<>(model.selectedColumnIds())
                + "|" + model.direction() + "|" + model.density() + "|" + new TreeSet<>(visible);
    }

    private void relayout(@NotNull Set<String> visible) {
        this.layout = DagLayout.compute(model.graph(), visible, model.direction(), model.density(),
                nodeRenderer::measureWidth, columnRenderer::reservedHeightFor);
        maybeFit();
    }

    private void maybeFit() {
        if (needsFit && getWidth() > 0 && getHeight() > 0 && layout != null
                && !layout.positions().isEmpty()) {
            fitToView();
            needsFit = false;
        }
    }

    public void fitToView() {
        if (layout == null || layout.positions().isEmpty()) return;
        viewport.fit(layout.bounds(), getWidth(), getHeight());
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        maybeFit();
    }

    // ------------------------------------------------------------------
    // Hit testing and hover state
    // ------------------------------------------------------------------

    private @Nullable String nodeAt(@NotNull Point screen) {
        if (layout == null) return null;
        return CanvasHitTest.nodeAt(layout.positions(), layout.nodeH(),
                viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private @Nullable String columnAt(@NotNull Point screen) {
        return columnRenderer.columnAt(viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private @Nullable String columnListNodeAt(@NotNull Point screen) {
        return columnRenderer.listNodeAt(viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    /**
     * Arms the delay timer that reveals (or, after leaving, hides) the hovered node's columns.
     * While the pointer stays over the node or its already-open column list the timer is
     * cancelled so the list stays open.
     */
    private void updateColumnHover(@NotNull Point screen) {
        lastHoverPoint = screen;
        if (model.selectedColumnId() != null) {
            columnHoverTimer.stop();
            if (hoverExpandedNodeId != null) {
                hoverExpandedNodeId = null;
                repaint();
            }
            return;
        }
        if (isOverExpandedRegion(screen)) {
            columnHoverTimer.stop();
            return;
        }
        columnHoverTimer.restart();
    }

    private void resetColumnHover() {
        lastHoverPoint = null;
        columnHoverTimer.restart();
    }

    /** Immediately hides the hover/click-opened column list. */
    private void collapseColumnList() {
        columnHoverTimer.stop();
        if (hoverExpandedNodeId != null) {
            hoverExpandedNodeId = null;
            repaint();
        }
    }

    private @Nullable String resolveHoverTarget(@Nullable Point screen) {
        if (screen == null) return null;
        if (isOverExpandedRegion(screen)) return hoverExpandedNodeId;
        return nodeAt(screen);
    }

    /**
     * Whether the pointer is over the currently expanded node's box or the contiguous
     * bounding rectangle of its column list (so inter-row gaps do not collapse it).
     */
    private boolean isOverExpandedRegion(@NotNull Point screen) {
        if (hoverExpandedNodeId == null || layout == null) return false;
        NodePosition pos = layout.positions().get(hoverExpandedNodeId);
        if (pos == null) return false;
        Rectangle nodeRect = new Rectangle((int) Math.round(pos.x()), (int) Math.round(pos.y()),
                pos.width(), layout.nodeH());
        int count = columnRenderer.columnCountFor(hoverExpandedNodeId);
        Rectangle region = nodeRect.union(columnRenderer.listWorldBounds(pos, count, layout.nodeH()));
        return region.contains(viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private void setHoverColumn(@Nullable String columnId) {
        if (!columnRenderer.setHoverColumn(columnId)) return;
        setCursor(columnId != null
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
        repaint();
    }

    // ------------------------------------------------------------------
    // Mouse handling
    // ------------------------------------------------------------------

    private void installMouseHandlers() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }
                if (nodeAt(e.getPoint()) == null) {
                    panning = true;
                    lastDragPoint = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panning = false;
                lastDragPoint = null;
                setCursor(Cursor.getDefaultCursor());
                if (e.isPopupTrigger()) showContextMenu(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panning && lastDragPoint != null) {
                    viewport.panBy(e.getX() - lastDragPoint.x, e.getY() - lastDragPoint.y);
                    lastDragPoint = e.getPoint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                model.setHover(nodeAt(e.getPoint()));
                setHoverColumn(columnAt(e.getPoint()));
                updateColumnHover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                model.setHover(null);
                setHoverColumn(null);
                resetColumnHover();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger()) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                handleLeftClick(e);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                String node = columnListNodeAt(e.getPoint());
                if (node != null) {
                    columnRenderer.scrollBy(node, e.getWheelRotation());
                    repaint();
                    return;
                }
                viewport.zoomAt(e.getPoint(), e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1);
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }

    private void handleLeftClick(@NotNull MouseEvent e) {
        String columnId = columnAt(e.getPoint());
        if (columnId != null) {
            if (e.isControlDown() || e.isMetaDown()) {
                model.toggleColumn(columnId);
            } else {
                model.selectColumn(columnId);
            }
            return;
        }
        if (columnListNodeAt(e.getPoint()) != null) return;

        String id = nodeAt(e.getPoint());
        if (e.getClickCount() == 2 && id != null) {
            openSource(model.graph().node(id));
            return;
        }
        if (id != null && columnRenderer.columnCountFor(id) > 0 && !Objects.equals(id, hoverExpandedNodeId)) {
            model.clearColumnSelection();
            columnHoverTimer.stop();
            hoverExpandedNodeId = id;
            repaint();
            return;
        }
        if (id == null) collapseColumnList();
        model.clearColumnSelection();
        model.select(id);
    }

    private void showContextMenu(@NotNull MouseEvent e) {
        String id = nodeAt(e.getPoint());
        if (id == null) return;
        LineageNode node = model.graph().node(id);
        if (node == null) return;
        JPopupMenu menu = new JPopupMenu();
        boolean focused = id.equals(model.focusId());
        menu.add(item(focused ? "Exit focus" : "Focus on lineage", () -> {
            if (focused) {
                model.exitFocus();
            } else {
                model.focusOn(id);
            }
            fitToView();
        }));
        menu.add(item("Select node", () -> model.select(id)));
        if (node.fileName() != null) {
            menu.add(item("Open source", () -> openSource(node)));
        }
        menu.add(item("Copy qualified name", () ->
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(node.fullName()), null)));
        menu.show(this, e.getX(), e.getY());
    }

    private static @NotNull JMenuItem item(@NotNull String text, @NotNull Runnable action) {
        JMenuItem menuItem = new JMenuItem(text);
        menuItem.addActionListener(a -> action.run());
        return menuItem;
    }

    private void openSource(@Nullable LineageNode node) {
        LineageActions.openSource(project, node);
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D bg = (Graphics2D) g.create();
        bg.setColor(UIUtil.getPanelBackground());
        bg.fillRect(0, 0, getWidth(), getHeight());
        bg.dispose();
        if (layout == null || layout.positions().isEmpty()) return;

        columnRenderer.clearRowBounds();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            viewport.applyTo(g2);

            edgeRenderer.paintEdges(g2, layout, columnRenderer.listBoundsByNode());
            for (NodePosition pos : layout.positions().values()) {
                LineageNode node = model.graph().node(pos.id());
                if (node == null) continue;
                nodeRenderer.paint(g2, node, pos, layout.nodeH(), pos.id().equals(model.selectedId()));
            }
            edgeRenderer.paintOverlappingParts(g2);
            columnRenderer.paint(g2, layout, hoverExpandedNodeId);
        } finally {
            g2.dispose();
        }

        if (model.minimapVisible()) {
            Graphics2D gm = (Graphics2D) g.create();
            try {
                gm.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                minimapRenderer.paint(gm, layout);
            } finally {
                gm.dispose();
            }
        }
    }
}
