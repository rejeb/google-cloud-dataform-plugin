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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.StatusText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import com.intellij.ui.scale.JBUIScale;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.DagLayout;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
    private static final int DOT_SPACING = 18;
    private static final int DOT_SIZE = 2;

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
    private String lastStructureKey;
    private String lastColumnKey;
    private boolean needsRecenter = true;
    private @Nullable String anchorNodeId;
    private Point lastDragPoint;
    private boolean panning;
    private boolean minimapShown = true;
    private boolean fitDown;
    private @NotNull Runnable selectionHandler = () -> {};
    private final StatusText emptyText = new StatusText(this) {
        @Override
        protected boolean isStatusVisible() {
            return isEmptyStateShown();
        }
    };

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
        EmptyStates.show(emptyText, "No action matches the filters", "Reset all filters", model::resetFilters);
        model.addListener(m -> onModelChanged());
        onModelChanged();
    }

    /**
     * The message shown in place of the graph when the filters leave no action.
     */
    public @NotNull StatusText getEmptyText() {
        return emptyText;
    }

    /**
     * Whether the graph has actions but the filters show none of them.
     */
    public boolean isEmptyStateShown() {
        return !model.graph().isEmpty() && model.visibleIds().isEmpty();
    }

    /**
     * Sets the action run after a click selects a node or one of its columns, including the node
     * that is already selected.
     */
    public void setSelectionHandler(@NotNull Runnable handler) {
        this.selectionHandler = handler;
    }

    /**
     * Zooms by the given factor around the centre of the canvas.
     */
    public void zoomBy(double factor) {
        viewport.zoomAt(new Point(getWidth() / 2, getHeight() / 2), factor);
    }

    public void setZoomListener(@NotNull IntConsumer listener) {
        viewport.setZoomListener(listener);
    }

    /**
     * Shows or leaves out the minimap, whatever the minimap setting of the model says.
     */
    public void setMinimapShown(boolean shown) {
        this.minimapShown = shown;
        repaint();
    }

    /**
     * Whether the minimap is painted: allowed on this canvas and switched on in the model.
     */
    public boolean isMinimapShown() {
        return minimapShown && model.minimapVisible();
    }

    /**
     * When on, recentring scales the graph down to fit the canvas but never above 100%.
     * When off, recentring fits the graph to the canvas, as the fit button does.
     */
    public void setFitDown(boolean fitDown) {
        this.fitDown = fitDown;
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
                } else if (model.selectedId() != null) {
                    model.select(null);
                }
            }
        });
    }

    private void onModelChanged() {
        Set<String> visible = model.visibleIds();
        String structureKey = structureKey(visible);
        String columnKey = columnKey();
        boolean firstLayout = layout == null;
        boolean structureChanged = !structureKey.equals(lastStructureKey);
        boolean columnChanged = !columnKey.equals(lastColumnKey);
        lastStructureKey = structureKey;
        lastColumnKey = columnKey;
        boolean recenter = firstLayout || (structureChanged && !columnChanged);
        if (firstLayout || structureChanged || columnChanged) {
            String anchorId = recenter ? null : anchorNodeId();
            NodePosition before = anchorId == null ? null : layout.positions().get(anchorId);
            relayout(visible);
            if (before != null) holdAnchorInPlace(before, layout.positions().get(anchorId));
        }
        anchorNodeId = null;
        if (recenter) {
            needsRecenter = true;
            maybeRecenter();
        }
        repaint();
    }

    private @NotNull String structureKey(@NotNull Set<String> visible) {
        return System.identityHashCode(model.graph())
                + "|" + model.direction() + "|" + model.density() + "|" + new TreeSet<>(visible);
    }

    private @NotNull String columnKey() {
        return System.identityHashCode(model.columnGraph()) + "|"
                + new TreeSet<>(model.selectedColumnIds());
    }

    /**
     * Node to keep pinned under the pointer when a column selection re-flows the layout: the node
     * whose column list was clicked, else the expanded one, else the selected node.
     */
    private @Nullable String anchorNodeId() {
        if (anchorNodeId != null) return anchorNodeId;
        if (hoverExpandedNodeId != null) return hoverExpandedNodeId;
        return model.selectedId();
    }

    private void holdAnchorInPlace(@NotNull NodePosition before, @Nullable NodePosition after) {
        if (after == null) return;
        viewport.panByWorld(before.x() - after.x(), before.y() - after.y());
    }

    private void relayout(@NotNull Set<String> visible) {
        this.layout = DagLayout.compute(model.graph(), visible, model.direction(), model.density(),
                nodeRenderer::measureWidth, columnRenderer::reservedHeightFor);
    }

    private void maybeRecenter() {
        if (needsRecenter && getWidth() > 0 && getHeight() > 0 && layout != null
                && !layout.positions().isEmpty()) {
            needsRecenter = false;
            recenter();
        }
    }

    /**
     * Brings the whole graph back into view: fitted down in fit-down mode, fitted to the canvas
     * otherwise. Waits for the canvas to have a size when it has none yet.
     */
    public void recenter() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            needsRecenter = true;
            return;
        }
        if (layout == null || layout.positions().isEmpty()) return;
        if (fitDown) {
            viewport.fitDown(layout.bounds(), getWidth(), getHeight());
        } else {
            fitToView();
        }
    }

    public void fitToView() {
        if (layout == null || layout.positions().isEmpty()) return;
        viewport.fit(layout.bounds(), getWidth(), getHeight());
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int oldWidth = getWidth();
        int oldHeight = getHeight();
        super.setBounds(x, y, width, height);
        if (oldWidth > 0 && oldHeight > 0 && width > 0 && height > 0 && (oldWidth != width || oldHeight != height)) {
            if (fitDown) {
                recenter();
            } else {
                viewport.panBy((width - oldWidth) / 2, (height - oldHeight) / 2);
            }
        }
        maybeRecenter();
    }

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
            anchorNodeId = columnListNodeAt(e.getPoint());
            if (e.isControlDown() || e.isMetaDown()) {
                model.toggleColumn(columnId);
            } else {
                model.selectColumn(columnId);
            }
            selectionHandler.run();
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
        if (id != null) selectionHandler.run();
    }

    private void showContextMenu(@NotNull MouseEvent e) {
        String id = nodeAt(e.getPoint());
        if (id == null || model.graph().node(id) == null) return;
        ActionManager.getInstance()
                .createActionPopupMenu("Dataform.Lineage.Node", contextMenuFor(id))
                .getComponent().show(this, e.getX(), e.getY());
    }

    /**
     * Selects the node and returns the actions of its context menu: open its SQLX, run it, focus
     * on its lineage and copy its name.
     */
    @NotNull ActionGroup contextMenuFor(@NotNull String id) {
        model.select(id);
        LineageNode node = model.graph().node(id);
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(nodeAction("Open SQLX", DataformIcons.FILE,
                node != null && node.fileName() != null, () -> openSource(node)));
        group.add(nodeAction("Run", DataformIcons.RUN,
                node != null && node.fileName() != null && !"declaration".equals(node.dataformType()),
                () -> LineageActions.runAction(project, node)));
        group.addSeparator();
        boolean focused = id.equals(model.focusId());
        group.add(nodeAction(focused ? "Exit focus" : "Focus on lineage", LineageIcons.FOCUS, true, () -> {
            if (focused) {
                model.exitFocus();
            } else {
                model.focusOn(id);
            }
        }));
        group.add(nodeAction("Copy name", DataformIcons.COPY, node != null, () -> {
            if (node != null) CopyPasteManager.getInstance().setContents(new StringSelection(node.fullName()));
        }));
        return group;
    }

    private static @NotNull AnAction nodeAction(@NotNull String text, @NotNull Icon icon, boolean enabled,
                                                @NotNull Runnable run) {
        return new DumbAwareAction(text, null, icon) {
            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(enabled);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                run.run();
            }
        };
    }

    private void openSource(@Nullable LineageNode node) {
        LineageActions.openSource(project, node);
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintBackground(g);
        emptyText.paint(this, g);
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

        if (isMinimapShown()) {
            Graphics2D gm = (Graphics2D) g.create();
            try {
                gm.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                minimapRenderer.paint(gm, layout);
            } finally {
                gm.dispose();
            }
        }
    }

    private void paintBackground(@NotNull Graphics g) {
        Graphics2D bg = (Graphics2D) g.create();
        try {
            bg.setColor(LineageTheme.graphBackground());
            bg.fillRect(0, 0, getWidth(), getHeight());
            int spacing = JBUIScale.scale(DOT_SPACING);
            int size = JBUIScale.scale(DOT_SIZE);
            int first = spacing / 2 - size / 2;
            bg.setColor(LineageTheme.graphDot());
            for (int y = first; y < getHeight(); y += spacing) {
                for (int x = first; x < getWidth(); x += spacing) {
                    bg.fillRect(x, y, size, size);
                }
            }
        } finally {
            bg.dispose();
        }
    }
}
