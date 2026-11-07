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
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import javax.swing.JComponent;

/** Measures and paints a single node box: type glyph, name and schema. */
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

    private final JComponent host;
    private final LineageModel model;

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
        int total = LEFT_PAD + GLYPH + TEXT_GAP + textW + RIGHT_PAD;
        return Math.max(MIN_WIDTH, Math.min(total, max));
    }

    /** Alpha a node is painted with: faded when the selection's lineage or the search leaves it out. */
    float alphaOf(@NotNull String id) {
        return model.dimmedIds().contains(id) ? DIM_ALPHA : 1f;
    }

    void paint(@NotNull Graphics2D g2, @NotNull LineageNode node, @NotNull NodePosition pos,
               int nodeH, boolean selected) {
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaOf(pos.id())));

        int w = pos.width();
        int x = (int) Math.round(pos.x());
        int y = (int) Math.round(pos.y());

        if (model.highlightedIds().contains(pos.id())) paintMatchOutline(g2, x, y, w, nodeH);
        paintBox(g2, x, y, w, nodeH, selected, pos.id().equals(model.hoverId()));

        int glyphX = x + LEFT_PAD;
        TypeGlyphIcon.of(node.dataformType()).paintIcon(host, g2, glyphX, y + (nodeH - GLYPH) / 2);

        int textX = glyphX + GLYPH + TEXT_GAP;
        int textAvail = (x + w - RIGHT_PAD) - textX;
        if (node.disabled()) {
            textAvail -= MARKER + TEXT_GAP;
            paintDisabledMarker(g2, x + w - RIGHT_PAD - MARKER, y + (nodeH - MARKER) / 2);
        }
        paintTexts(g2, node, textX, y, nodeH, textAvail, selected);

        g2.setComposite(oldComposite);
    }

    private static void paintBox(@NotNull Graphics2D g2, int x, int y, int w, int h,
                                 boolean selected, boolean hovered) {
        g2.setColor(selected ? UIUtil.getListSelectionBackground(true) : LineageTheme.nodeBackground());
        g2.fillRoundRect(x, y, w, h, NODE_ARC, NODE_ARC);
        if (selected) {
            g2.setColor(LineageTheme.accentColor());
            g2.setStroke(new BasicStroke(2f));
        } else {
            g2.setColor(hovered ? UIUtil.getContextHelpForeground() : LineageTheme.nodeBorder());
            g2.setStroke(new BasicStroke(1f));
        }
        g2.drawRoundRect(x, y, w, h, NODE_ARC, NODE_ARC);
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
