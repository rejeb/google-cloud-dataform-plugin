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

import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paints the table-to-table edges of the lineage DAG. Edges are routed through the free
 * channels between the nodes they would otherwise pass under; whatever still overlaps a node
 * is drawn on top of it as a thin dashed line so it stays readable.
 */
final class EdgeRenderer {

    private static final float DIM_ALPHA = 0.28f;
    private static final double EDGE_CLEARANCE = 6;
    private static final int EDGE_DETOUR_ATTEMPTS = 8;
    private static final double MIN_EDGE_GAP = 12;
    private static final float CROSSING_EDGE_ALPHA = 0.85f;
    private static final double ARROW_SIZE = 6;

    private final LineageModel model;
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<CrossingEdge> crossingEdges = new ArrayList<>();

    private record Obstacle(@NotNull String id, @NotNull Rectangle2D.Double rect) {
    }

    private record CrossingEdge(@NotNull Path2D.Double path, float alpha) {
    }

    EdgeRenderer(@NotNull LineageModel model) {
        this.model = model;
    }

    /**
     * Draws every visible edge below the nodes and remembers the parts that overlap a node,
     * to be painted afterwards by {@link #paintOverlappingParts(Graphics2D)}.
     */
    void paintEdges(@NotNull Graphics2D g2, @NotNull LayoutResult layout,
                    @NotNull Map<String, Rectangle> columnListBounds) {
        collectObstacles(layout, columnListBounds);
        crossingEdges.clear();

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
                drawEdge(g2, predId, node.id(), from, to, layout.nodeH(), dir, lit ? 1f : DIM_ALPHA);
            }
        }
    }

    /** Paints only the node-overlapping parts of edges, above the nodes, thin and dashed. */
    void paintOverlappingParts(@NotNull Graphics2D g2) {
        if (crossingEdges.isEmpty()) return;
        Composite old = g2.getComposite();
        g2.setStroke(new BasicStroke(0.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                1f, new float[]{3f, 3f}, 0f));
        g2.setColor(LineageTheme.edgeColor());
        for (CrossingEdge edge : crossingEdges) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    Math.min(1f, edge.alpha() * CROSSING_EDGE_ALPHA)));
            g2.draw(edge.path());
        }
        g2.setComposite(old);
        g2.setStroke(new BasicStroke(1f));
    }

    static void drawArrowHead(@NotNull Graphics2D g2, double x, double y, @NotNull Direction dir) {
        Path2D.Double head = new Path2D.Double();
        if (dir == Direction.TB) {
            head.moveTo(x, y);
            head.lineTo(x - ARROW_SIZE / 2, y - ARROW_SIZE);
            head.lineTo(x + ARROW_SIZE / 2, y - ARROW_SIZE);
        } else {
            head.moveTo(x, y);
            head.lineTo(x - ARROW_SIZE, y - ARROW_SIZE / 2);
            head.lineTo(x - ARROW_SIZE, y + ARROW_SIZE / 2);
        }
        head.closePath();
        g2.fill(head);
    }

    /**
     * Rebuilds the list of areas edges must not cross: every node box plus every currently
     * expanded column list.
     */
    private void collectObstacles(@NotNull LayoutResult layout,
                                  @NotNull Map<String, Rectangle> columnListBounds) {
        obstacles.clear();
        double h = layout.nodeH();
        for (NodePosition pos : layout.positions().values()) {
            obstacles.add(new Obstacle(pos.id(), new Rectangle2D.Double(pos.x(), pos.y(), pos.width(), h)));
        }
        for (Map.Entry<String, Rectangle> entry : columnListBounds.entrySet()) {
            Rectangle r = entry.getValue();
            obstacles.add(new Obstacle(entry.getKey(),
                    new Rectangle2D.Double(r.x, r.y, r.width, r.height)));
        }
    }

    private void drawEdge(@NotNull Graphics2D g2, @NotNull String fromId, @NotNull String toId,
                          @NotNull NodePosition from, @NotNull NodePosition to, int nodeH,
                          @NotNull Direction dir, float alpha) {
        double x1, y1, x2, y2;
        if (dir == Direction.TB) {
            x1 = from.x() + from.width() / 2.0; y1 = from.y() + nodeH;
            x2 = to.x() + to.width() / 2.0;     y2 = to.y();
        } else {
            x1 = from.x() + from.width(); y1 = from.y() + nodeH / 2.0;
            x2 = to.x();                  y2 = to.y() + nodeH / 2.0;
        }

        List<Rectangle2D.Double> blockers = blockers(fromId, toId);
        Path2D.Double path = edgePath(x1, y1, x2, y2, dir, 0);
        if (crosses(path, blockers, EDGE_CLEARANCE)) {
            for (double bend : candidateBends(x1, y1, x2, y2, dir, blockers)) {
                Path2D.Double candidate = edgePath(x1, y1, x2, y2, dir, bend);
                if (!crosses(candidate, blockers, EDGE_CLEARANCE)) {
                    path = candidate;
                    break;
                }
            }
        }

        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.setStroke(new BasicStroke(1.4f));
        g2.setColor(LineageTheme.edgeColor());
        drawSolidOutsideParts(g2, path, blockers, alpha);
        drawArrowHead(g2, x2, y2, dir);
        g2.setComposite(old);
    }

    /**
     * Draws the parts of the edge that lie outside every node as a solid line, and defers the
     * parts that overlap a node so they can be painted on top of it as a thin dashed line.
     */
    private void drawSolidOutsideParts(@NotNull Graphics2D g2, @NotNull Path2D.Double path,
                                       @NotNull List<Rectangle2D.Double> blockers, float alpha) {
        if (blockers.isEmpty()) {
            g2.draw(path);
            return;
        }
        Path2D.Double run = null;
        boolean runInside = false;
        double px = 0, py = 0;
        boolean started = false;
        PathIterator it = new FlatteningPathIterator(path.getPathIterator(null), 1.0);
        double[] coords = new double[6];
        while (!it.isDone()) {
            int type = it.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                px = coords[0];
                py = coords[1];
                started = true;
            } else if (type == PathIterator.SEG_LINETO && started) {
                boolean inside = insideAny((px + coords[0]) / 2.0, (py + coords[1]) / 2.0, blockers);
                if (run == null || inside != runInside) {
                    flushRun(g2, run, runInside, alpha);
                    run = new Path2D.Double();
                    run.moveTo(px, py);
                    runInside = inside;
                }
                run.lineTo(coords[0], coords[1]);
                px = coords[0];
                py = coords[1];
            }
            it.next();
        }
        flushRun(g2, run, runInside, alpha);
    }

    private void flushRun(@NotNull Graphics2D g2, @Nullable Path2D.Double run, boolean inside, float alpha) {
        if (run == null) return;
        if (inside) {
            crossingEdges.add(new CrossingEdge(run, alpha));
        } else {
            g2.draw(run);
        }
    }

    private static boolean insideAny(double x, double y, @NotNull List<Rectangle2D.Double> blockers) {
        for (Rectangle2D.Double r : blockers) {
            if (r.contains(x, y)) return true;
        }
        return false;
    }

    /**
     * Builds the edge curve, optionally bent perpendicular to the flow direction by
     * {@code bend} at its midpoint so it can pass above or below an obstacle.
     */
    private static @NotNull Path2D.Double edgePath(double x1, double y1, double x2, double y2,
                                                   @NotNull Direction dir, double bend) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x1, y1);
        if (dir == Direction.TB) {
            double cy = (y1 + y2) / 2.0;
            if (bend == 0) {
                path.curveTo(x1, cy, x2, cy, x2, y2);
            } else {
                double mx = (x1 + x2) / 2.0 + bend;
                path.curveTo(x1, y1 + (cy - y1) * 0.6, mx, cy - (cy - y1) * 0.6, mx, cy);
                path.curveTo(mx, cy + (y2 - cy) * 0.6, x2, y2 - (y2 - cy) * 0.6, x2, y2);
            }
        } else {
            double cx = (x1 + x2) / 2.0;
            if (bend == 0) {
                path.curveTo(cx, y1, cx, y2, x2, y2);
            } else {
                double my = (y1 + y2) / 2.0 + bend;
                path.curveTo(x1 + (cx - x1) * 0.6, y1, cx - (cx - x1) * 0.6, my, cx, my);
                path.curveTo(cx + (x2 - cx) * 0.6, my, x2 - (x2 - cx) * 0.6, y2, x2, y2);
            }
        }
        return path;
    }

    /**
     * Offsets that steer the edge through the free channels between the nodes it would otherwise
     * cross, closest to the straight route first. Only gaps wide enough for a line plus its
     * clearance are offered, so a detour never trades one crossed node for several.
     */
    private @NotNull List<Double> candidateBends(double x1, double y1, double x2, double y2,
                                                 @NotNull Direction dir,
                                                 @NotNull List<Rectangle2D.Double> blockers) {
        boolean vertical = dir == Direction.TB;
        double flowMin = Math.min(vertical ? y1 : x1, vertical ? y2 : x2);
        double flowMax = Math.max(vertical ? y1 : x1, vertical ? y2 : x2);
        double mid = vertical ? (x1 + x2) / 2.0 : (y1 + y2) / 2.0;

        List<double[]> spans = new ArrayList<>();
        for (Rectangle2D.Double r : blockers) {
            double flowStart = vertical ? r.y : r.x;
            double flowEnd = flowStart + (vertical ? r.height : r.width);
            if (flowEnd <= flowMin || flowStart >= flowMax) continue;
            double crossStart = vertical ? r.x : r.y;
            double crossEnd = crossStart + (vertical ? r.width : r.height);
            spans.add(new double[]{crossStart - EDGE_CLEARANCE, crossEnd + EDGE_CLEARANCE});
        }
        if (spans.isEmpty()) return List.of();
        spans.sort((a, b) -> Double.compare(a[0], b[0]));

        List<double[]> merged = new ArrayList<>();
        for (double[] span : spans) {
            double[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && span[0] <= last[1]) {
                last[1] = Math.max(last[1], span[1]);
            } else {
                merged.add(new double[]{span[0], span[1]});
            }
        }

        List<Double> targets = new ArrayList<>();
        targets.add(merged.get(0)[0] - MIN_EDGE_GAP);
        targets.add(merged.get(merged.size() - 1)[1] + MIN_EDGE_GAP);
        for (int i = 1; i < merged.size(); i++) {
            double gapStart = merged.get(i - 1)[1];
            double gapEnd = merged.get(i)[0];
            if (gapEnd - gapStart < MIN_EDGE_GAP) continue;
            targets.add(Math.max(gapStart + MIN_EDGE_GAP / 2,
                    Math.min(gapEnd - MIN_EDGE_GAP / 2, mid)));
        }

        List<Double> bends = new ArrayList<>();
        for (double target : targets) bends.add(target - mid);
        bends.sort((a, b) -> Double.compare(Math.abs(a), Math.abs(b)));
        return bends.size() > EDGE_DETOUR_ATTEMPTS ? bends.subList(0, EDGE_DETOUR_ATTEMPTS) : bends;
    }

    /** Node areas this edge must avoid: every obstacle except the ones it connects. */
    private @NotNull List<Rectangle2D.Double> blockers(@NotNull String fromId, @NotNull String toId) {
        List<Rectangle2D.Double> blockers = new ArrayList<>();
        for (Obstacle obstacle : obstacles) {
            if (obstacle.id().equals(fromId) || obstacle.id().equals(toId)) continue;
            blockers.add(obstacle.rect());
        }
        return blockers;
    }

    /** True when the flattened path enters any blocker grown by {@code clearance}. */
    private boolean crosses(@NotNull Path2D.Double path, @NotNull List<Rectangle2D.Double> blockers,
                            double clearance) {
        if (blockers.isEmpty()) return false;
        List<Rectangle2D.Double> grown = new ArrayList<>(blockers.size());
        for (Rectangle2D.Double r : blockers) {
            grown.add(new Rectangle2D.Double(r.x - clearance, r.y - clearance,
                    r.width + 2 * clearance, r.height + 2 * clearance));
        }

        PathIterator it = new FlatteningPathIterator(path.getPathIterator(null), 2.0);
        double[] coords = new double[6];
        double px = 0, py = 0;
        boolean started = false;
        while (!it.isDone()) {
            int type = it.currentSegment(coords);
            if (type == PathIterator.SEG_MOVETO) {
                px = coords[0];
                py = coords[1];
                started = true;
            } else if (type == PathIterator.SEG_LINETO && started) {
                for (Rectangle2D.Double r : grown) {
                    if (r.intersectsLine(px, py, coords[0], coords[1])) return true;
                }
                px = coords[0];
                py = coords[1];
            }
            it.next();
        }
        return false;
    }
}
