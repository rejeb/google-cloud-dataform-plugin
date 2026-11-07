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
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The type glyph of an action, as in the UI prototype: its letter in the colour of the type, on a
 * rounded square of the matching background. Used on the canvas nodes, in the filters, the details
 * and the legend.
 */
final class TypeGlyphIcon implements Icon {

    private static final int SIZE = 18;
    private static final int SMALL_SIZE = 16;
    private static final int ARC = 8;

    private final String type;
    private final int size;
    private final boolean small;

    private TypeGlyphIcon(@NotNull String type, int size, boolean small) {
        this.type = type;
        this.size = size;
        this.small = small;
    }

    /**
     * The glyph of the canvas nodes and of the details header.
     */
    static @NotNull TypeGlyphIcon of(@NotNull String type) {
        return new TypeGlyphIcon(type, SIZE, false);
    }

    /**
     * The smaller glyph of the list rows.
     */
    static @NotNull TypeGlyphIcon small(@NotNull String type) {
        return new TypeGlyphIcon(type, SMALL_SIZE, true);
    }

    /**
     * The action type the glyph stands for.
     */
    @NotNull String type() {
        return type;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int side = getIconWidth();
            int arc = JBUIScale.scale(ARC);
            g2.setColor(LineageTheme.glyphBackground(type));
            g2.fillRoundRect(x, y, side, side, arc, arc);
            Font font = small ? JBFont.small().lessOn(1f).asBold() : JBFont.small().asBold();
            g2.setFont(font);
            g2.setColor(LineageTheme.glyphForeground(type));
            String letter = LineageTheme.glyphFor(type);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, x + (side - fm.stringWidth(letter)) / 2,
                    y + (side + fm.getAscent() - fm.getDescent()) / 2);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return JBUIScale.scale(size);
    }

    @Override
    public int getIconHeight() {
        return JBUIScale.scale(size);
    }
}
