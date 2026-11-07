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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.geom.RoundRectangle2D;
import java.awt.Graphics2D;
import java.awt.Shape;

/**
 * Draws the bottom-right overview map, as in the UI prototype: a rounded panel box with the nodes
 * in grey, the selected one in blue, and a blue rectangle showing the visible area, kept inside
 * the box.
 */
final class MinimapRenderer {

    private static final int WIDTH = 170;
    private static final int HEIGHT = 104;
    private static final int MARGIN = 14;
    private static final int PADDING = 8;
    private static final int ARC = 12;
    private static final int VIEW_ARC = 6;
    private static final double NODE_ARC = 4;

    private final JComponent host;
    private final LineageModel model;
    private final CanvasViewport viewport;

    MinimapRenderer(@NotNull JComponent host, @NotNull LineageModel model,
                    @NotNull CanvasViewport viewport) {
        this.host = host;
        this.model = model;
        this.viewport = viewport;
    }

    void paint(@NotNull Graphics2D g2, @NotNull LayoutResult layout) {
        int mmW = JBUIScale.scale(WIDTH);
        int mmH = JBUIScale.scale(HEIGHT);
        int margin = JBUIScale.scale(MARGIN);
        int inset = JBUIScale.scale(PADDING);
        int arc = JBUIScale.scale(ARC);
        int mx = host.getWidth() - mmW - margin;
        int my = host.getHeight() - mmH - margin;

        var b = layout.bounds();
        if (b.width <= 0 || b.height <= 0) return;

        g2.setColor(UIUtil.getPanelBackground());
        g2.fillRoundRect(mx, my, mmW, mmH, arc, arc);
        g2.setColor(JBUI.CurrentTheme.Separator.color());
        g2.drawRoundRect(mx, my, mmW - 1, mmH - 1, arc, arc);

        int ix = mx + 1;
        int iy = my + 1;
        int iw = mmW - 2;
        int ih = mmH - 2;
        double availW = iw - 2.0 * inset;
        double availH = ih - 2.0 * inset;
        double s = Math.min(availW / b.width, availH / b.height);
        double ox = ix + inset + (availW - b.width * s) / 2.0 - b.x * s;
        double oy = iy + inset + (availH - b.height * s) / 2.0 - b.y * s;

        Shape clip = g2.getClip();
        g2.clip(new RoundRectangle2D.Double(ix, iy, iw, ih, arc - 2, arc - 2));
        for (NodePosition pos : layout.positions().values()) {
            if (model.graph().node(pos.id()) == null) continue;
            g2.setColor(pos.id().equals(model.selectedId()) ? LineageTheme.accentColor() : LineageTheme.minimapNode());
            double nx = ox + pos.x() * s;
            double ny = oy + pos.y() * s;
            double nw = Math.max(3, pos.width() * s);
            double nh = Math.max(2, layout.nodeH() * s);
            g2.fill(new RoundRectangle2D.Double(nx, ny, nw, nh, NODE_ARC, NODE_ARC));
        }

        double vx = ox + viewport.worldX(0) * s;
        double vy = oy + viewport.worldY(0) * s;
        double vw = (viewport.worldX(host.getWidth()) - viewport.worldX(0)) * s;
        double vh = (viewport.worldY(host.getHeight()) - viewport.worldY(0)) * s;
        int rx = (int) Math.round(Math.max(ix, vx));
        int ry = (int) Math.round(Math.max(iy, vy));
        int rw = (int) Math.round(Math.min(iw, vw)) - 1;
        int rh = (int) Math.round(Math.min(ih, vh)) - 1;
        int viewArc = JBUIScale.scale(VIEW_ARC);
        g2.setColor(LineageTheme.accentColor());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(rx, ry, rw, rh, viewArc, viewArc);
        g2.setClip(clip);
    }
}
