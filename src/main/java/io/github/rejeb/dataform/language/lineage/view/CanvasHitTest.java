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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/** Point-in-rectangle lookups over laid-out nodes and column rows, in world coordinates. */
final class CanvasHitTest {

    static final int EDGE_REACH = 6;

    private CanvasHitTest() {
    }

    /**
     * Returns the id of the node whose rectangle contains the given world point, or
     * {@code null} if none. Each node carries its own width from the layout pass.
     */
    static @Nullable String nodeAt(@NotNull Map<String, NodePosition> positions,
                                   int nodeH, double worldX, double worldY) {
        for (NodePosition p : positions.values()) {
            if (worldX >= p.x() && worldX <= p.x() + p.width()
                    && worldY >= p.y() && worldY <= p.y() + nodeH) {
                return p.id();
            }
        }
        return null;
    }

    /**
     * Returns the id of the node whose card, header and rows, contains the given world point, or
     * {@code null} if none.
     */
    static @Nullable String cardAt(@NotNull Map<String, NodePosition> positions, int nodeH,
                                   @NotNull ToIntFunction<String> extraHeight, double worldX, double worldY) {
        for (NodePosition p : positions.values()) {
            if (worldX >= p.x() && worldX <= p.x() + p.width()
                    && worldY >= p.y() && worldY <= p.y() + nodeH + extraHeight.applyAsInt(p.id())) {
                return p.id();
            }
        }
        return null;
    }

    /**
     * Returns the id of the node whose column toggle band contains the given world point, or
     * {@code null} if none. The band is the bottom {@link NodeCardMetrics#TOGGLE_BAND_H} of the
     * header over the full width of the node; nodes without columns have none.
     */
    static @Nullable String columnToggleAt(@NotNull Map<String, NodePosition> positions, int nodeH,
                                           @NotNull Predicate<String> hasColumns, double worldX, double worldY) {
        for (NodePosition p : positions.values()) {
            double bottom = p.y() + nodeH;
            if (worldX >= p.x() && worldX <= p.x() + p.width()
                    && worldY >= bottom - NodeCardMetrics.TOGGLE_BAND_H && worldY <= bottom) {
                return hasColumns.test(p.id()) ? p.id() : null;
            }
        }
        return null;
    }

    /**
     * Returns the id of the node whose freshness icon contains the given world point, or
     * {@code null} if none.
     */
    static @Nullable String infoAt(@NotNull Map<String, NodePosition> positions, double worldX, double worldY) {
        for (NodePosition p : positions.values()) {
            if (NodeCardMetrics.infoBounds(p).contains(worldX, worldY)) return p.id();
        }
        return null;
    }

    /**
     * Returns the expander containing the given world point, or {@code null} if none.
     *
     * @param cardHeight the height of the whole card of a node
     * @param hidden     the number of entities hidden on a side of a node: zero for no expander,
     *                   negative when the number is unknown
     */
    static @Nullable ExpanderHit expanderAt(@NotNull Map<String, NodePosition> positions,
                                            @NotNull ToIntFunction<String> cardHeight,
                                            @NotNull ToIntBiFunction<String, DataplexLineageClient.Direction> hidden,
                                            double worldX, double worldY) {
        for (NodePosition p : positions.values()) {
            for (DataplexLineageClient.Direction direction : DataplexLineageClient.Direction.values()) {
                int count = hidden.applyAsInt(p.id(), direction);
                if (count == 0) continue;
                Rectangle pill = NodeCardMetrics.expanderBounds(p, cardHeight.applyAsInt(p.id()), direction,
                        NodeCardMetrics.expanderLabel(count));
                if (pill.contains(worldX, worldY)) return new ExpanderHit(p.id(), direction);
            }
        }
        return null;
    }

    /**
     * Returns the edge whose curve passes within {@link #EDGE_REACH} pixels of the given world
     * point, or {@code null} if none.
     */
    static @Nullable LinkRef edgeAt(@NotNull Map<LinkRef, ? extends Shape> edges, double worldX, double worldY) {
        BasicStroke reach = new BasicStroke(2 * EDGE_REACH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        for (Map.Entry<LinkRef, ? extends Shape> edge : edges.entrySet()) {
            if (reach.createStrokedShape(edge.getValue()).contains(worldX, worldY)) return edge.getKey();
        }
        return null;
    }

    /**
     * An expander under the pointer.
     *
     * @param nodeId    the node the expander belongs to
     * @param direction the side it loads
     */
    record ExpanderHit(@NotNull String nodeId, @NotNull DataplexLineageClient.Direction direction) {
    }

    /**
     * Returns the id of the column row whose world-coordinate rectangle contains the given
     * world point, or {@code null} if none.
     */
    static @Nullable String columnAt(@NotNull Map<String, Rectangle> bounds,
                                     double worldX, double worldY) {
        for (Map.Entry<String, Rectangle> entry : bounds.entrySet()) {
            Rectangle r = entry.getValue();
            if (worldX >= r.x && worldX <= r.x + r.width
                    && worldY >= r.y && worldY < r.y + r.height) {
                return entry.getKey();
            }
        }
        return null;
    }
}
