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

        int alongNode = direction == Direction.TB ? nodeH : nodeW;
        int acrossNode = direction == Direction.TB ? nodeW : nodeH;

        Map<String, NodePosition> positions = new LinkedHashMap<>();
        int maxRows = layers.stream().mapToInt(List::size).max().orElse(0);
        double totalAcross = maxRows * acrossNode + (double) (maxRows - 1) * rowGap;

        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> lay = layers.get(layerIdx);
            double layerAcross = lay.size() * acrossNode + (double) (lay.size() - 1) * rowGap;
            double acrossStart = (totalAcross - layerAcross) / 2.0;
            for (int i = 0; i < lay.size(); i++) {
                String id = lay.get(i);
                double along = layerIdx * (alongNode + (double) layerGap);
                double across = acrossStart + i * (acrossNode + (double) rowGap);
                double x = direction == Direction.TB ? across : along;
                double y = direction == Direction.TB ? along : across;
                positions.put(id, new NodePosition(id, x, y, layerIdx));
            }
        }

        return new LayoutResult(positions, computeBounds(positions, nodeW, nodeH), nodeW, nodeH);
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
                                                             int nodeW, int nodeH) {
        if (positions.isEmpty()) return new Rectangle2D.Double(0, 0, 0, 0);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (NodePosition p : positions.values()) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x() + nodeW);
            maxY = Math.max(maxY, p.y() + nodeH);
        }
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }
}
