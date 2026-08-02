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

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

/** Measures and paints a single node box: type stripe, glyph badge, name and subtitle. */
final class NodeRenderer {

    private static final int LEFT_PAD = 10;
    private static final int BADGE = 22;
    private static final int TEXT_GAP = 8;
    private static final int RIGHT_PAD = 10;
    private static final float DIM_ALPHA = 0.28f;
    private static final int MARKER = 12;

    private final JComponent host;
    private final LineageModel model;

    NodeRenderer(@NotNull JComponent host, @NotNull LineageModel model) {
        this.host = host;
        this.model = model;
    }

    /** Width a single node needs for its own label, clamped to the density minimum and a maximum. */
    int measureWidth(@NotNull String id) {
        boolean comfortable = model.density() == Density.COMFORTABLE;
        int min = comfortable ? 180 : 160;
        int max = JBUIScale.scale(460);
        LineageNode node = model.graph().node(id);
        if (node == null) return min;
        var nameFm = host.getFontMetrics(LineageTheme.monospace(comfortable ? 12f : 11f));
        int textW;
        if (comfortable) {
            var subFm = host.getFontMetrics(LineageTheme.monospace(10f));
            int nameW = nameFm.stringWidth(node.name());
            int subW = subFm.stringWidth(node.schema() + " · " + node.dataformType());
            textW = Math.max(nameW, subW);
        } else {
            textW = nameFm.stringWidth(node.name());
        }
        int total = LEFT_PAD + BADGE + TEXT_GAP + textW + RIGHT_PAD;
        return Math.max(min, Math.min(total, max));
    }

    /** Alpha a node is painted with: dimmed when a lineage highlight excludes it. */
    float alphaOf(@NotNull String id) {
        java.util.Set<String> highlight = model.highlightLineage();
        return !highlight.isEmpty() && !highlight.contains(id) ? DIM_ALPHA : 1f;
    }

    void paint(@NotNull Graphics2D g2, @NotNull LineageNode node, @NotNull NodePosition pos,
               int nodeH, boolean selected) {
        Composite oldComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaOf(pos.id())));

        int w = pos.width();
        int x = (int) Math.round(pos.x());
        int y = (int) Math.round(pos.y());
        Color type = LineageTheme.typeColor(node.dataformType());

        g2.setColor(LineageTheme.nodeBackground());
        g2.fillRoundRect(x, y, w, nodeH, 10, 10);
        if (selected) {
            g2.setColor(LineageTheme.accentColor());
            g2.setStroke(new BasicStroke(2f));
        } else {
            g2.setColor(LineageTheme.nodeBorder());
            g2.setStroke(new BasicStroke(1f));
        }
        g2.drawRoundRect(x, y, w, nodeH, 10, 10);

        g2.setColor(type);
        g2.fillRoundRect(x, y, 4, nodeH, 4, 4);

        int by = y + (nodeH - BADGE) / 2;
        int bx = x + LEFT_PAD;
        g2.setColor(LineageTheme.translucent(type, 40));
        g2.fillRoundRect(bx, by, BADGE, BADGE, 6, 6);
        g2.setColor(type);
        g2.setFont(LineageTheme.monospace(12f).deriveFont(Font.BOLD));
        String glyph = LineageTheme.glyphFor(node.dataformType());
        var fm = g2.getFontMetrics();
        g2.drawString(glyph, bx + (BADGE - fm.stringWidth(glyph)) / 2,
                by + (BADGE + fm.getAscent() - fm.getDescent()) / 2);

        int textX = bx + BADGE + TEXT_GAP;
        int textAvail = (x + w - RIGHT_PAD) - textX;
        if (node.disabled()) {
            textAvail -= MARKER + TEXT_GAP;
            paintDisabledMarker(g2, x + w - RIGHT_PAD - MARKER, y + (nodeH - MARKER) / 2);
        }
        if (model.density() == Density.COMFORTABLE) {
            g2.setColor(UIUtil.getLabelForeground());
            g2.setFont(LineageTheme.monospace(12f));
            g2.drawString(LineageTheme.clip(g2, node.name(), textAvail), textX, y + 18);
            g2.setColor(UIUtil.getLabelDisabledForeground());
            g2.setFont(LineageTheme.monospace(10f));
            g2.drawString(LineageTheme.clip(g2, node.schema() + " · " + node.dataformType(), textAvail),
                    textX, y + 32);
        } else {
            g2.setColor(UIUtil.getLabelForeground());
            g2.setFont(LineageTheme.monospace(11f));
            var nfm = g2.getFontMetrics();
            int ty = y + (nodeH + nfm.getAscent() - nfm.getDescent()) / 2;
            g2.drawString(LineageTheme.clip(g2, node.name(), textAvail), textX, ty);
        }

        g2.setComposite(oldComposite);
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
