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

import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeListener;
import java.util.function.Supplier;

/**
 * A box with rounded corners, filled with a background and drawn with a thin border in the
 * separator colour, as the schema tables of the preview. Its content is clipped to the rounded
 * shape, so an opaque content does not square the corners. While the focus is inside the box, the
 * border takes the focus colour.
 */
public class RoundedBox extends JPanel {

    private static final int ARC = 12;
    private static final String FOCUS_OWNER = "permanentFocusOwner";

    private final Supplier<Color> background;
    private final RoundedLineBorder line = new RoundedLineBorder(JBUI.CurrentTheme.Separator.color(), JBUI.scale(ARC), 1);
    private final PropertyChangeListener focusListener =
            e -> focusOwnerChanged(e.getNewValue() instanceof Component owner ? owner : null);
    private boolean highlighted;

    /**
     * Wraps the given content.
     *
     * @param background the colour filling the box, read on each paint so it follows the theme
     * @param content    the component filling the box
     */
    public RoundedBox(@NotNull Supplier<Color> background, @NotNull JComponent content) {
        super(new BorderLayout());
        this.background = background;
        setOpaque(false);
        setBorder(line);
        add(content, BorderLayout.CENTER);
    }

    /**
     * Draws the border in the focus colour, as while the content has the focus, or back in the
     * separator colour.
     */
    public void setHighlighted(boolean highlighted) {
        if (this.highlighted == highlighted) return;
        this.highlighted = highlighted;
        line.setColor(highlighted ? JBUI.CurrentTheme.Focus.focusColor() : JBUI.CurrentTheme.Separator.color());
        repaint();
    }

    /**
     * Highlights the border when the given component, the new focus owner, is inside the box.
     */
    void focusOwnerChanged(@Nullable Component owner) {
        setHighlighted(owner != null && SwingUtilities.isDescendingFrom(owner, this));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUS_OWNER, focusListener);
    }

    @Override
    public void removeNotify() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(FOCUS_OWNER, focusListener);
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            GraphicsUtil.setupAAPainting(g2);
            g2.setColor(background.get());
            g2.fill(shape());
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintBorder(Graphics g) {
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.clip(shape());
            super.paintChildren(g2);
        } finally {
            g2.dispose();
        }
        super.paintBorder(g);
    }

    @NotNull
    private RoundRectangle2D shape() {
        int arc = JBUI.scale(ARC);
        return new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc);
    }
}
