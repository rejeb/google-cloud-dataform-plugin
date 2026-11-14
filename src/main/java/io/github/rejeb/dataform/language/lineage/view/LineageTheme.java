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

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Colours, fonts and text helpers shared by every lineage view component. Every colour is a
 * theme key under {@code Dataform.Lineage.} with light and dark fallbacks.
 */
public final class LineageTheme {

    private static final String THEME_KEY_PREFIX = "Dataform.Lineage.";
    private static final int[][] SELECTION_PALETTE = {
            {0x1F9E7A, 0x4FC3A1}, {0x9B4FB5, 0xC98BDB}, {0xB5762A, 0xE0AD7A}, {0xC9424D, 0xE06C75}, {0x1E8C99, 0x56B6C2}
    };

    private static Font monoBase;

    private LineageTheme() {
    }

    public static @NotNull Font monospace(float size) {
        if (monoBase == null) monoBase = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        return monoBase.deriveFont(JBUIScale.scale(size));
    }

    /**
     * Font of a node name: the bold label font.
     */
    public static @NotNull Font nodeNameFont() {
        return JBFont.label().asBold();
    }

    /**
     * Font of the schema line under a node name: the small label font.
     */
    public static @NotNull Font nodeSchemaFont() {
        return JBFont.medium();
    }

    /**
     * Background of the graph area: the editor background, as in the code editor next to it.
     */
    public static @NotNull Color graphBackground() {
        return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    }

    /**
     * Colour of the dots of the graph background grid.
     */
    public static @NotNull Color graphDot() {
        return themed("GraphDot", 0xEBECF0, 0x2A2C31);
    }

    public static @NotNull Color nodeBackground() {
        return themed("NodeBackground", 0xFFFFFF, 0x2B2D30);
    }

    public static @NotNull Color nodeBorder() {
        return themed("NodeBorder", 0xD3D5DB, 0x43454A);
    }

    public static @NotNull Color accentColor() {
        return themed("Accent", 0x3574F0, 0x548AF7);
    }

    public static @NotNull Color edgeColor() {
        return themed("Edge", 0xA8ADBD, 0x5A5D63);
    }

    /**
     * Colour of an edge Knowledge Catalog recorded and the repository does not declare.
     */
    public static @NotNull Color differenceColor() {
        return themed("Difference", 0xC77C1A, 0xE0AD7A);
    }

    /**
     * Colour of the dashed border of a node that is outside the repository.
     */
    public static @NotNull Color outsideBorder() {
        return themed("OutsideBorder", 0x9DA0A8, 0x6F737A);
    }

    /**
     * The freshness icon of a node header: a circled "i", 12 pixels wide.
     *
     * @param strong whether the pointer is over it or its node is selected
     */
    public static @NotNull Icon infoIcon(boolean strong) {
        return new InfoIcon(strong);
    }

    /**
     * Colour of the outline drawn around the nodes matching the search.
     */
    public static @NotNull Color searchMatch() {
        return themed("SearchMatch", 0xFFAF0F, 0xF2C55C);
    }

    /**
     * Colour of the nodes drawn in the minimap.
     */
    public static @NotNull Color minimapNode() {
        return themed("MinimapNode", 0xDFE1E5, 0x43454A);
    }

    /**
     * Colour of the schema line of the selected node, readable on the selection background.
     */
    public static @NotNull Color selectedSchema() {
        return themed("SelectedSchema", 0x565A66, 0xB4B8BF);
    }

    /**
     * Background of the type glyph of an action.
     */
    public static @NotNull Color glyphBackground(@Nullable String type) {
        return switch (glyphKind(type)) {
            case "Table" -> themed("Glyph.Table.Background", 0xDDF2E0, 0x24382A);
            case "View" -> themed("Glyph.View.Background", 0xE1ECFF, 0x233150);
            case "Incremental" -> themed("Glyph.Incremental.Background", 0xF3E6F5, 0x3A2F3A);
            case "Operation" -> themed("Glyph.Operation.Background", 0xFCEBDA, 0x3D3123);
            case "Test" -> themed("Glyph.Test.Background", 0xDDF1F3, 0x233A3D);
            default -> themed("Glyph.Declaration.Background", 0xEBECF0, 0x393B40);
        };
    }

    /**
     * Colour of the letter of the type glyph of an action.
     */
    public static @NotNull Color glyphForeground(@Nullable String type) {
        return switch (glyphKind(type)) {
            case "Table" -> themed("Glyph.Table.Foreground", 0x1B7A33, 0x4FBA5B);
            case "View" -> themed("Glyph.View.Foreground", 0x2F5FD0, 0x6FA8FF);
            case "Incremental" -> themed("Glyph.Incremental.Foreground", 0x8E3A9E, 0xC9A3C2);
            case "Operation" -> themed("Glyph.Operation.Foreground", 0x9A5200, 0xE0AD7A);
            case "Test" -> themed("Glyph.Test.Foreground", 0x116B75, 0x6FC3C9);
            default -> themed("Glyph.Declaration.Foreground", 0x565A66, 0xB4B8BF);
        };
    }

    private static @NotNull String glyphKind(@Nullable String type) {
        String t = type == null ? "" : type.toLowerCase();
        return switch (t) {
            case "table", "materialized_view" -> "Table";
            case "view" -> "View";
            case "incremental" -> "Incremental";
            case "operation", "operations" -> "Operation";
            case "test" -> "Test";
            default -> "Declaration";
        };
    }

    public static @NotNull String glyphFor(@Nullable String type) {
        String t = type == null ? "" : type.toLowerCase();
        return switch (t) {
            case "declaration" -> "D";
            case "view" -> "V";
            case "incremental" -> "I";
            case "table" -> "T";
            case "materialized_view" -> "M";
            case "operation", "operations" -> "O";
            case "assertion" -> "A";
            case "test" -> "U";
            case "outside", "asset" -> "↗";
            default -> "?";
        };
    }

    /**
     * The colour of a column lineage. The first one is the accent colour; the palette holds six
     * colours and then repeats.
     */
    public static @NotNull Color selectionColor(int index) {
        int slot = Math.floorMod(index, SELECTION_PALETTE.length + 1);
        if (slot == 0) return accentColor();
        int[] pair = SELECTION_PALETTE[slot - 1];
        return themed("Selection" + (slot + 1), pair[0], pair[1]);
    }

    /**
     * The translucent tint of the band of a node header that opens and closes its columns,
     * while the pointer is over it. It is painted under the texts.
     */
    public static @NotNull Color toggleBand() {
        return new JBColor(new Color(20, 30, 60, 26), new Color(0, 0, 0, 82));
    }

    /**
     * The tint of the column row under the pointer.
     */
    public static @NotNull Color rowHover() {
        return new JBColor(new Color(20, 30, 60, 15), new Color(255, 255, 255, 15));
    }

    /**
     * Font of a column name in a node card.
     */
    public static @NotNull Font columnFont() {
        return JBFont.medium();
    }

    /**
     * Font of a column type and of the note rows of a node card.
     */
    public static @NotNull Font columnTypeFont() {
        return JBFont.small();
    }

    /**
     * A colour themes can override under {@code Dataform.Lineage.<key>}, with the given light and
     * dark fallbacks.
     */
    static @NotNull Color themed(@NotNull String key, int light, int dark) {
        return JBColor.namedColor(THEME_KEY_PREFIX + key, new JBColor(light, dark));
    }

    private static final class InfoIcon implements Icon {

        private static final int SIZE = 12;
        private final boolean strong;

        private InfoIcon(boolean strong) {
            this.strong = strong;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                double side = getIconWidth();
                g2.setColor(strong ? UIUtil.getLabelForeground() : UIUtil.getContextHelpForeground());
                g2.setStroke(new BasicStroke((float) (side / 12.0)));
                g2.draw(new Ellipse2D.Double(x + 0.5, y + 0.5, side - 1, side - 1));
                double centre = x + side / 2;
                double dot = side / 8.0;
                g2.fill(new Ellipse2D.Double(centre - dot / 2, y + side * 0.25, dot, dot));
                g2.fill(new Rectangle2D.Double(centre - dot / 2, y + side * 0.44, dot, side * 0.32));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return JBUIScale.scale(SIZE);
        }

        @Override
        public int getIconHeight() {
            return JBUIScale.scale(SIZE);
        }
    }

    /** Truncates the text with an ellipsis so it fits {@code maxWidth} in the current font. */
    public static @NotNull String clip(@NotNull Graphics2D g2, @NotNull String text, int maxWidth) {
        var fm = g2.getFontMetrics();
        if (maxWidth <= 0) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        int ellipsisW = fm.stringWidth(ellipsis);
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) + ellipsisW > maxWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }
}
