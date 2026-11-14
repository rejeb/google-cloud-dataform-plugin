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

import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.CardColumns;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Paints the column rows of a node card, under its header: one row per column with its name and
 * type, the "more columns" footer and the note row. A row on a shown lineage is tinted with the
 * colour of that lineage; the column the lineage starts from is outlined and bold.
 */
final class ColumnListRenderer {

    static final int PAD = 10;
    private static final int GAP = 8;
    private static final int NODE_ARC = 14;
    private static final int LINEAGE_TINT_ALPHA = 56;
    private static final int SEARCH_MARK_ALPHA = 90;
    private static final float DIM_ALPHA = 0.28f;

    private final LineageModel model;
    private @Nullable String hoverColumnId;

    ColumnListRenderer(@NotNull LineageModel model) {
        this.model = model;
    }

    /**
     * Sets the row under the pointer.
     *
     * @return {@code true} when it changed and a repaint is needed
     */
    boolean setHoverColumn(@Nullable String columnId) {
        if (Objects.equals(hoverColumnId, columnId)) return false;
        hoverColumnId = columnId;
        return true;
    }

    /**
     * @return the height the card of the node needs under its header, {@code 0} when it lists no column
     */
    int extraHeight(@NotNull String nodeId) {
        return NodeCardMetrics.rowsHeight(model.cardColumns(nodeId), model.cardNote(nodeId) != null);
    }

    /**
     * Paints the rows of the card of a node inside the box {@link NodeRenderer} painted for it.
     *
     * @param nodeH the height of the header
     */
    void paintRows(@NotNull Graphics2D g2, @NotNull String nodeId, @NotNull NodePosition pos, int nodeH) {
        CardColumns columns = model.cardColumns(nodeId);
        String note = model.cardNote(nodeId);
        int extra = NodeCardMetrics.rowsHeight(columns, note != null);
        if (extra == 0) return;

        boolean selected = nodeId.equals(model.selectedId());
        Composite oldComposite = g2.getComposite();
        Shape oldClip = g2.getClip();
        Font oldFont = g2.getFont();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                model.dimmedIds().contains(nodeId) ? DIM_ALPHA : 1f));
        g2.clip(interior(pos, nodeH + extra, selected ? 2 : 1));

        List<ColumnRef> rows = columns.rows();
        for (int i = 0; i < rows.size(); i++) {
            paintRow(g2, rows.get(i), NodeCardMetrics.rowBounds(pos, nodeH, i), selected);
        }
        if (columns.hidden() > 0) {
            paintNote(g2, "+ " + columns.hidden() + " more columns", UIUtil.getContextHelpForeground(),
                    NodeCardMetrics.footerBounds(pos, nodeH, columns), selected);
        }
        if (note != null) {
            paintNote(g2, note, LineageTheme.glyphForeground("operations"),
                    NodeCardMetrics.noteBounds(pos, nodeH, columns), selected);
        }

        g2.setFont(oldFont);
        g2.setClip(oldClip);
        g2.setComposite(oldComposite);
    }

    private static @NotNull Shape interior(@NotNull NodePosition pos, int height, int border) {
        return new RoundRectangle2D.Double(Math.round(pos.x()) + border, Math.round(pos.y()) + border,
                pos.width() - 2 * border + 1, height - 2 * border + 1, NODE_ARC, NODE_ARC);
    }

    private void paintRow(@NotNull Graphics2D g2, @NotNull ColumnRef column, @NotNull Rectangle row,
                          boolean onSelectedNode) {
        Integer colourSlot = model.lineageColourIndex().get(column.id());
        Color lineage = colourSlot == null ? null : LineageTheme.selectionColor(colourSlot);
        boolean root = model.isRootColumn(column.id());

        paintRowBackground(g2, row, lineage, column.id().equals(hoverColumnId), onSelectedNode);
        g2.setColor(JBUI.CurrentTheme.Separator.color());
        g2.fillRect(row.x, row.y, row.width, 1);
        if (root) {
            g2.setColor(lineage != null ? lineage : LineageTheme.searchMatch());
            g2.drawRect(row.x + 1, row.y + 1, row.width - 2, row.height - 2);
        }

        String type = model.columnType(column);
        int right = row.x + row.width - PAD;
        if (type != null) {
            g2.setFont(LineageTheme.columnTypeFont());
            FontMetrics typeFm = g2.getFontMetrics();
            String shown = LineageTheme.clip(g2, type, row.width / 2 - PAD);
            right -= typeFm.stringWidth(shown);
            g2.setColor(UIUtil.getContextHelpForeground());
            g2.drawString(shown, right, baseline(row, typeFm));
            right -= GAP;
        }

        Font nameFont = LineageTheme.columnFont();
        g2.setFont(root ? nameFont.deriveFont(Font.BOLD) : nameFont);
        FontMetrics nameFm = g2.getFontMetrics();
        int left = row.x + PAD;
        String name = LineageTheme.clip(g2, column.columnName(), right - left);
        paintSearchMark(g2, name, left, row, nameFm);
        g2.setColor(UIUtil.getLabelForeground());
        g2.drawString(name, left, baseline(row, nameFm));
    }

    private static void paintRowBackground(@NotNull Graphics2D g2, @NotNull Rectangle row, @Nullable Color lineage,
                                           boolean hover, boolean onSelectedNode) {
        if (onSelectedNode) {
            g2.setColor(LineageTheme.nodeBackground());
            g2.fillRect(row.x, row.y, row.width, row.height);
        }
        if (lineage != null) {
            g2.setColor(ColorUtil.toAlpha(lineage, LINEAGE_TINT_ALPHA));
            g2.fillRect(row.x, row.y, row.width, row.height);
        } else if (hover) {
            g2.setColor(LineageTheme.rowHover());
            g2.fillRect(row.x, row.y, row.width, row.height);
        }
    }

    private void paintSearchMark(@NotNull Graphics2D g2, @NotNull String shownName, int left, @NotNull Rectangle row,
                                 @NotNull FontMetrics fm) {
        String needle = model.searchQuery().trim().toLowerCase(Locale.ROOT);
        if (model.searchScope() != SearchScope.COLUMNS || needle.isEmpty()) return;
        int at = shownName.toLowerCase(Locale.ROOT).indexOf(needle);
        if (at < 0) return;
        int from = left + fm.stringWidth(shownName.substring(0, at));
        int width = fm.stringWidth(shownName.substring(at, at + needle.length()));
        int height = fm.getAscent() + fm.getDescent();
        g2.setColor(ColorUtil.toAlpha(LineageTheme.searchMatch(), SEARCH_MARK_ALPHA));
        g2.fillRoundRect(from - 1, row.y + (row.height - height) / 2, width + 2, height, 4, 4);
    }

    private static void paintNote(@NotNull Graphics2D g2, @NotNull String text, @NotNull Color colour,
                                  @NotNull Rectangle row, boolean onSelectedNode) {
        if (onSelectedNode) {
            g2.setColor(LineageTheme.nodeBackground());
            g2.fillRect(row.x, row.y, row.width, row.height);
        }
        g2.setColor(JBUI.CurrentTheme.Separator.color());
        g2.fillRect(row.x, row.y, row.width, 1);
        g2.setFont(LineageTheme.columnTypeFont());
        g2.setColor(colour);
        g2.drawString(LineageTheme.clip(g2, text, row.width - 2 * PAD), row.x + PAD, baseline(row, g2.getFontMetrics()));
    }

    private static int baseline(@NotNull Rectangle row, @NotNull FontMetrics fm) {
        return row.y + (row.height + fm.getAscent() - fm.getDescent()) / 2;
    }
}
