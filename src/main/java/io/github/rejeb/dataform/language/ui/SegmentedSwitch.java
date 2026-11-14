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

import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A row of joined buttons, one of which is pressed: a choice between a few values that are all
 * visible, such as the source of a view or the scope of a search. The buttons share one rounded
 * border.
 *
 * @param <T> the type of the values
 */
public final class SegmentedSwitch<T> extends JPanel {

    private static final int SWITCH_H = 26;
    private static final int COMPACT_SWITCH_H = 20;
    private static final int ARC = 10;
    private static final int COMPACT_ARC = 8;
    private static final int PAD = 10;

    private final Map<T, Segment> segments = new LinkedHashMap<>();
    private final Consumer<T> onChange;
    private T selected;
    private boolean compact;

    /**
     * @param values   the values to choose from, in the order of the buttons
     * @param label    the text of the button of a value
     * @param selected the value pressed at first
     * @param onChange called with the new value each time the choice changes
     */
    public SegmentedSwitch(@NotNull List<T> values, @NotNull Function<T, String> label, @NotNull T selected,
                           @NotNull Consumer<T> onChange) {
        this.onChange = onChange;
        this.selected = selected;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(false);
        setBorder(JBUI.Borders.empty(1));
        ButtonGroup group = new ButtonGroup();
        for (T value : values) {
            Segment segment = new Segment(label.apply(value), !segments.isEmpty());
            segment.setSelected(value.equals(selected));
            segment.addActionListener(e -> select(value));
            group.add(segment);
            segments.put(value, segment);
            add(segment);
        }
        if (!segments.containsKey(selected)) throw new IllegalArgumentException("Unknown value: " + selected);
    }

    /**
     * @return the value whose button is pressed
     */
    public @NotNull T selected() {
        return selected;
    }

    /**
     * Presses the button of the given value. The change listener is called when the choice changes.
     *
     * @throws IllegalArgumentException when the switch has no button for the value
     */
    public void select(@NotNull T value) {
        Segment segment = segments.get(value);
        if (segment == null) throw new IllegalArgumentException("Unknown value: " + value);
        segment.setSelected(true);
        if (value.equals(selected)) return;
        selected = value;
        repaint();
        onChange.accept(value);
    }

    /**
     * Makes the switch small enough to sit inside a field: 20 pixels high instead of 26, with a
     * smaller font.
     */
    public void setCompact(boolean compact) {
        if (this.compact == compact) return;
        this.compact = compact;
        for (Segment segment : segments.values()) segment.invalidate();
        invalidate();
        revalidate();
        repaint();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            GraphicsUtil.setupAAPainting(g2);
            Shape outline = outline();
            Shape oldClip = g2.getClip();
            g2.clip(outline);
            super.paintChildren(g2);
            g2.setClip(oldClip);
            g2.setColor(borderColor());
            g2.draw(outline);
        } finally {
            g2.dispose();
        }
    }

    private @NotNull Shape outline() {
        int arc = JBUIScale.scale(compact ? COMPACT_ARC : ARC);
        return new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc);
    }

    private static @NotNull Color borderColor() {
        return JBColor.namedColor("Component.borderColor", new JBColor(0xC9CCD6, 0x4E5157));
    }

    private final class Segment extends JToggleButton {

        private final boolean divided;

        Segment(@NotNull String text, boolean divided) {
            super(text);
            this.divided = divided;
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        private @NotNull Font segmentFont() {
            return compact ? JBFont.medium() : JBFont.label();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(segmentFont());
            return new Dimension(fm.stringWidth(getText()) + 2 * JBUIScale.scale(compact ? PAD - 2 : PAD) + (divided ? 1 : 0),
                    JBUIScale.scale(compact ? COMPACT_SWITCH_H : SWITCH_H) - 2);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                GraphicsUtil.setupAAPainting(g2);
                GraphicsUtil.setupAntialiasing(g2);
                int left = divided ? 1 : 0;
                if (isSelected()) {
                    g2.setColor(JBUI.CurrentTheme.ActionButton.pressedBackground());
                    g2.fillRect(left, 0, getWidth() - left, getHeight());
                } else if (getModel().isRollover()) {
                    g2.setColor(JBUI.CurrentTheme.ActionButton.hoverBackground());
                    g2.fillRect(left, 0, getWidth() - left, getHeight());
                }
                if (divided) {
                    g2.setColor(borderColor());
                    g2.fillRect(0, 0, 1, getHeight());
                }
                if (isFocusOwner()) {
                    g2.setColor(JBUI.CurrentTheme.Focus.focusColor());
                    g2.drawRect(left, 0, getWidth() - left - 1, getHeight() - 1);
                }
                g2.setFont(segmentFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(isSelected() ? UIUtil.getLabelForeground() : UIUtil.getContextHelpForeground());
                g2.drawString(getText(), left + (getWidth() - left - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            } finally {
                g2.dispose();
            }
        }
    }
}
