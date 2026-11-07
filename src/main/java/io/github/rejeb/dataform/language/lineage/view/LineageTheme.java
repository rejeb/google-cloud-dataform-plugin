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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Colours, fonts and text helpers shared by every lineage view component. Every colour is a
 * theme key under {@code Dataform.Lineage.} with light and dark fallbacks.
 */
public final class LineageTheme {

    private static final String THEME_KEY_PREFIX = "Dataform.Lineage.";
    private static final float GOLDEN_RATIO_CONJUGATE = 0.618033988f;
    private static final float FIRST_SELECTION_HUE = 0.61f;

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
            default -> "?";
        };
    }

    /**
     * A distinct colour per selected column. The first selection is blue; subsequent hues are
     * spread by the golden ratio so consecutive selections are far apart on the wheel and colours
     * do not repeat for a large number of selections. Saturation/brightness are tuned per theme.
     */
    public static @NotNull Color selectionColor(int index) {
        float hue = (FIRST_SELECTION_HUE + index * GOLDEN_RATIO_CONJUGATE) % 1.0f;
        return new JBColor(Color.getHSBColor(hue, 0.68f, 0.72f),
                Color.getHSBColor(hue, 0.55f, 0.88f));
    }

    /**
     * A colour themes can override under {@code Dataform.Lineage.<key>}, with the given light and
     * dark fallbacks.
     */
    static @NotNull Color themed(@NotNull String key, int light, int dark) {
        return JBColor.namedColor(THEME_KEY_PREFIX + key, new JBColor(light, dark));
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
