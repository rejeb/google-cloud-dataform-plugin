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
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * Colours, fonts and text helpers shared by every lineage view component. Everything is
 * theme-aware through {@link JBColor} except the pinned semantic type colours.
 */
public final class LineageTheme {

    private static final float GOLDEN_RATIO_CONJUGATE = 0.618033988f;
    private static final float FIRST_SELECTION_HUE = 0.61f;

    private static Font monoBase;

    private LineageTheme() {
    }

    public static @NotNull Font monospace(float size) {
        if (monoBase == null) monoBase = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        return monoBase.deriveFont(JBUIScale.scale(size));
    }

    public static @NotNull Color nodeBackground() {
        return new JBColor(new Color(0xFFFFFF), new Color(0x2B2D30));
    }

    public static @NotNull Color nodeBorder() {
        return new JBColor(new Color(0xD4D4D4), new Color(0x3D4045));
    }

    public static @NotNull Color accentColor() {
        return new JBColor(new Color(0x3574F0), new Color(0x548AF7));
    }

    public static @NotNull Color edgeColor() {
        return new JBColor(new Color(0xB0B3BA), new Color(0x4E5157));
    }

    public static @NotNull Color translucent(@NotNull Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    public static @NotNull Color typeColor(@Nullable String type) {
        String t = type == null ? "" : type.toLowerCase();
        return switch (t) {
            case "view" -> new JBColor(new Color(0x3574F0), new Color(0x5C9EFF));
            case "incremental" -> new JBColor(new Color(0x9B59B6), new Color(0xB48EAD));
            case "table" -> new JBColor(new Color(0x2A8A35), new Color(0x3FA84A));
            case "materialized_view" -> new JBColor(new Color(0x2A8A35), new Color(0x3FA84A));
            case "operation" -> new JBColor(new Color(0xC97A32), new Color(0xD19A66));
            case "assertion" -> new JBColor(new Color(0xC04148), new Color(0xE06C75));
            default -> new JBColor(new Color(0x6B7280), new Color(0x9CA0A4));
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
            case "operation" -> "O";
            case "assertion" -> "A";
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
