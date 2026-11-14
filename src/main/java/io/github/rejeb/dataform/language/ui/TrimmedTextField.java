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
package io.github.rejeb.dataform.language.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.SwingTextTrimmer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ToolTipManager;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;

/**
 * A single-line text that, given less width than its text needs, shows the text shortened by a
 * {@link SwingTextTrimmer} and the whole text in its tooltip. The text itself stays whole: once
 * the field has the focus, to select part of it, it shows the text as a plain field does, and a
 * copy copies the whole text.
 */
public final class TrimmedTextField extends JBTextField {

    private final SwingTextTrimmer trimmer;

    TrimmedTextField(@NotNull String text, @NotNull SwingTextTrimmer trimmer) {
        super(text);
        this.trimmer = trimmer;
        ToolTipManager.sharedInstance().registerComponent(this);
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    /**
     * The text as it is painted: shortened while the field is narrower than its text, whole
     * otherwise.
     */
    public @NotNull String shownText() {
        return isTrimmed() ? trimmer.trim(getText(), getFontMetrics(getFont()), textWidth()) : getText();
    }

    /**
     * The tooltip set on the field when there is one; otherwise the whole text while it is shown
     * shortened, and no tooltip once it fits.
     */
    @Override
    public @Nullable String getToolTipText(@NotNull MouseEvent event) {
        String explicit = super.getToolTipText(event);
        if (explicit != null) {
            return explicit;
        }
        return isTrimmed() ? getText() : null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isTrimmed()) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (isOpaque()) {
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            UISettings.setupAntialiasing(g2);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            g2.drawString(shownText(), getInsets().left, baseline());
        } finally {
            g2.dispose();
        }
    }

    private boolean isTrimmed() {
        return !isFocusOwner() && getWidth() < getPreferredSize().width;
    }

    private int textWidth() {
        Insets insets = getInsets();
        return Math.max(0, getWidth() - insets.left - insets.right);
    }

    private int baseline() {
        int baseline = getBaseline(getWidth(), getHeight());
        if (baseline >= 0) {
            return baseline;
        }
        Insets insets = getInsets();
        FontMetrics metrics = getFontMetrics(getFont());
        return insets.top + (getHeight() - insets.top - insets.bottom - metrics.getHeight()) / 2 + metrics.getAscent();
    }
}
