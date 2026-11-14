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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.StatusText;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.gcp.common.BigQueryConsoleUrls;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
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
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.github.rejeb.dataform.language.lineage.model.CardColumns;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;

/**
 * Custom-painted canvas rendering the whole lineage DAG. It owns the layout, the pointer and
 * keyboard interaction and the paint order, and delegates the drawing itself to
 * {@link EdgeRenderer}, {@link NodeRenderer}, {@link ColumnListRenderer} and
 * {@link MinimapRenderer}. Positions come from {@link DagLayout}.
 */
public final class GraphCanvas extends JComponent {

    private static final int DOT_SPACING = 18;
    private static final int DOT_SIZE = 2;

    private final LineageModel model;
    private final Project project;

    private final CanvasViewport viewport = new CanvasViewport(this::repaint);
    private final NodeRenderer nodeRenderer;
    private final EdgeRenderer edgeRenderer;
    private final ColumnListRenderer columnRenderer;
    private final MinimapRenderer minimapRenderer;

    private LayoutResult layout;
    private String lastStructureKey;
    private String lastCardKey;
    private @NotNull Map<String, Integer> cardHeights = Map.of();
    private @Nullable Map<String, Rectangle> rowBounds;
    private boolean needsRecenter = true;
    private @Nullable String anchorNodeId;
    private Point lastDragPoint;
    private boolean panning;
    private boolean minimapShown = true;
    private boolean fitDown;
    private @Nullable String placeholder;
    private @Nullable String pinnedNodeId;
    private @Nullable ViewportState pendingViewport;
    private @Nullable Set<String> pendingFit;
    private @Nullable String pendingCentre;
    private @Nullable DataplexView expandersFor;
    private @NotNull Map<String, int[]> expanders = Map.of();
    private @Nullable BiConsumer<String, DataplexLineageClient.Direction> expandHandler;
    private @Nullable FreshnessTips freshnessTips;
    private @Nullable String infoNodeId;
    private @Nullable IdeTooltip infoTooltip;
    private boolean linkSelection;
    private @NotNull Consumer<String> urlOpener = BrowserUtil::browse;
    private @Nullable String emptyStateKey;
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
        this.columnRenderer = new ColumnListRenderer(model);
        this.minimapRenderer = new MinimapRenderer(this, model, viewport);
        setOpaque(true);
        setFocusable(true);
        installMouseHandlers();
        installKeyBindings();
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
        if (placeholder != null) return model.graph().isEmpty();
        return !model.graph().isEmpty() && model.visibleIds().isEmpty();
    }

    /**
     * Shows a message with a link in place of the graph while there is none, such as what to do to
     * load one. Without a placeholder an empty graph shows nothing.
     */
    void setPlaceholder(@NotNull String message, @NotNull String link, @NotNull Runnable onLink) {
        placeholder = message;
        emptyStateKey = "placeholder:" + message;
        EmptyStates.show(emptyText, message, link, onLink);
        repaint();
    }

    /**
     * Removes the message shown in place of an empty graph.
     */
    void clearPlaceholder() {
        if (placeholder == null) return;
        placeholder = null;
        refreshEmptyText();
        repaint();
    }

    /**
     * Sets what a click on an expander does. Without a handler no expander is drawn.
     */
    void setExpandHandler(@Nullable BiConsumer<String, DataplexLineageClient.Direction> handler) {
        expandHandler = handler;
        expandersFor = null;
        expanders = Map.of();
        refreshExpanders();
        repaint();
    }

    /**
     * Sets where the freshness tooltips of the nodes come from. Without it the freshness icon has
     * no tooltip.
     */
    void setFreshnessTips(@Nullable FreshnessTips tips) {
        freshnessTips = tips;
    }

    /**
     * Lets a click on an edge select it, with the recorded lineage only.
     */
    void setLinkSelection(boolean enabled) {
        linkSelection = enabled;
    }

    /**
     * Sets what opens a page of the Google Cloud console; the browser by default.
     */
    void setUrlOpener(@NotNull Consumer<String> opener) {
        urlOpener = opener;
    }

    private @Nullable LinkRef edgeAt(@NotNull Point screen) {
        if (!linkSelection || model.source() != LineageSource.DATAPLEX || cardAt(screen) != null) return null;
        return CanvasHitTest.edgeAt(edgeRenderer.edgePaths(), viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    /**
     * Keeps the given node where it is on screen the next time the graph changes, instead of
     * fitting the new graph to the view.
     */
    void keepInPlace(@NotNull String nodeId) {
        pinnedNodeId = nodeId;
    }

    /**
     * Keeps the view where it is the next time the graph changes: the selected node, else the node
     * nearest the middle of the canvas, stays where it is on screen.
     */
    void keepView() {
        if (layout == null || layout.positions().isEmpty()) return;
        String selected = model.selectedId();
        String kept = selected != null && layout.positions().containsKey(selected) ? selected : nodeNearestTheMiddle();
        if (kept != null) pinnedNodeId = kept;
    }

    /**
     * @return the zoom and scroll position of the canvas
     */
    @NotNull ViewportState viewportState() {
        return new ViewportState(viewport.zoom(), viewport.offsetX(), viewport.offsetY());
    }

    /**
     * Puts the canvas back at the given zoom and scroll position the next time a graph is laid out,
     * instead of fitting that graph to the view.
     */
    void restoreViewport(@NotNull ViewportState state) {
        pendingViewport = state;
    }

    /**
     * Fits the given nodes to the view, or brings the whole graph back when none of them is laid out.
     */
    void fitTo(@NotNull Set<String> nodeIds) {
        Rectangle2D.Double box = boxOf(nodeIds);
        if (box == null) {
            recenter();
            return;
        }
        if (getWidth() <= 0 || getHeight() <= 0) {
            pendingFit = Set.copyOf(nodeIds);
            return;
        }
        viewport.fit(box, getWidth(), getHeight());
    }

    /**
     * Puts the node in the middle of the canvas at the current zoom.
     */
    void centerOn(@NotNull String nodeId) {
        Point2D.Double centre = worldCentreOf(nodeId);
        if (centre == null) return;
        if (getWidth() <= 0 || getHeight() <= 0) {
            pendingCentre = nodeId;
            return;
        }
        viewport.centerOn(centre.x, centre.y, getWidth(), getHeight());
    }

    /**
     * @return where the middle of the node is on the canvas, or {@code null} when it is not laid out
     */
    @Nullable Point2D.Double screenCentreOf(@NotNull String nodeId) {
        Point2D.Double centre = worldCentreOf(nodeId);
        return centre == null ? null : new Point2D.Double(viewport.screenX(centre.x), viewport.screenY(centre.y));
    }

    private @Nullable Point2D.Double worldCentreOf(@NotNull String nodeId) {
        NodePosition position = layout == null ? null : layout.positions().get(nodeId);
        if (position == null) return null;
        return new Point2D.Double(position.x() + position.width() / 2.0,
                position.y() + (layout.nodeH() + extraHeight(nodeId)) / 2.0);
    }

    private @Nullable Rectangle2D.Double boxOf(@NotNull Set<String> nodeIds) {
        if (layout == null) return null;
        Rectangle2D.Double box = null;
        for (String id : nodeIds) {
            NodePosition position = layout.positions().get(id);
            if (position == null) continue;
            Rectangle2D.Double node = new Rectangle2D.Double(position.x(), position.y(), position.width(),
                    layout.nodeH() + extraHeight(id));
            if (box == null) {
                box = node;
            } else {
                box.add(node);
            }
        }
        return box;
    }

    private @Nullable String nodeNearestTheMiddle() {
        double middleX = viewport.worldX(getWidth() / 2.0);
        double middleY = viewport.worldY(getHeight() / 2.0);
        String nearest = null;
        double best = Double.MAX_VALUE;
        for (String id : layout.positions().keySet()) {
            Point2D.Double centre = worldCentreOf(id);
            if (centre == null) continue;
            double distance = centre.distanceSq(middleX, middleY);
            if (distance < best) {
                best = distance;
                nearest = id;
            }
        }
        return nearest;
    }

    private void refreshExpanders() {
        DataplexView view = expandHandler == null ? null : model.dataplexView();
        if (view == expandersFor) return;
        expandersFor = view;
        if (view == null) {
            expanders = Map.of();
            return;
        }
        Map<String, int[]> counts = new HashMap<>();
        for (String fqn : view.snapshot().entities()) {
            int upstream = view.snapshot().hiddenUpstream(fqn);
            int downstream = view.snapshot().hiddenDownstream(fqn);
            if (upstream != 0 || downstream != 0) counts.put(EntityNames.nodeIdOf(fqn), new int[]{upstream, downstream});
        }
        expanders = counts;
    }

    private int hidden(@NotNull String nodeId, @NotNull DataplexLineageClient.Direction direction) {
        int[] counts = expanders.get(nodeId);
        if (counts == null) return 0;
        return counts[direction == DataplexLineageClient.Direction.UPSTREAM ? 0 : 1];
    }

    private void refreshEmptyText() {
        if (placeholder != null) return;
        String key = model.source().name();
        if (key.equals(emptyStateKey)) return;
        emptyStateKey = key;
        EmptyStates.show(emptyText,
                model.source() == LineageSource.DATAPLEX ? "No node matches the filters" : "No action matches the filters",
                "Reset all filters", model::resetFilters);
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
        refreshEmptyText();
        refreshExpanders();
        Set<String> visible = model.visibleIds();
        String structureKey = structureKey(visible);
        cardHeights = cardHeights(visible);
        rowBounds = null;
        String cardKey = cardHeights.toString();
        boolean firstLayout = layout == null;
        boolean structureChanged = !structureKey.equals(lastStructureKey);
        boolean cardChanged = !cardKey.equals(lastCardKey);
        lastStructureKey = structureKey;
        lastCardKey = cardKey;
        String pinned = structureChanged && !firstLayout && pinnedNodeId != null
                && layout.positions().containsKey(pinnedNodeId) ? pinnedNodeId : null;
        if (structureChanged) pinnedNodeId = null;
        boolean recenter = firstLayout || (structureChanged && !cardChanged && pinned == null);
        if (firstLayout || structureChanged || cardChanged) {
            String anchorId = recenter ? null : pinned != null ? pinned : anchorNodeId();
            NodePosition before = anchorId == null ? null : layout.positions().get(anchorId);
            relayout(visible);
            if (before != null) holdAnchorInPlace(before, layout.positions().get(anchorId));
        }
        anchorNodeId = null;
        if (pendingViewport != null && structureChanged && !layout.positions().isEmpty()) {
            ViewportState restored = pendingViewport;
            pendingViewport = null;
            recenter = false;
            needsRecenter = false;
            viewport.set(restored.zoom(), restored.offsetX(), restored.offsetY());
        }
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

    private @NotNull Map<String, Integer> cardHeights(@NotNull Set<String> visible) {
        Map<String, Integer> heights = new TreeMap<>();
        for (String id : visible) {
            int extra = columnRenderer.extraHeight(id);
            if (extra > 0) heights.put(id, extra);
        }
        return heights;
    }

    private int extraHeight(@NotNull String nodeId) {
        return cardHeights.getOrDefault(nodeId, 0);
    }

    private @NotNull Map<String, Rectangle> rowBounds() {
        Map<String, Rectangle> current = rowBounds;
        if (current == null) {
            current = layout == null ? Map.of() : NodeCardMetrics.rowBounds(layout.positions(), layout.nodeH(),
                    id -> cardHeights.containsKey(id) ? model.cardColumns(id) : CardColumns.NONE);
            rowBounds = current;
        }
        return current;
    }

    /**
     * Node to keep pinned under the pointer when cards grow or shrink and re-flow the layout: the
     * node whose band or column was clicked, else the selected node.
     */
    private @Nullable String anchorNodeId() {
        return anchorNodeId != null ? anchorNodeId : model.selectedId();
    }

    private void holdAnchorInPlace(@NotNull NodePosition before, @Nullable NodePosition after) {
        if (after == null) return;
        viewport.panByWorld(before.x() - after.x(), before.y() - after.y());
    }

    private void relayout(@NotNull Set<String> visible) {
        this.layout = DagLayout.compute(model.graph(), visible, model.direction(), model.density(),
                nodeRenderer::measureWidth, this::extraHeight);
    }

    private void maybeRecenter() {
        if (getWidth() <= 0 || getHeight() <= 0 || layout == null || layout.positions().isEmpty()) return;
        if (pendingFit != null) {
            Set<String> ids = pendingFit;
            pendingFit = null;
            needsRecenter = false;
            fitTo(ids);
            return;
        }
        if (pendingCentre != null) {
            String id = pendingCentre;
            pendingCentre = null;
            if (needsRecenter) {
                needsRecenter = false;
                recenter();
            }
            centerOn(id);
            return;
        }
        if (needsRecenter) {
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

    private @Nullable String cardAt(@NotNull Point screen) {
        if (layout == null) return null;
        return CanvasHitTest.cardAt(layout.positions(), layout.nodeH(), this::extraHeight,
                viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private @Nullable String toggleAt(@NotNull Point screen) {
        if (layout == null) return null;
        return CanvasHitTest.columnToggleAt(layout.positions(), layout.nodeH(), model::hasColumns,
                viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private @Nullable String columnAt(@NotNull Point screen) {
        return CanvasHitTest.columnAt(rowBounds(), viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private @Nullable CanvasHitTest.ExpanderHit expanderAt(@NotNull Point screen) {
        if (layout == null || expanders.isEmpty()) return null;
        int nodeH = layout.nodeH();
        return CanvasHitTest.expanderAt(layout.positions(), id -> nodeH + extraHeight(id), this::hidden,
                viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private @Nullable String infoAt(@NotNull Point screen) {
        if (layout == null || model.source() != LineageSource.DATAPLEX) return null;
        return CanvasHitTest.infoAt(layout.positions(), viewport.worldX(screen.x), viewport.worldY(screen.y));
    }

    private void setPointerTargets(@Nullable String toggleNodeId, @Nullable String columnId,
                                   @Nullable CanvasHitTest.ExpanderHit expander, @Nullable String infoId,
                                   boolean overEdge) {
        boolean toggleChanged = nodeRenderer.setHoverToggle(toggleNodeId);
        boolean columnChanged = columnRenderer.setHoverColumn(columnId);
        boolean expanderChanged = nodeRenderer.setHoverExpander(expander);
        boolean infoChanged = nodeRenderer.setHoverInfo(infoId);
        setCursor(toggleNodeId != null || columnId != null || expander != null || overEdge
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
        if (toggleChanged || columnChanged || expanderChanged || infoChanged) repaint();
        if (!Objects.equals(infoId, infoNodeId)) {
            infoNodeId = infoId;
            showInfoTooltip();
        }
    }

    private void showInfoTooltip() {
        if (infoTooltip != null) {
            IdeTooltipManager.getInstance().hide(infoTooltip);
            infoTooltip = null;
        }
        String nodeId = infoNodeId;
        if (nodeId == null || freshnessTips == null || layout == null) return;
        String text = freshnessTips.text(nodeId, () -> {
            if (nodeId.equals(infoNodeId)) showInfoTooltip();
        });
        NodePosition pos = layout.positions().get(nodeId);
        if (text == null || pos == null || !isShowing()) return;
        Rectangle icon = NodeCardMetrics.infoBounds(pos);
        Point anchor = new Point((int) Math.round(viewport.screenX(icon.getCenterX())),
                (int) Math.round(viewport.screenY(icon.getMaxY())));
        infoTooltip = new IdeTooltip(this, anchor, new JBLabel(UiText.htmlTooltip(text)), nodeId, text)
                .setPreferredPosition(Balloon.Position.below);
        IdeTooltipManager.getInstance().show(infoTooltip, true);
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
                if (cardAt(e.getPoint()) == null) {
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
                Point point = e.getPoint();
                CanvasHitTest.ExpanderHit expander = expanderAt(point);
                model.setHover(expander != null ? null : cardAt(point));
                String info = expander == null ? infoAt(point) : null;
                String toggle = expander == null && info == null ? toggleAt(point) : null;
                String column = expander == null && info == null && toggle == null ? columnAt(point) : null;
                setPointerTargets(toggle, column, expander, info, expander == null && edgeAt(point) != null);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                model.setHover(null);
                setPointerTargets(null, null, null, null, false);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger()) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                handleLeftClick(e);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                CanvasWheel.apply(e, viewport);
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }

    private void handleLeftClick(@NotNull MouseEvent e) {
        Point point = e.getPoint();
        CanvasHitTest.ExpanderHit expander = expanderAt(point);
        if (expander != null) {
            if (expandHandler != null) expandHandler.accept(expander.nodeId(), expander.direction());
            return;
        }
        String toggle = toggleAt(point);
        if (toggle != null) {
            anchorNodeId = toggle;
            model.toggleExpanded(toggle);
            return;
        }
        String columnId = columnAt(point);
        if (columnId != null) {
            anchorNodeId = cardAt(point);
            selectColumn(columnId, e.isControlDown() || e.isMetaDown() || e.isShiftDown());
            return;
        }
        String id = nodeAt(point);
        if (id == null && cardAt(point) != null) return;
        if (e.getClickCount() == 2 && id != null) {
            openNode(model.graph().node(id));
            return;
        }
        LinkRef edge = id == null ? edgeAt(point) : null;
        if (edge != null) {
            model.selectLink(edge);
            selectionHandler.run();
            return;
        }
        if (id == null) model.clearColumnSelection();
        model.select(id);
        if (id != null) selectionHandler.run();
    }

    private void selectColumn(@NotNull String columnId, boolean addToSelection) {
        if (addToSelection) {
            model.toggleColumn(columnId);
        } else if (model.selectedColumnIds().equals(Set.of(columnId))) {
            model.clearColumnSelection();
        } else {
            model.selectColumn(columnId);
        }
        if (!model.selectedColumnIds().isEmpty()) selectionHandler.run();
    }

    private void showContextMenu(@NotNull MouseEvent e) {
        String id = cardAt(e.getPoint());
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

    private void openNode(@Nullable LineageNode node) {
        if (node == null) return;
        if (DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType())) {
            urlOpener.accept(BigQueryConsoleUrls.tableUrl(node.fullName()));
        } else if (!DataplexGraphBuilder.TYPE_ASSET.equals(node.dataformType())) {
            openSource(node);
        }
    }

    private void openSource(@Nullable LineageNode node) {
        LineageActions.openSource(project, node);
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintBackground(g);
        emptyText.paint(this, g);
        if (layout == null || layout.positions().isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            viewport.applyTo(g2);

            edgeRenderer.paintEdges(g2, layout, this::extraHeight);
            for (NodePosition pos : layout.positions().values()) {
                LineageNode node = model.graph().node(pos.id());
                if (node == null) continue;
                int extra = extraHeight(pos.id());
                nodeRenderer.paint(g2, node, pos, layout.nodeH(), extra, pos.id().equals(model.selectedId()));
                if (extra > 0) columnRenderer.paintRows(g2, pos.id(), pos, layout.nodeH());
            }
            edgeRenderer.paintOverlappingParts(g2);
            for (NodePosition pos : layout.positions().values()) {
                int[] counts = expanders.get(pos.id());
                if (counts == null) continue;
                nodeRenderer.paintExpanders(g2, pos, layout.nodeH() + extraHeight(pos.id()), counts[0], counts[1]);
            }
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

    /**
     * The zoom and scroll position of the canvas.
     */
    record ViewportState(double zoom, double offsetX, double offsetY) {
    }
}
