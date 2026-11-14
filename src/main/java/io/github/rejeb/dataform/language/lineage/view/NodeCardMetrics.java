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

import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.CardColumns;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Geometry of the part of a node card that sits under its header: the column rows, the
 * "more columns" footer and the note row, and of what is laid over a card with the recorded
 * lineage: the freshness icon and the expanders.
 */
final class NodeCardMetrics {

    static final int ROW_H = 24;
    static final int NOTE_H = 22;
    static final int TOGGLE_BAND_H = 18;
    static final int INFO_SIZE = 12;
    static final int INFO_GAP = 2;
    private static final int INFO_RIGHT = 5;
    private static final int INFO_TOP = 4;
    private static final int EXPANDER_H = 18;
    private static final int EXPANDER_MIN_W = 26;
    private static final int EXPANDER_PAD = 12;
    private static final int EXPANDER_CHAR_W = 7;
    private static final int EXPANDER_OUT_X = 12;
    private static final int EXPANDER_OUT_Y = 9;

    private NodeCardMetrics() {
    }

    /**
     * @param columns the rows of the card
     * @param note    whether the card carries a note row
     * @return the height the card needs under its header
     */
    static int rowsHeight(@NotNull CardColumns columns, boolean note) {
        return columns.rows().size() * ROW_H + (columns.hidden() > 0 ? NOTE_H : 0) + (note ? NOTE_H : 0);
    }

    /**
     * @param pos      the position of the node
     * @param nodeH    the height of the node header
     * @param rowIndex the index of the row in the card
     * @return the world bounds of that column row
     */
    static @NotNull Rectangle rowBounds(@NotNull NodePosition pos, int nodeH, int rowIndex) {
        return new Rectangle(left(pos), top(pos) + nodeH + rowIndex * ROW_H, pos.width(), ROW_H);
    }

    /**
     * @param positions the positions of the nodes
     * @param nodeH     the height of a node header
     * @param columns   the rows of the card of a node
     * @return the world bounds of every column row, by column id
     */
    static @NotNull Map<String, Rectangle> rowBounds(@NotNull Map<String, NodePosition> positions, int nodeH,
                                                     @NotNull Function<String, CardColumns> columns) {
        Map<String, Rectangle> bounds = new LinkedHashMap<>();
        for (NodePosition pos : positions.values()) {
            List<ColumnRef> rows = columns.apply(pos.id()).rows();
            for (int i = 0; i < rows.size(); i++) {
                bounds.put(rows.get(i).id(), rowBounds(pos, nodeH, i));
            }
        }
        return bounds;
    }

    /**
     * @return the world bounds of the "more columns" footer, under the last row
     */
    static @NotNull Rectangle footerBounds(@NotNull NodePosition pos, int nodeH, @NotNull CardColumns columns) {
        return new Rectangle(left(pos), top(pos) + nodeH + columns.rows().size() * ROW_H, pos.width(), NOTE_H);
    }

    /**
     * @return the world bounds of the note row, under the rows and the footer
     */
    static @NotNull Rectangle noteBounds(@NotNull NodePosition pos, int nodeH, @NotNull CardColumns columns) {
        int y = top(pos) + nodeH + columns.rows().size() * ROW_H + (columns.hidden() > 0 ? NOTE_H : 0);
        return new Rectangle(left(pos), y, pos.width(), NOTE_H);
    }

    /**
     * @return the world bounds of the freshness icon, in the top-right corner of the header
     */
    static @NotNull Rectangle infoBounds(@NotNull NodePosition pos) {
        return new Rectangle(left(pos) + pos.width() - INFO_RIGHT - INFO_SIZE, top(pos) + INFO_TOP, INFO_SIZE, INFO_SIZE);
    }

    /**
     * @param hidden the number of entities known but not loaded, negative when it is unknown
     * @return the text of the expander: "+N", or "+" when the number is unknown
     */
    static @NotNull String expanderLabel(int hidden) {
        return hidden < 0 ? "+" : "+" + hidden;
    }

    /**
     * @param cardHeight the height of the whole card, header and rows
     * @param direction  the side the expander loads
     * @param label      the text of the expander
     * @return the world bounds of the expander pill: across the top-left corner of the card for
     * upstream, across its bottom-right corner for downstream
     */
    static @NotNull Rectangle expanderBounds(@NotNull NodePosition pos, int cardHeight,
                                             @NotNull DataplexLineageClient.Direction direction, @NotNull String label) {
        int width = Math.max(EXPANDER_MIN_W, EXPANDER_PAD + EXPANDER_CHAR_W * label.length());
        if (direction == DataplexLineageClient.Direction.UPSTREAM) {
            return new Rectangle(left(pos) - EXPANDER_OUT_X, top(pos) - EXPANDER_OUT_Y, width, EXPANDER_H);
        }
        return new Rectangle(left(pos) + pos.width() + EXPANDER_OUT_X - width,
                top(pos) + cardHeight + EXPANDER_OUT_Y - EXPANDER_H, width, EXPANDER_H);
    }

    private static int left(@NotNull NodePosition pos) {
        return (int) Math.round(pos.x());
    }

    private static int top(@NotNull NodePosition pos) {
        return (int) Math.round(pos.y());
    }
}
