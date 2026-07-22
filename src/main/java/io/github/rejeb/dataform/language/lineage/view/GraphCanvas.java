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
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.DagLayout;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.util.Set;

/**
 * Single custom-painted canvas rendering the whole lineage DAG via {@link Graphics2D},
 * with pan, zoom-to-cursor, hover/selection highlighting, a context menu, and fit-to-view.
 * Positions come from {@link DagLayout}; colours are theme-aware except the pinned
 * semantic type colours.
 */
public final class GraphCanvas extends JComponent {

    private static final int LEFT_PAD = 10;
    private static final int BADGE = 22;
    private static final int TEXT_GAP = 8;
    private static final int RIGHT_PAD = 10;

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.5;
    private static final int FIT_PAD = 60;
    private static final double FIT_MAX_ZOOM = 1.4;
    private static final float DIM_ALPHA = 0.28f;

    private final LineageModel model;
    private final Project project;

    private LayoutResult layout;
    private double zoom = 1.0;
    private double offsetX;
    private double offsetY;

    private Object lastGraph;
    private String lastLayoutKey;
    private boolean needsFit = true;
    private Point lastDragPoint;
    private boolean panning;
    private java.util.function.IntConsumer zoomListener;

    public void setZoomListener(@NotNull java.util.function.IntConsumer listener) {
        this.zoomListener = listener;
        notifyZoom();
    }

    private void notifyZoom() {
        if (zoomListener != null) zoomListener.accept((int) Math.round(zoom * 100));
    }

    public GraphCanvas(@NotNull Project project, @NotNull LineageModel model) {
        this.project = project;
        this.model = model;
        setOpaque(true);
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        installMouseHandlers();
        installKeyBindings();
        model.addListener(m -> onModelChanged());
        onModelChanged();
    }

    private void installKeyBindings() {
        getInputMap(WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "lineage.escape");
        getActionMap().put("lineage.escape", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (model.focusId() != null) {
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
            relayout(visible);
        }
        if (graphChanged) {
            lastGraph = model.graph();
            needsFit = true;
            maybeFit();
        }
        repaint();
    }

    private @NotNull String layoutKey(@NotNull Set<String> visible) {
        return System.identityHashCode(model.graph()) + "|" + model.direction() + "|"
                + model.density() + "|" + new java.util.TreeSet<>(visible);
    }

    private void relayout(@NotNull Set<String> visible) {
        int nodeW = measureNodeWidth(visible);
        this.layout = DagLayout.compute(model.graph(), visible, model.direction(), model.density(), nodeW);
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
        var b = layout.bounds();
        if (b.width <= 0 || b.height <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        double availW = getWidth() - 2.0 * FIT_PAD;
        double availH = getHeight() - 2.0 * FIT_PAD;
        double scale = Math.min(availW / b.width, availH / b.height);
        zoom = clamp(scale, MIN_ZOOM, FIT_MAX_ZOOM);
        offsetX = (getWidth() - b.width * zoom) / 2.0 - b.x * zoom;
        offsetY = (getHeight() - b.height * zoom) / 2.0 - b.y * zoom;
        notifyZoom();
        repaint();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        maybeFit();
    }

    // ------------------------------------------------------------------
    // Transform
    // ------------------------------------------------------------------

    private double worldX(int screenX) {
        return (screenX - offsetX) / zoom;
    }

    private double worldY(int screenY) {
        return (screenY - offsetY) / zoom;
    }

    /**
     * Returns the id of the node whose rectangle contains the given world point, or
     * {@code null} if none. Nodes share a uniform width/height from the layout pass.
     */
    static @Nullable String hitTest(@NotNull java.util.Map<String, NodePosition> positions,
                                    int nodeW, int nodeH, double worldX, double worldY) {
        for (NodePosition p : positions.values()) {
            if (worldX >= p.x() && worldX <= p.x() + nodeW
                    && worldY >= p.y() && worldY <= p.y() + nodeH) {
                return p.id();
            }
        }
        return null;
    }

    private @Nullable String nodeAt(@NotNull Point screen) {
        if (layout == null) return null;
        return hitTest(layout.positions(), layout.nodeW(), layout.nodeH(),
                worldX(screen.x), worldY(screen.y));
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
                    offsetX += e.getX() - lastDragPoint.x;
                    offsetY += e.getY() - lastDragPoint.y;
                    lastDragPoint = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                model.setHover(nodeAt(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                model.setHover(null);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger()) return;
                String id = nodeAt(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && id != null) {
                    openSource(model.graph().node(id));
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    model.select(id);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAt(e.getPoint(), e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1);
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }

    private void zoomAt(@NotNull Point cursor, double factor) {
        double newZoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == zoom) return;
        double wx = worldX(cursor.x);
        double wy = worldY(cursor.y);
        zoom = newZoom;
        offsetX = cursor.x - wx * zoom;
        offsetY = cursor.y - wy * zoom;
        notifyZoom();
        repaint();
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

    private static @NotNull javax.swing.JMenuItem item(@NotNull String text, @NotNull Runnable action) {
        javax.swing.JMenuItem menuItem = new javax.swing.JMenuItem(text);
        menuItem.addActionListener(a -> action.run());
        return menuItem;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        String id = nodeAt(event.getPoint());
        if (id == null) return null;
        LineageNode node = model.graph().node(id);
        return node != null ? node.fullName() + "  [" + node.dataformType() + "]" : null;
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

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(offsetX, offsetY);
            g2.scale(zoom, zoom);

            Set<String> highlight = model.highlightLineage();
            boolean dimming = !highlight.isEmpty();
            Direction dir = model.direction();

            for (LineageNode node : model.graph().nodes()) {
                NodePosition to = layout.positions().get(node.id());
                if (to == null) continue;
                for (String predId : model.graph().predecessors(node.id())) {
                    NodePosition from = layout.positions().get(predId);
                    if (from == null) continue;
                    boolean lit = !dimming || (highlight.contains(node.id()) && highlight.contains(predId));
                    drawEdge(g2, from, to, dir, lit ? 1f : DIM_ALPHA);
                }
            }

            for (NodePosition pos : layout.positions().values()) {
                LineageNode node = model.graph().node(pos.id());
                if (node == null) continue;
                float alpha = dimming && !highlight.contains(pos.id()) ? DIM_ALPHA : 1f;
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                drawNode(g2, node, pos, pos.id().equals(model.selectedId()));
                g2.setComposite(old);
            }
        } finally {
            g2.dispose();
        }

        if (model.minimapVisible()) {
            Graphics2D gm = (Graphics2D) g.create();
            try {
                gm.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintMinimap(gm);
            } finally {
                gm.dispose();
            }
        }
    }

    private void paintMinimap(@NotNull Graphics2D g2) {
        int mmW = JBUI.scale(180);
        int mmH = JBUI.scale(120);
        int margin = JBUI.scale(12);
        int inset = JBUI.scale(4);
        int mx = getWidth() - mmW - margin;
        int my = getHeight() - mmH - margin;

        var b = layout.bounds();
        if (b.width <= 0 || b.height <= 0) return;
        double availW = mmW - 2.0 * inset;
        double availH = mmH - 2.0 * inset;
        double s = Math.min(availW / b.width, availH / b.height);
        double ox = mx + inset + (availW - b.width * s) / 2.0 - b.x * s;
        double oy = my + inset + (availH - b.height * s) / 2.0 - b.y * s;

        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(mx, my, mmW, mmH, 8, 8);
        g2.setColor(edgeColor());
        g2.drawRoundRect(mx, my, mmW, mmH, 8, 8);

        for (NodePosition pos : layout.positions().values()) {
            LineageNode node = model.graph().node(pos.id());
            if (node == null) continue;
            g2.setColor(typeColor(node.dataformType()));
            int nx = (int) Math.round(ox + pos.x() * s);
            int ny = (int) Math.round(oy + pos.y() * s);
            int nw = Math.max(2, (int) Math.round(layout.nodeW() * s));
            int nh = Math.max(2, (int) Math.round(layout.nodeH() * s));
            g2.fillRect(nx, ny, nw, nh);
        }

        double vx = ox + worldX(0) * s;
        double vy = oy + worldY(0) * s;
        double vw = (worldX(getWidth()) - worldX(0)) * s;
        double vh = (worldY(getHeight()) - worldY(0)) * s;
        java.awt.Shape clip = g2.getClip();
        g2.setClip(mx, my, mmW, mmH);
        g2.setColor(accentColor());
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRect((int) Math.round(vx), (int) Math.round(vy), (int) Math.round(vw), (int) Math.round(vh));
        g2.setClip(clip);
    }

    private void drawEdge(@NotNull Graphics2D g2, @NotNull NodePosition from,
                          @NotNull NodePosition to, @NotNull Direction dir, float alpha) {
        int w = layout.nodeW();
        int h = layout.nodeH();
        double x1, y1, x2, y2;
        Path2D.Double path = new Path2D.Double();
        if (dir == Direction.TB) {
            x1 = from.x() + w / 2.0; y1 = from.y() + h;
            x2 = to.x() + w / 2.0;   y2 = to.y();
            double cy = (y1 + y2) / 2.0;
            path.moveTo(x1, y1);
            path.curveTo(x1, cy, x2, cy, x2, y2);
        } else {
            x1 = from.x() + w; y1 = from.y() + h / 2.0;
            x2 = to.x();       y2 = to.y() + h / 2.0;
            double cx = (x1 + x2) / 2.0;
            path.moveTo(x1, y1);
            path.curveTo(cx, y1, cx, y2, x2, y2);
        }
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(edgeColor());
        g2.draw(path);
        drawArrowHead(g2, x2, y2, dir);
        g2.setComposite(old);
    }

    private void drawArrowHead(@NotNull Graphics2D g2, double x, double y, @NotNull Direction dir) {
        double s = 6;
        Path2D.Double head = new Path2D.Double();
        if (dir == Direction.TB) {
            head.moveTo(x, y);
            head.lineTo(x - s / 2, y - s);
            head.lineTo(x + s / 2, y - s);
        } else {
            head.moveTo(x, y);
            head.lineTo(x - s, y - s / 2);
            head.lineTo(x - s, y + s / 2);
        }
        head.closePath();
        g2.fill(head);
    }

    private void drawNode(@NotNull Graphics2D g2, @NotNull LineageNode node,
                          @NotNull NodePosition pos, boolean selected) {
        int w = layout.nodeW();
        int h = layout.nodeH();
        int x = (int) Math.round(pos.x());
        int y = (int) Math.round(pos.y());
        Color type = typeColor(node.dataformType());

        g2.setColor(nodeBackground());
        g2.fillRoundRect(x, y, w, h, 10, 10);
        if (selected) {
            g2.setColor(accentColor());
            g2.setStroke(new BasicStroke(2f));
        } else {
            g2.setColor(nodeBorder());
            g2.setStroke(new BasicStroke(1f));
        }
        g2.drawRoundRect(x, y, w, h, 10, 10);

        g2.setColor(type);
        g2.fillRoundRect(x, y, 4, h, 4, 4);

        int by = y + (h - BADGE) / 2;
        int bx = x + LEFT_PAD;
        g2.setColor(translucent(type, 40));
        g2.fillRoundRect(bx, by, BADGE, BADGE, 6, 6);
        g2.setColor(type);
        g2.setFont(monospace(12f).deriveFont(Font.BOLD));
        String glyph = glyphFor(node.dataformType());
        var fm = g2.getFontMetrics();
        g2.drawString(glyph, bx + (BADGE - fm.stringWidth(glyph)) / 2,
                by + (BADGE + fm.getAscent() - fm.getDescent()) / 2);

        int textX = bx + BADGE + TEXT_GAP;
        int textAvail = (x + w - RIGHT_PAD) - textX;
        boolean comfortable = model.density() == Density.COMFORTABLE;
        if (comfortable) {
            g2.setColor(UIUtil.getLabelForeground());
            g2.setFont(monospace(12f));
            g2.drawString(clip(g2, node.name(), textAvail), textX, y + 18);
            g2.setColor(UIUtil.getLabelDisabledForeground());
            g2.setFont(monospace(10f));
            g2.drawString(clip(g2, node.schema() + " · " + node.dataformType(), textAvail), textX, y + 32);
        } else {
            g2.setColor(UIUtil.getLabelForeground());
            g2.setFont(monospace(11f));
            var nfm = g2.getFontMetrics();
            int ty = y + (h + nfm.getAscent() - nfm.getDescent()) / 2;
            g2.drawString(clip(g2, node.name(), textAvail), textX, ty);
        }
    }

    private int measureNodeWidth(@NotNull Set<String> visibleIds) {
        boolean comfortable = model.density() == Density.COMFORTABLE;
        int min = comfortable ? 180 : 160;
        int max = JBUI.scale(460);
        var nameFm = getFontMetrics(monospace(comfortable ? 12f : 11f));
        var subFm = getFontMetrics(monospace(10f));
        int content = min;
        for (String id : visibleIds) {
            LineageNode node = model.graph().node(id);
            if (node == null) continue;
            int textW;
            if (comfortable) {
                int nameW = nameFm.stringWidth(node.name());
                int subW = subFm.stringWidth(node.schema() + " · " + node.dataformType());
                textW = Math.max(nameW, subW);
            } else {
                textW = nameFm.stringWidth(node.name());
            }
            int total = LEFT_PAD + BADGE + TEXT_GAP + textW + RIGHT_PAD;
            content = Math.max(content, total);
        }
        return Math.min(content, max);
    }

    private static @NotNull String clip(@NotNull Graphics2D g2, @NotNull String text, int maxWidth) {
        var fm = g2.getFontMetrics();
        if (maxWidth <= 0) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = fm.stringWidth(ellipsis);
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) + ellipsisW > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------
    // Colours & fonts
    // ------------------------------------------------------------------

    private static @NotNull Font monospace(float size) {
        return MonoFont.get().deriveFont(JBUI.scale(size));
    }

    private static @NotNull Color nodeBackground() {
        return new JBColor(new Color(0xFFFFFF), new Color(0x2B2D30));
    }

    private static @NotNull Color nodeBorder() {
        return new JBColor(new Color(0xD4D4D4), new Color(0x3D4045));
    }

    private static @NotNull Color accentColor() {
        return new JBColor(new Color(0x3574F0), new Color(0x548AF7));
    }

    private static @NotNull Color edgeColor() {
        return new JBColor(new Color(0xB0B3BA), new Color(0x4E5157));
    }

    private static @NotNull Color translucent(@NotNull Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    static @NotNull Color typeColor(@Nullable String type) {
        String t = type == null ? "" : type.toLowerCase();
        return switch (t) {
            case "view" -> new JBColor(new Color(0x3574F0), new Color(0x5C9EFF));
            case "incremental" -> new JBColor(new Color(0x9B59B6), new Color(0xB48EAD));
            case "table" -> new JBColor(new Color(0x2A8A35), new Color(0x3FA84A));
            case "materialized_view" -> new JBColor(new Color(0x2A8A35), new Color(0x3FA84A));
            case "operation" -> new JBColor(new Color(0xC97A32), new Color(0xD19A66));
            case "assertion" -> new JBColor(new Color(0xC04148), new Color(0xE06C75));
            default -> new JBColor(new Color(0x6B7280), new Color(0x9CA0A4));
        };
    }

    static @NotNull String glyphFor(@Nullable String type) {
        String t = type == null ? "" : type.toLowerCase();
        return switch (t) {
            case "declaration" -> "D";
            case "view" -> "V";
            case "incremental" -> "I";
            case "table" -> "T";
            case "materialized_view" -> "M";
            case "operation" -> "O";
            case "assertion" -> "A";
            default -> "?";
        };
    }

    /** Lazily-created shared monospace base font. */
    private static final class MonoFont {
        private static Font base;

        static @NotNull Font get() {
            if (base == null) base = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            return base;
        }
    }
}
