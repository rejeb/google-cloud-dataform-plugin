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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.DatabaseIcons;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TreeMap;

/**
 * Project-wide lineage view panel. Arranges {@link LineageNode}s in topological columns
 * (left = upstream, right = downstream) and draws dependency arrows between them.
 */
public final class LineageProjectPanel extends JPanel {

    private static final int NODE_W   = 210;
    private static final int NODE_H   = 36;
    private static final int COL_GAP  = 72;
    private static final int ROW_GAP  = 10;
    private static final int PAD      = 20;

    private static final Color EDGE_COLOR = new JBColor(new Color(160, 165, 185), new Color(100, 105, 125));

    private final ContentPanel content;

    public LineageProjectPanel(@NotNull Project project) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());

        content = new ContentPanel(project);
        JBScrollPane scroll = new JBScrollPane(
                content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setBackground(UIUtil.getPanelBackground());
        add(scroll, BorderLayout.CENTER);

        content.showPlaceholder("Compile the Dataform project to see the lineage.");
    }

    public void setGraph(@Nullable LineageGraph graph) {
        content.rebuild(graph);
    }

    // -------------------------------------------------------------------------
    // Content panel
    // -------------------------------------------------------------------------

    private final class ContentPanel extends JPanel {

        private final Project project;
        private final Map<String, Rectangle> nodeBounds = new LinkedHashMap<>();
        private final List<String[]> edges = new ArrayList<>();

        ContentPanel(@NotNull Project project) {
            super(null);
            this.project = project;
            setOpaque(true);
            setBackground(UIUtil.getPanelBackground());
        }

        void showPlaceholder(@NotNull String message) {
            removeAll();
            nodeBounds.clear();
            edges.clear();
            setLayout(new BorderLayout());
            JBLabel label = new JBLabel(message, SwingConstants.CENTER);
            label.setForeground(JBColor.GRAY);
            add(label, BorderLayout.CENTER);
            setPreferredSize(new Dimension(400, 200));
            revalidate();
            repaint();
        }

        void rebuild(@Nullable LineageGraph graph) {
            removeAll();
            nodeBounds.clear();
            edges.clear();
            setLayout(null);

            if (graph == null || graph.isEmpty()) {
                showPlaceholder("No lineage data available. Compile the project first.");
                return;
            }

            Map<String, Integer> depths = computeDepths(graph);
            TreeMap<Integer, List<LineageNode>> byDepth = groupByDepth(graph.nodes(), depths);

            if (byDepth.isEmpty()) {
                showPlaceholder("No lineage data available.");
                return;
            }

            int numCols    = byDepth.lastKey() + 1;
            int maxRows    = byDepth.values().stream().mapToInt(List::size).max().orElse(0);
            int totalW     = PAD * 2 + numCols * NODE_W + (numCols - 1) * COL_GAP;
            int totalH     = PAD * 2 + maxRows * NODE_H + (maxRows - 1) * ROW_GAP;
            setPreferredSize(new Dimension(Math.max(totalW, 400), Math.max(totalH, 200)));

            for (Map.Entry<Integer, List<LineageNode>> entry : byDepth.entrySet()) {
                int col = entry.getKey();
                int colX = PAD + col * (NODE_W + COL_GAP);
                List<LineageNode> nodes = entry.getValue();
                int colH = nodes.size() * NODE_H + (nodes.size() - 1) * ROW_GAP;
                int startY = PAD + (totalH - PAD * 2 - colH) / 2;
                for (int i = 0; i < nodes.size(); i++) {
                    LineageNode node = nodes.get(i);
                    int nodeY = startY + i * (NODE_H + ROW_GAP);
                    Rectangle bounds = new Rectangle(colX, nodeY, NODE_W, NODE_H);
                    nodeBounds.put(node.id(), bounds);
                    NodePanel panel = new NodePanel(node, project);
                    panel.setBounds(bounds);
                    add(panel);
                }
            }

            for (LineageNode node : graph.nodes()) {
                for (String predId : graph.predecessors(node.id())) {
                    edges.add(new String[]{predId, node.id()});
                }
            }

            revalidate();
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (edges.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(EDGE_COLOR);
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (String[] edge : edges) {
                    drawEdge(g2, nodeBounds.get(edge[0]), nodeBounds.get(edge[1]));
                }
            } finally {
                g2.dispose();
            }
        }

        private void drawEdge(Graphics2D g2, @Nullable Rectangle src, @Nullable Rectangle dst) {
            if (src == null || dst == null) return;
            int x1 = src.x + src.width;
            int y1 = src.y + src.height / 2;
            int x2 = dst.x;
            int y2 = dst.y + dst.height / 2;
            int mx = (x1 + x2) / 2;

            if (Math.abs(y1 - y2) < 3) {
                g2.drawLine(x1, y1, x2, y2);
            } else {
                g2.drawLine(x1, y1, mx, y1);
                g2.drawLine(mx, y1, mx, y2);
                g2.drawLine(mx, y2, x2, y2);
            }
            int s = 6;
            g2.fill(new Polygon(
                    new int[]{x2, x2 - s, x2 - s},
                    new int[]{y2, y2 - s / 2, y2 + s / 2},
                    3));
        }
    }

    // -------------------------------------------------------------------------
    // Topological layout
    // -------------------------------------------------------------------------

    private static @NotNull Map<String, Integer> computeDepths(@NotNull LineageGraph graph) {
        Map<String, Integer> depths = new LinkedHashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        for (LineageNode node : graph.nodes()) {
            if (graph.predecessors(node.id()).isEmpty()) {
                depths.put(node.id(), 0);
                queue.add(node.id());
            }
        }
        for (LineageNode node : graph.nodes()) {
            depths.putIfAbsent(node.id(), 0);
        }
        while (!queue.isEmpty()) {
            String id = queue.poll();
            int depth = depths.get(id);
            for (String succId : graph.successors(id)) {
                int newDepth = depth + 1;
                if (depths.getOrDefault(succId, -1) < newDepth) {
                    depths.put(succId, newDepth);
                    queue.add(succId);
                }
            }
        }
        return depths;
    }

    private static @NotNull TreeMap<Integer, List<LineageNode>> groupByDepth(
            @NotNull Collection<LineageNode> nodes,
            @NotNull Map<String, Integer> depths) {
        TreeMap<Integer, List<LineageNode>> map = new TreeMap<>();
        for (LineageNode node : nodes) {
            int d = depths.getOrDefault(node.id(), 0);
            map.computeIfAbsent(d, k -> new ArrayList<>()).add(node);
        }
        map.values().forEach(list -> list.sort(
                Comparator.comparing(LineageNode::name, String.CASE_INSENSITIVE_ORDER)));
        return map;
    }

    // -------------------------------------------------------------------------
    // Node component
    // -------------------------------------------------------------------------

    private static final class NodePanel extends JPanel {

        private final String dataformType;
        private boolean hovered = false;

        NodePanel(@NotNull LineageNode node, @NotNull Project project) {
            super(new BorderLayout());
            this.dataformType = node.dataformType();
            setOpaque(false);
            setBorder(JBUI.Borders.empty(0, 8));

            SimpleColoredComponent label = new SimpleColoredComponent();
            label.setOpaque(false);
            label.setFont(JBUI.Fonts.label(11f));
            label.setIcon(iconFor(dataformType));
            label.append(node.name(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            label.append("  " + node.dataformType(),
                    new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER,
                            JBUI.CurrentTheme.ContextHelp.FOREGROUND));
            add(label, BorderLayout.CENTER);
            setToolTipText(node.fullName() + "  [" + node.dataformType() + "]");

            boolean hasFile = node.fileName() != null && !node.fileName().isBlank();
            if (hasFile) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }

                    @Override
                    public void mouseExited(MouseEvent e) { hovered = false; repaint(); }

                    @Override
                    public void mouseClicked(MouseEvent e) { openFile(node, project); }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = NODE_H;

            g2.setColor(new Color(0, 0, 0, 20));
            g2.fillRoundRect(2, 2, w - 2, h - 2, 8, 8);

            g2.setColor(hovered ? UIUtil.getPanelBackground().brighter() : UIUtil.getPanelBackground());
            g2.fillRoundRect(0, 0, w - 2, h - 2, 8, 8);

            g2.setColor(borderColor(dataformType));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 2, h - 2, 8, 8);

            g2.dispose();
            super.paintComponent(g);
        }

        private static void openFile(@NotNull LineageNode node, @NotNull Project project) {
            String fileName = node.fileName();
            if (fileName == null) return;
            String basePath = project.getBasePath();
            if (basePath == null) return;
            String absolutePath = basePath + "/" + fileName;
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath);
                if (vf == null) return;
                ApplicationManager.getApplication().invokeLater(() ->
                        FileEditorManager.getInstance(project)
                                .openEditor(new OpenFileDescriptor(project, vf), true));
            });
        }

        private static @NotNull Color borderColor(@NotNull String type) {
            return switch (type.toLowerCase()) {
                case "table"             -> new JBColor(new Color(70, 130, 200), new Color(80, 150, 220));
                case "incremental"       -> new JBColor(new Color(40, 160, 130), new Color(50, 190, 155));
                case "view"              -> new JBColor(new Color(60, 160, 80), new Color(70, 190, 90));
                case "materialized_view" -> new JBColor(new Color(100, 180, 100), new Color(110, 200, 115));
                case "assertion"         -> new JBColor(new Color(210, 150, 40), new Color(230, 170, 60));
                case "declaration"       -> new JBColor(new Color(150, 80, 180), new Color(170, 100, 200));
                default                  -> new JBColor(new Color(150, 150, 160), new Color(90, 90, 100));
            };
        }

        private static @NotNull Icon iconFor(@NotNull String type) {
            return switch (type.toLowerCase()) {
                case "table"             -> AllIcons.Nodes.DataTables;
                case "incremental"       -> DatabaseIcons.PartionTable;
                case "view"              -> DatabaseIcons.VirtualView;
                case "materialized_view" -> DatabaseIcons.MaterializedView;
                case "assertion"         -> AllIcons.Nodes.JunitTestMark;
                case "declaration"       -> DatabaseIcons.Foreign_table;
                default                  -> AllIcons.Nodes.Unknown;
            };
        }
    }
}
