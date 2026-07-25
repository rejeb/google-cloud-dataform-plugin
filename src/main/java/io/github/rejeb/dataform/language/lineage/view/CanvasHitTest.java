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

import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.Map;

/** Point-in-rectangle lookups over laid-out nodes and column rows, in world coordinates. */
final class CanvasHitTest {

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
     * Returns the id of the column row whose world-coordinate rectangle contains the given
     * world point, or {@code null} if none.
     */
    static @Nullable String columnAt(@NotNull Map<String, Rectangle> bounds,
                                     double worldX, double worldY) {
        for (Map.Entry<String, Rectangle> entry : bounds.entrySet()) {
            Rectangle r = entry.getValue();
            if (worldX >= r.x && worldX <= r.x + r.width
                    && worldY >= r.y && worldY <= r.y + r.height) {
                return entry.getKey();
            }
        }
        return null;
    }
}
