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
package io.github.rejeb.dataform.language.folding;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.CustomFoldRegionRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Paints the evaluated value of a Dataform expression over as many lines as it needs.
 */
public final class DataformValueFoldRenderer implements CustomFoldRegionRenderer {

    private static final int LEFT_PADDING = 2;

    private final List<String> lines;
    private final Runnable showSource;

    public DataformValueFoldRenderer(@NotNull List<String> value,
                                     @NotNull String prefix,
                                     @NotNull String suffix,
                                     @NotNull Runnable showSource) {
        this.lines = paintedLines(value, prefix, suffix);
        this.showSource = showSource;
    }

    /**
     * Merges the value with the code sharing its lines, so folding whole lines does not hide it.
     */
    @NotNull
    private static List<String> paintedLines(@NotNull List<String> value,
                                             @NotNull String prefix,
                                             @NotNull String suffix) {
        if (value.isEmpty()) {
            return List.of(prefix + suffix);
        }
        List<String> painted = new ArrayList<>(value);
        painted.set(0, prefix + painted.getFirst());
        painted.set(painted.size() - 1, painted.getLast() + suffix);
        return List.copyOf(painted);
    }

    @Override
    public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
        FontMetrics metrics = metricsOf(region.getEditor());
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, metrics.stringWidth(line));
        }
        return width + 2 * LEFT_PADDING;
    }

    @Override
    public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
        return Math.max(1, lines.size()) * region.getEditor().getLineHeight();
    }

    @Override
    public void paint(@NotNull CustomFoldRegion region,
                      @NotNull Graphics2D g,
                      @NotNull Rectangle2D targetRegion,
                      @NotNull TextAttributes textAttributes) {
        Editor editor = region.getEditor();
        Font font = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
        g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(foreground(editor, textAttributes));

        FontMetrics metrics = g.getFontMetrics(font);
        int lineHeight = editor.getLineHeight();
        int x = (int) targetRegion.getX() + LEFT_PADDING;
        int y = (int) targetRegion.getY() + metrics.getAscent();
        for (String line : lines) {
            g.drawString(line, x, y);
            y += lineHeight;
        }
    }

    @Override
    public @Nullable GutterIconRenderer calcGutterIconRenderer(@NotNull CustomFoldRegion region) {
        return new ShowSourceGutterIcon(lines, showSource);
    }

    @NotNull
    private static Color foreground(@NotNull Editor editor, @NotNull TextAttributes textAttributes) {
        TextAttributes folded = editor.getColorsScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
        if (folded != null && folded.getForegroundColor() != null) {
            return folded.getForegroundColor();
        }
        return textAttributes.getForegroundColor() == null
                ? editor.getColorsScheme().getDefaultForeground()
                : textAttributes.getForegroundColor();
    }

    @NotNull
    private static FontMetrics metricsOf(@NotNull Editor editor) {
        return editor.getContentComponent()
                .getFontMetrics(editor.getColorsScheme().getFont(EditorFontType.PLAIN));
    }

    private static final class ShowSourceGutterIcon extends GutterIconRenderer {

        private final List<String> lines;
        private final Runnable showSource;

        private ShowSourceGutterIcon(@NotNull List<String> lines, @NotNull Runnable showSource) {
            this.lines = lines;
            this.showSource = showSource;
        }

        @Override
        public @NotNull Icon getIcon() {
            return AllIcons.General.InlineRefresh;
        }

        @Override
        public @Nullable String getTooltipText() {
            return "Show the Dataform expression source";
        }

        @Override
        public @Nullable com.intellij.openapi.actionSystem.AnAction getClickAction() {
            return new com.intellij.openapi.actionSystem.AnAction("Show Dataform Expression Source") {
                @Override
                public void actionPerformed(@NotNull com.intellij.openapi.actionSystem.AnActionEvent event) {
                    showSource.run();
                }
            };
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShowSourceGutterIcon icon && lines.equals(icon.lines);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lines);
        }
    }
}
