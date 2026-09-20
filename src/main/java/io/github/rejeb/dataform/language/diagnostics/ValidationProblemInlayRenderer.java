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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Paints a Dataform compilation error as a red chip in the editor.
 */
public final class ValidationProblemInlayRenderer
        implements com.intellij.openapi.editor.EditorCustomElementRenderer {

    private static final JBColor BACKGROUND =
            new JBColor(new Color(0xFF, 0xE4, 0xE4), new Color(0x5A, 0x2A, 0x2A));
    private static final JBColor BORDER =
            new JBColor(new Color(0xE5, 0x9B, 0x9B), new Color(0x8B, 0x3F, 0x3F));
    private static final JBColor FOREGROUND =
            new JBColor(new Color(0xC5, 0x22, 0x1F), new Color(0xFF, 0x8A, 0x80));

    private final List<String> lines;

    public ValidationProblemInlayRenderer(@NotNull List<String> lines) {
        this.lines = List.copyOf(lines);
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        FontMetrics metrics = metrics(inlay.getEditor());
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, metrics.stringWidth(line));
        }
        return widest + JBUI.scale(16);
    }

    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
        return inlay.getEditor().getLineHeight() * lines.size();
    }

    @Override
    public void paint(@NotNull Inlay inlay,
                      @NotNull Graphics g,
                      @NotNull Rectangle target,
                      @NotNull TextAttributes textAttributes) {
        Editor editor = inlay.getEditor();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int arc = JBUI.scale(8);
            int inset = JBUI.scale(2);
            int width = calcWidthInPixels(inlay) - JBUI.scale(4);
            int height = target.height - inset * 2;
            int originX = target.x + lineEndOffsetX(inlay);

            g2.setColor(BACKGROUND);
            g2.fillRoundRect(originX + inset, target.y + inset, width, height, arc, arc);
            g2.setColor(BORDER);
            g2.drawRoundRect(originX + inset, target.y + inset, width, height, arc, arc);

            g2.setFont(font(editor));
            g2.setColor(FOREGROUND);

            FontMetrics metrics = g2.getFontMetrics();
            int lineHeight = editor.getLineHeight();
            int textX = originX + JBUI.scale(8);
            for (int i = 0; i < lines.size(); i++) {
                int baseline = target.y + i * lineHeight
                        + (lineHeight + metrics.getAscent() - metrics.getDescent()) / 2;
                g2.drawString(lines.get(i), textX, baseline);
            }
        } finally {
            g2.dispose();
        }
    }

    private static int lineEndOffsetX(@NotNull Inlay inlay) {
        if (!(inlay.getPlacement() == com.intellij.openapi.editor.Inlay.Placement.BELOW_LINE
                || inlay.getPlacement() == com.intellij.openapi.editor.Inlay.Placement.ABOVE_LINE)) {
            return 0;
        }
        Editor editor = inlay.getEditor();
        int offset = inlay.getOffset();
        if (offset < 0 || offset > editor.getDocument().getTextLength()) {
            return 0;
        }
        return editor.offsetToXY(offset, true, false).x;
    }

    private static Font font(@NotNull Editor editor) {
        return editor.getColorsScheme().getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN)
                .deriveFont(Font.PLAIN);
    }

    private static FontMetrics metrics(@NotNull Editor editor) {
        return editor.getContentComponent().getFontMetrics(font(editor));
    }
}
