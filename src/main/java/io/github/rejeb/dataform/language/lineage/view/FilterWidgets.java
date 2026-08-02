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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The small labels the lineage filter sections are built from. They carry the look of the panel —
 * accent colour, dimmed captions, monospaced glyphs — and hold no state of their own.
 */
final class FilterWidgets {

    private FilterWidgets() {
    }

    static JBLabel glyphLabel(@NotNull String glyph, @NotNull Color color) {
        JBLabel label = new JBLabel(glyph);
        label.setForeground(color);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, JBUIScale.scaleFontSize(11f)));
        return label;
    }

    static JBLabel dim(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        label.setForeground(UIUtil.getLabelDisabledForeground());
        label.setFont(label.getFont().deriveFont(JBUIScale.scale(10f)));
        return label;
    }

    static JComponent link(@NotNull String text, @NotNull Runnable action) {
        JBLabel label = new JBLabel(text);
        label.setForeground(accent());
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return label;
    }

    static Font monospace(@NotNull Font base) {
        return new Font(Font.MONOSPACED, Font.PLAIN, base.getSize());
    }

    static Color accent() {
        return new JBColor(new Color(0x3574F0), new Color(0x548AF7));
    }
}
