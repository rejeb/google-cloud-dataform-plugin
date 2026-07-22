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

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * Custom toolbar icons matching the design reference's inline SVG glyphs (filters funnel,
 * layout direction, density, minimap, re-layout). Painted from a 16-unit view box and
 * scaled to the current DPI; stroke colour is theme-aware.
 */
final class LineageIcons {

    static final Icon FILTERS = icon(g -> {
        g.draw(new Line2D.Double(2, 4, 14, 4));
        g.draw(new Line2D.Double(4, 8, 12, 8));
        g.draw(new Line2D.Double(6, 12, 10, 12));
    });

    static final Icon DIRECTION_LR = icon(g -> {
        g.draw(new RoundRectangle2D.Double(1.5, 5.5, 3, 5, 1, 1));
        g.draw(new RoundRectangle2D.Double(11.5, 5.5, 3, 5, 1, 1));
        g.draw(new Line2D.Double(4.5, 8, 11.5, 8));
        g.draw(polyline(9.5, 6, 11.5, 8, 9.5, 10));
    });

    static final Icon DIRECTION_TB = icon(g -> {
        g.draw(new RoundRectangle2D.Double(5.5, 1.5, 5, 3, 1, 1));
        g.draw(new RoundRectangle2D.Double(5.5, 11.5, 5, 3, 1, 1));
        g.draw(new Line2D.Double(8, 4.5, 8, 11.5));
        g.draw(polyline(6, 9.5, 8, 11.5, 10, 9.5));
    });

    static final Icon DENSITY_COMPACT = icon(g -> {
        for (double y : new double[]{4, 7, 10, 13}) g.draw(new Line2D.Double(2, y, 14, y));
    });

    static final Icon DENSITY_COMFORTABLE = icon(g -> {
        for (double y : new double[]{4, 8, 12}) g.draw(new Line2D.Double(2, y, 14, y));
    });

    static final Icon MINIMAP = icon(g -> {
        g.draw(new RoundRectangle2D.Double(1.5, 1.5, 13, 13, 2, 2));
        Color prev = g.getColor();
        g.setColor(new Color(prev.getRed(), prev.getGreen(), prev.getBlue(), 77));
        g.fill(new RoundRectangle2D.Double(9, 9, 4.5, 4.5, 1, 1));
        g.setColor(prev);
        g.draw(new RoundRectangle2D.Double(9, 9, 4.5, 4.5, 1, 1));
    });

    static final Icon RELAYOUT = icon(g -> {
        g.draw(new Arc2D.Double(3, 3, 10, 10, 200, 300, Arc2D.OPEN));
        g.draw(new Line2D.Double(8, 13, 5.5, 13));
        g.draw(new Line2D.Double(8, 13, 8, 10.5));
    });

    static final Icon FIT = icon(g -> {
        g.draw(new RoundRectangle2D.Double(1.5, 3.5, 13, 9, 2, 2));
        g.draw(new Line2D.Double(5, 8, 11, 8));
        g.draw(polyline(6.5, 6.5, 5, 8, 6.5, 9.5));
        g.draw(polyline(9.5, 6.5, 11, 8, 9.5, 9.5));
    });

    static final Icon OPEN_FULL = icon(g -> {
        g.draw(polyline(9.5, 2.5, 13.5, 2.5, 13.5, 6.5));
        g.draw(polyline(6.5, 13.5, 2.5, 13.5, 2.5, 9.5));
        g.draw(new Line2D.Double(13.5, 2.5, 8.5, 7.5));
        g.draw(new Line2D.Double(2.5, 13.5, 7.5, 8.5));
    });

    private LineageIcons() {
    }

    private static @NotNull Path2D.Double polyline(double... coords) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(coords[0], coords[1]);
        for (int i = 2; i < coords.length; i += 2) path.lineTo(coords[i], coords[i + 1]);
        return path;
    }

    private static @NotNull Color strokeColor() {
        return new JBColor(new Color(0x5A5D63), new Color(0xAFB1B3));
    }

    private static @NotNull Icon icon(@NotNull Consumer<Graphics2D> painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.translate(x, y);
                    double scale = getIconWidth() / 16.0;
                    g2.scale(scale, scale);
                    g2.setColor(strokeColor());
                    g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    painter.accept(g2);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return JBUI.scale(16);
            }

            @Override
            public int getIconHeight() {
                return JBUI.scale(16);
            }
        };
    }
}
