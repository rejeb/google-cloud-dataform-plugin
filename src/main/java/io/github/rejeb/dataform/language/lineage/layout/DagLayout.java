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
package io.github.rejeb.dataform.language.lineage.layout;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import org.jetbrains.annotations.NotNull;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Sugiyama-style layered layout for the lineage DAG. Nodes are assigned to layers by
 * longest path from a root (no predecessors); within a layer, order is refined with
 * barycenter passes to reduce edge crossings; coordinates are then centred per layer.
 *
 * <p>Java port of the design reference {@code layout.js}. Only nodes in {@code visibleIds}
 * (and edges between them) participate.</p>
 */
public final class DagLayout {

    private static final int BARYCENTER_PASSES = 6;

    private DagLayout() {
    }

    public static @NotNull LayoutResult compute(@NotNull LineageGraph graph,
                                                @NotNull Set<String> visibleIds,
                                                @NotNull Direction direction,
                                                @NotNull Density density) {
        return compute(graph, visibleIds, direction, density,
                density == Density.COMPACT ? 160 : 180);
    }

    /**
     * Same as {@link #compute(LineageGraph, Set, Direction, Density)} but with an explicit
     * node width (e.g. measured from text so labels fit). Height and gaps still follow density.
     */
    public static @NotNull LayoutResult compute(@NotNull LineageGraph graph,
                                                @NotNull Set<String> visibleIds,
                                                @NotNull Direction direction,
                                                @NotNull Density density,
                                                int nodeW) {
        return compute(graph, visibleIds, direction, density, nodeW, id -> 0);
    }

    /**
     * Same as {@link #compute(LineageGraph, Set, Direction, Density, int)} but reserves, per
     * node, {@code extraVerticalHeight} additional pixels below the node along the vertical
     * (screen {@code y}) axis, so a column list drawn below a node does not overlap the node
     * beneath it. Each node only reserves the space its own list needs.
     */
    public static @NotNull LayoutResult compute(@NotNull LineageGraph graph,
                                                @NotNull Set<String> visibleIds,
                                                @NotNull Direction direction,
                                                @NotNull Density density,
                                                int nodeW,
                                                @NotNull ToIntFunction<String> extraVerticalHeight) {
        return compute(graph, visibleIds, direction, density, id -> nodeW, extraVerticalHeight);
    }

    /**
     * Same as {@link #compute(LineageGraph, Set, Direction, Density, int, ToIntFunction)} but with
     * a per-node measured width. Nodes of the same vertical group share the widest measurement of
     * that group: in {@link Direction#LR} a group is a layer (the nodes stacked vertically in one
     * column), in {@link Direction#TB} it is the set of nodes sharing a row index across layers.
     * Groups are laid out one after another using their own width, so a group of short labels no
     * longer reserves the space required by the longest label in the whole graph.
     */
    public static @NotNull LayoutResult compute(@NotNull LineageGraph graph,
                                                @NotNull Set<String> visibleIds,
                                                @NotNull Direction direction,
                                                @NotNull Density density,
                                                @NotNull ToIntFunction<String> nodeWidth,
                                                @NotNull ToIntFunction<String> extraVerticalHeight) {
        int nodeH = density == Density.COMPACT ? 30 : 44;
        int layerGap = direction == Direction.TB ? 60 : 90;
        int rowGap = density == Density.COMPACT ? 14 : 22;

        List<String> ids = new ArrayList<>(visibleIds);

        Map<String, List<String>> incoming = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (String id : ids) {
            incoming.put(id, new ArrayList<>());
            outgoing.put(id, new ArrayList<>());
        }
        for (String id : ids) {
            for (String pred : graph.predecessors(id)) {
                if (!visibleIds.contains(pred)) continue;
                incoming.get(id).add(pred);
                outgoing.get(pred).add(id);
            }
        }

        Map<String, Integer> layer = new HashMap<>();
        Set<String> visiting = new HashSet<>();
        for (String id : ids) {
            longestPathLayer(id, incoming, layer, visiting);
        }

        int layerCount = layer.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<List<String>> layers = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) layers.add(new ArrayList<>());
        for (String id : ids) layers.get(layer.get(id)).add(id);

        Comparator<String> stable = Comparator.comparing(id -> {
            LineageNode n = graph.node(id);
            return n != null ? (n.schema() + n.name()) : id;
        });
        for (List<String> lay : layers) lay.sort(stable);

        for (int pass = 0; pass < BARYCENTER_PASSES; pass++) {
            for (int l = 1; l < layers.size(); l++) {
                barycenter(layers, l, positionMap(layers.get(l - 1)), incoming);
            }
            for (int l = layers.size() - 2; l >= 0; l--) {
                barycenter(layers, l, positionMap(layers.get(l + 1)), outgoing);
            }
        }

        Map<String, Integer> widths = groupWidths(layers, direction, nodeWidth);

        Map<String, NodePosition> positions = direction == Direction.TB
                ? layoutTopToBottom(layers, widths, nodeH, layerGap, rowGap, extraVerticalHeight)
                : layoutLeftToRight(layers, widths, nodeH, layerGap, rowGap, extraVerticalHeight);

        int maxWidth = widths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new LayoutResult(positions, computeBounds(positions, nodeH), maxWidth, nodeH);
    }

    /**
     * Width of each node, equalised within its vertical group so the group forms a clean column:
     * per layer in {@link Direction#LR}, per row index across layers in {@link Direction#TB}.
     */
    private static @NotNull Map<String, Integer> groupWidths(@NotNull List<List<String>> layers,
                                                             @NotNull Direction direction,
                                                             @NotNull ToIntFunction<String> nodeWidth) {
        Map<String, Integer> widths = new HashMap<>();
        if (direction == Direction.TB) {
            int maxRows = layers.stream().mapToInt(List::size).max().orElse(0);
            for (int row = 0; row < maxRows; row++) {
                int groupWidth = 0;
                for (List<String> lay : layers) {
                    if (row < lay.size()) groupWidth = Math.max(groupWidth, nodeWidth.applyAsInt(lay.get(row)));
                }
                for (List<String> lay : layers) {
                    if (row < lay.size()) widths.put(lay.get(row), groupWidth);
                }
            }
        } else {
            for (List<String> lay : layers) {
                int groupWidth = 0;
                for (String id : lay) groupWidth = Math.max(groupWidth, nodeWidth.applyAsInt(id));
                for (String id : lay) widths.put(id, groupWidth);
            }
        }
        return widths;
    }

    /**
     * Left-to-right: layers advance along {@code x}; within a layer nodes stack along the
     * vertical {@code y} axis, each consuming {@code nodeH + its column-list height}, so a
     * node reserves only the space its own list needs.
     */
    private static @NotNull Map<String, NodePosition> layoutLeftToRight(
            @NotNull List<List<String>> layers, @NotNull Map<String, Integer> widths, int nodeH,
            int layerGap, int rowGap, @NotNull ToIntFunction<String> extraVerticalHeight) {
        double totalAcross = 0;
        for (List<String> lay : layers) {
            totalAcross = Math.max(totalAcross, layerVerticalExtent(lay, nodeH, rowGap, extraVerticalHeight));
        }
        Map<String, NodePosition> positions = new LinkedHashMap<>();
        double along = 0;
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> lay = layers.get(layerIdx);
            int layerWidth = layerWidth(lay, widths);
            double cursor = (totalAcross - layerVerticalExtent(lay, nodeH, rowGap, extraVerticalHeight)) / 2.0;
            for (String id : lay) {
                positions.put(id, new NodePosition(id, along, cursor, layerIdx, widths.get(id)));
                cursor += nodeH + extraVerticalHeight.applyAsInt(id) + rowGap;
            }
            along += layerWidth + (double) layerGap;
        }
        return positions;
    }

    private static int layerWidth(@NotNull List<String> layer, @NotNull Map<String, Integer> widths) {
        int width = 0;
        for (String id : layer) width = Math.max(width, widths.getOrDefault(id, 0));
        return width;
    }

    /**
     * Top-to-bottom: layers advance along the vertical {@code y} axis; within a layer nodes
     * stack along {@code x}. Because the list extends into the next layer, each layer's gap
     * grows by the tallest column list in it.
     */
    private static @NotNull Map<String, NodePosition> layoutTopToBottom(
            @NotNull List<List<String>> layers, @NotNull Map<String, Integer> widths, int nodeH,
            int layerGap, int rowGap, @NotNull ToIntFunction<String> extraVerticalHeight) {
        double totalAcross = 0;
        for (List<String> lay : layers) {
            totalAcross = Math.max(totalAcross, layerHorizontalExtent(lay, widths, rowGap));
        }
        Map<String, NodePosition> positions = new LinkedHashMap<>();
        double alongCursor = 0;
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> lay = layers.get(layerIdx);
            double across = (totalAcross - layerHorizontalExtent(lay, widths, rowGap)) / 2.0;
            int maxExtra = 0;
            for (String id : lay) {
                positions.put(id, new NodePosition(id, across, alongCursor, layerIdx, widths.get(id)));
                across += widths.get(id) + (double) rowGap;
                maxExtra = Math.max(maxExtra, extraVerticalHeight.applyAsInt(id));
            }
            alongCursor += nodeH + maxExtra + layerGap;
        }
        return positions;
    }

    private static double layerHorizontalExtent(@NotNull List<String> layer,
                                                @NotNull Map<String, Integer> widths, int rowGap) {
        if (layer.isEmpty()) return 0;
        double extent = (layer.size() - 1) * (double) rowGap;
        for (String id : layer) extent += widths.getOrDefault(id, 0);
        return extent;
    }

    private static double layerVerticalExtent(@NotNull List<String> layer, int nodeH, int rowGap,
                                              @NotNull ToIntFunction<String> extraVerticalHeight) {
        if (layer.isEmpty()) return 0;
        double extent = (layer.size() - 1) * (double) rowGap;
        for (String id : layer) extent += nodeH + extraVerticalHeight.applyAsInt(id);
        return extent;
    }

    private static int longestPathLayer(@NotNull String id,
                                        @NotNull Map<String, List<String>> incoming,
                                        @NotNull Map<String, Integer> layer,
                                        @NotNull Set<String> visiting) {
        Integer known = layer.get(id);
        if (known != null) return known;
        if (!visiting.add(id)) return 0;
        List<String> ins = incoming.getOrDefault(id, List.of());
        int l = 0;
        for (String pred : ins) {
            l = Math.max(l, longestPathLayer(pred, incoming, layer, visiting) + 1);
        }
        visiting.remove(id);
        layer.put(id, l);
        return l;
    }

    private static @NotNull Map<String, Integer> positionMap(@NotNull List<String> layer) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < layer.size(); i++) map.put(layer.get(i), i);
        return map;
    }

    private static void barycenter(@NotNull List<List<String>> layers,
                                   int layerIdx,
                                   @NotNull Map<String, Integer> refPositions,
                                   @NotNull Map<String, List<String>> neighbours) {
        List<String> lay = layers.get(layerIdx);
        List<String> order = new ArrayList<>(lay);
        Map<String, Double> avgById = new HashMap<>();
        for (String id : lay) {
            List<String> neigh = neighbours.getOrDefault(id, List.of());
            double sum = 0;
            int count = 0;
            for (String n : neigh) {
                Integer p = refPositions.get(n);
                if (p != null) {
                    sum += p;
                    count++;
                }
            }
            avgById.put(id, count > 0 ? sum / count : 0.0);
        }
        order.sort(Comparator.comparingDouble(avgById::get));
        layers.set(layerIdx, order);
    }

    private static @NotNull Rectangle2D.Double computeBounds(@NotNull Map<String, NodePosition> positions,
                                                             int nodeH) {
        if (positions.isEmpty()) return new Rectangle2D.Double(0, 0, 0, 0);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (NodePosition p : positions.values()) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x() + p.width());
            maxY = Math.max(maxY, p.y() + nodeH);
        }
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }
}
