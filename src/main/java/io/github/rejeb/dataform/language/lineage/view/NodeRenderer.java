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

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexGraphBuilder;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * Measures and paints a single node card: its box, as tall as the header plus the column rows, the
 * band that opens and closes the columns, the type glyph, the name and the schema. A node outside
 * the repository has a dashed border on the graph background. With the recorded lineage a header
 * carries a freshness icon and a card can carry expanders across its corners.
 */
final class NodeRenderer {

    private static final int MIN_WIDTH = 170;
    private static final int LEFT_PAD = 10;
    private static final int GLYPH = 18;
    private static final int NODE_ARC = 14;
    private static final int TEXT_GAP = 8;
    private static final int RIGHT_PAD = 10;
    private static final float DIM_ALPHA = 0.28f;
    private static final int MARKER = 12;
    private static final int MATCH_RING = 2;
    private static final int CHEVRON_W = 8;
    private static final int CHEVRON_H = 4;
    private static final int CHEVRON_PAD = 6;
    private static final float[] OUTSIDE_DASH = {4f, 3f};

    private final JComponent host;
    private final LineageModel model;
    private @Nullable String hoverToggleId;
    private @Nullable String hoverInfoId;
    private @Nullable CanvasHitTest.ExpanderHit hoverExpander;

    NodeRenderer(@NotNull JComponent host, @NotNull LineageModel model) {
        this.host = host;
        this.model = model;
    }

    /** Width a single node needs for its own label, clamped to the prototype minimum and a maximum. */
    int measureWidth(@NotNull String id) {
        int max = JBUIScale.scale(460);
        LineageNode node = model.graph().node(id);
        if (node == null) return MIN_WIDTH;
        int textW = host.getFontMetrics(LineageTheme.nodeNameFont()).stringWidth(node.name());
        if (model.density() == Density.COMFORTABLE) {
            textW = Math.max(textW, host.getFontMetrics(LineageTheme.nodeSchemaFont()).stringWidth(node.schema()));
        }
        int total = LEFT_PAD + GLYPH + TEXT_GAP + textW + RIGHT_PAD + infoWidth();
        String note = model.cardNote(id);
        if (note != null) {
            int noteW = host.getFontMetrics(LineageTheme.columnTypeFont()).stringWidth(note);
            total = Math.max(total, noteW + 2 * ColumnListRenderer.PAD);
        }
        return Math.max(MIN_WIDTH, Math.min(total, max));
    }

    private int infoWidth() {
        return model.source() == LineageSource.DATAPLEX ? NodeCardMetrics.INFO_SIZE + NodeCardMetrics.INFO_GAP : 0;
    }

    private static boolean isOutside(@NotNull LineageNode node) {
        return DataplexGraphBuilder.TYPE_OUTSIDE.equals(node.dataformType())
                || DataplexGraphBuilder.TYPE_ASSET.equals(node.dataformType());
    }

    /**
     * Sets the node whose freshness icon is under the pointer.
     *
     * @return {@code true} when it changed and a repaint is needed
     */
    boolean setHoverInfo(@Nullable String nodeId) {
        if (Objects.equals(hoverInfoId, nodeId)) return false;
        hoverInfoId = nodeId;
        return true;
    }

    /**
     * Sets the expander under the pointer.
     *
     * @return {@code true} when it changed and a repaint is needed
     */
    boolean setHoverExpander(@Nullable CanvasHitTest.ExpanderHit expander) {
        if (Objects.equals(hoverExpander, expander)) return false;
        hoverExpander = expander;
        return true;
    }

    /**
     * Paints the expanders of a node over its corners: "+N" for the entities recorded on a side but
     * not loaded, "+" when their number is unknown, nothing for zero.
     *
     * @param cardHeight the height of the whole card, header and rows
     */
    void paintExpanders(@NotNull Graphics2D g2, @NotNull NodePosition pos, int cardHeight, int upstream, int downstream) {
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaOf(pos.id())));
        if (upstream != 0) paintExpander(g2, pos, cardHeight, DataplexLineageClient.Direction.UPSTREAM, upstream);
        if (downstream != 0) paintExpander(g2, pos, cardHeight, DataplexLineageClient.Direction.DOWNSTREAM, downstream);
        g2.setComposite(oldComposite);
    }

    private void paintExpander(@NotNull Graphics2D g2, @NotNull NodePosition pos, int cardHeight,
                               @NotNull DataplexLineageClient.Direction direction, int hidden) {
        String label = NodeCardMetrics.expanderLabel(hidden);
        Rectangle pill = NodeCardMetrics.expanderBounds(pos, cardHeight, direction, label);
        boolean hovered = new CanvasHitTest.ExpanderHit(pos.id(), direction).equals(hoverExpander);
        g2.setColor(UIUtil.getPanelBackground());
        g2.fillRoundRect(pill.x, pill.y, pill.width, pill.height, pill.height, pill.height);
        g2.setColor(hovered ? LineageTheme.accentColor() : UIUtil.getContextHelpForeground());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(pill.x, pill.y, pill.width, pill.height, pill.height, pill.height);
        g2.setFont(LineageTheme.columnTypeFont().deriveFont(Font.BOLD));
        g2.setColor(UIUtil.getLabelForeground());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, pill.x + (pill.width - fm.stringWidth(label)) / 2,
                pill.y + (pill.height + fm.getAscent() - fm.getDescent()) / 2);
    }

    /** Alpha a node is painted with: faded when the selection's lineage or the search leaves it out. */
    float alphaOf(@NotNull String id) {
        return model.dimmedIds().contains(id) ? DIM_ALPHA : 1f;
    }

    /**
     * Sets the node whose toggle band is under the pointer.
     *
     * @return {@code true} when it changed and a repaint is needed
     */
    boolean setHoverToggle(@Nullable String nodeId) {
        if (Objects.equals(hoverToggleId, nodeId)) return false;
        hoverToggleId = nodeId;
        return true;
    }

    /** The shape of a node card: the header and {@code extra} pixels of rows under it. */
    static @NotNull Shape cardShape(@NotNull NodePosition pos, int nodeH, int extra) {
        return new RoundRectangle2D.Double(Math.round(pos.x()), Math.round(pos.y()), pos.width(),
                nodeH + extra, NODE_ARC, NODE_ARC);
    }

    void paint(@NotNull Graphics2D g2, @NotNull LineageNode node, @NotNull NodePosition pos,
               int nodeH, boolean selected) {
        paint(g2, node, pos, nodeH, 0, selected);
    }

    /**
     * Paints the card of a node.
     *
     * @param nodeH the height of the header
     * @param extra the height of the column rows under the header, painted by {@link ColumnListRenderer}
     */
    void paint(@NotNull Graphics2D g2, @NotNull LineageNode node, @NotNull NodePosition pos,
               int nodeH, int extra, boolean selected) {
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaOf(pos.id())));

        int w = pos.width();
        int x = (int) Math.round(pos.x());
        int y = (int) Math.round(pos.y());
        boolean hovered = pos.id().equals(model.hoverId());

        if (model.highlightedIds().contains(pos.id())) paintMatchOutline(g2, x, y, w, nodeH + extra);
        paintBox(g2, x, y, w, nodeH + extra, selected, hovered, pos.id().equals(hoverToggleId) ? nodeH : -1,
                isOutside(node));
        if (model.hasColumns(pos.id()) && (hovered || model.isExpanded(pos.id()))) {
            paintChevron(g2, x + w - CHEVRON_PAD - CHEVRON_W, y + nodeH - NodeCardMetrics.TOGGLE_BAND_H / 2,
                    model.isExpanded(pos.id()), pos.id().equals(hoverToggleId));
        }

        int glyphX = x + LEFT_PAD;
        TypeGlyphIcon.of(node.dataformType()).paintIcon(host, g2, glyphX, y + (nodeH - GLYPH) / 2);

        int textX = glyphX + GLYPH + TEXT_GAP;
        int textAvail = (x + w - RIGHT_PAD) - textX - infoWidth();
        if (model.source() == LineageSource.DATAPLEX) {
            Rectangle info = NodeCardMetrics.infoBounds(pos);
            LineageTheme.infoIcon(selected || pos.id().equals(hoverInfoId)).paintIcon(host, g2, info.x, info.y);
        }
        if (node.disabled()) {
            textAvail -= MARKER + TEXT_GAP;
            paintDisabledMarker(g2, x + w - RIGHT_PAD - infoWidth() - MARKER, y + (nodeH - MARKER) / 2);
        }
        paintTexts(g2, node, textX, y, nodeH, textAvail, selected);

        g2.setComposite(oldComposite);
    }

    private static void paintBox(@NotNull Graphics2D g2, int x, int y, int w, int h,
                                 boolean selected, boolean hovered, int bandBottom, boolean outside) {
        g2.setColor(selected ? UIUtil.getListSelectionBackground(true)
                : outside ? LineageTheme.graphBackground() : LineageTheme.nodeBackground());
        g2.fillRoundRect(x, y, w, h, NODE_ARC, NODE_ARC);
        if (bandBottom >= 0) {
            Shape oldClip = g2.getClip();
            g2.clip(new RoundRectangle2D.Double(x, y, w, h, NODE_ARC, NODE_ARC));
            g2.setColor(LineageTheme.toggleBand());
            g2.fillRect(x, y + bandBottom - NodeCardMetrics.TOGGLE_BAND_H, w, NodeCardMetrics.TOGGLE_BAND_H);
            g2.setClip(oldClip);
        }
        float width = selected ? 2f : 1f;
        if (selected) {
            g2.setColor(LineageTheme.accentColor());
        } else if (hovered) {
            g2.setColor(UIUtil.getContextHelpForeground());
        } else {
            g2.setColor(outside ? LineageTheme.outsideBorder() : LineageTheme.nodeBorder());
        }
        g2.setStroke(outside
                ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, OUTSIDE_DASH, 0f)
                : new BasicStroke(width));
        g2.drawRoundRect(x, y, w, h, NODE_ARC, NODE_ARC);
        g2.setStroke(new BasicStroke(1f));
    }

    private static void paintChevron(@NotNull Graphics2D g2, int x, int centerY, boolean up, boolean strong) {
        g2.setColor(strong ? UIUtil.getLabelForeground() : UIUtil.getContextHelpForeground());
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double tip = up ? centerY - CHEVRON_H / 2.0 : centerY + CHEVRON_H / 2.0;
        double ends = up ? centerY + CHEVRON_H / 2.0 : centerY - CHEVRON_H / 2.0;
        Path2D.Double chevron = new Path2D.Double();
        chevron.moveTo(x, ends);
        chevron.lineTo(x + CHEVRON_W / 2.0, tip);
        chevron.lineTo(x + CHEVRON_W, ends);
        g2.draw(chevron);
        g2.setStroke(new BasicStroke(1f));
    }

    private static void paintMatchOutline(@NotNull Graphics2D g2, int x, int y, int w, int h) {
        int ring = MATCH_RING;
        g2.setColor(LineageTheme.searchMatch());
        g2.setStroke(new BasicStroke(ring));
        g2.drawRoundRect(x - ring / 2 - 1, y - ring / 2 - 1, w + ring + 2, h + ring + 2,
                NODE_ARC + 2 * ring, NODE_ARC + 2 * ring);
        g2.setStroke(new BasicStroke(1f));
    }

    private void paintTexts(@NotNull Graphics2D g2, @NotNull LineageNode node, int x, int y, int nodeH,
                            int available, boolean selected) {
        FontMetrics nameFm = g2.getFontMetrics(LineageTheme.nodeNameFont());
        g2.setColor(UIUtil.getLabelForeground());
        g2.setFont(LineageTheme.nodeNameFont());
        if (model.density() != Density.COMFORTABLE) {
            int baseline = y + (nodeH + nameFm.getAscent() - nameFm.getDescent()) / 2;
            g2.drawString(LineageTheme.clip(g2, node.name(), available), x, baseline);
            return;
        }
        FontMetrics schemaFm = g2.getFontMetrics(LineageTheme.nodeSchemaFont());
        int top = y + (nodeH - nameFm.getHeight() - schemaFm.getHeight()) / 2;
        g2.drawString(LineageTheme.clip(g2, node.name(), available), x, top + nameFm.getAscent());
        g2.setColor(selected ? LineageTheme.selectedSchema() : UIUtil.getContextHelpForeground());
        g2.setFont(LineageTheme.nodeSchemaFont());
        g2.drawString(LineageTheme.clip(g2, node.schema(), available), x,
                top + nameFm.getHeight() + schemaFm.getAscent());
    }

    /**
     * Draws the "no entry" marker of a disabled action: a circle crossed by a diagonal bar. The
     * action keeps its place in the flow, so the node is still painted; the marker is what tells
     * the reader nothing is executed and no column lineage is computed for it.
     */
    private void paintDisabledMarker(@NotNull Graphics2D g2, int x, int y) {
        g2.setColor(UIUtil.getLabelDisabledForeground());
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(x, y, MARKER, MARKER);
        double inset = MARKER * 0.5 - MARKER * 0.5 / Math.sqrt(2);
        g2.draw(new Line2D.Double(x + inset, y + MARKER - inset, x + MARKER - inset, y + inset));
        g2.setStroke(new BasicStroke(1f));
    }
}
