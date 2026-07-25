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
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.layout.LayoutResult;
import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

/** Draws the bottom-right overview map with a rectangle showing the visible area. */
final class MinimapRenderer {

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
        int mmW = JBUIScale.scale(180);
        int mmH = JBUIScale.scale(120);
        int margin = JBUIScale.scale(12);
        int inset = JBUIScale.scale(4);
        int mx = host.getWidth() - mmW - margin;
        int my = host.getHeight() - mmH - margin;

        var b = layout.bounds();
        if (b.width <= 0 || b.height <= 0) return;
        double availW = mmW - 2.0 * inset;
        double availH = mmH - 2.0 * inset;
        double s = Math.min(availW / b.width, availH / b.height);
        double ox = mx + inset + (availW - b.width * s) / 2.0 - b.x * s;
        double oy = my + inset + (availH - b.height * s) / 2.0 - b.y * s;

        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(mx, my, mmW, mmH, 8, 8);
        g2.setColor(LineageTheme.edgeColor());
        g2.drawRoundRect(mx, my, mmW, mmH, 8, 8);

        for (NodePosition pos : layout.positions().values()) {
            LineageNode node = model.graph().node(pos.id());
            if (node == null) continue;
            g2.setColor(LineageTheme.typeColor(node.dataformType()));
            int nx = (int) Math.round(ox + pos.x() * s);
            int ny = (int) Math.round(oy + pos.y() * s);
            int nw = Math.max(2, (int) Math.round(pos.width() * s));
            int nh = Math.max(2, (int) Math.round(layout.nodeH() * s));
            g2.fillRect(nx, ny, nw, nh);
        }

        double vx = ox + viewport.worldX(0) * s;
        double vy = oy + viewport.worldY(0) * s;
        double vw = (viewport.worldX(host.getWidth()) - viewport.worldX(0)) * s;
        double vh = (viewport.worldY(host.getHeight()) - viewport.worldY(0)) * s;
        Shape clip = g2.getClip();
        g2.setClip(mx, my, mmW, mmH);
        g2.setColor(LineageTheme.accentColor());
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRect((int) Math.round(vx), (int) Math.round(vy),
                (int) Math.round(vw), (int) Math.round(vh));
        g2.setClip(clip);
    }
}
