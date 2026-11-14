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

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.List;

/**
 * Lays out a header on one row: fixed leading components, a primary part, a secondary part, a
 * filler taking the free width, then the trailing component at the right edge, as the header of a
 * {@link CollapsibleSection} (chevron and icon, title, subtitle, line) or a {@link PreviewHeader}
 * (summary, links). The trailing component always gets its whole width. When the rest does not
 * fit, the filler shrinks to its minimum width first, which keeps the parts apart, then the
 * primary part gives up width, down to a minimum, then the secondary part, then the primary part
 * again, so the parts never overlap. A secondary part with no width, such as a panel whose
 * components are all hidden, takes no gap either.
 */
final class HeaderRowLayout implements LayoutManager {

    private static final int MIN_PRIMARY_WIDTH = 80;

    private final List<Component> leading;
    private final Component primary;
    private final Component secondary;
    private final Component filler;
    private final int gap;
    private final int minFillerWidth;
    private @Nullable Component trailing;

    /**
     * @param gap            the space between the leading components, the primary part and the
     *                       secondary part, without DPI scaling
     * @param minFillerWidth the width the filler keeps however narrow the header, without DPI
     *                       scaling
     */
    HeaderRowLayout(@NotNull List<Component> leading, @NotNull Component primary, @NotNull Component secondary,
                    @NotNull Component filler, int gap, int minFillerWidth) {
        this.leading = List.copyOf(leading);
        this.primary = primary;
        this.secondary = secondary;
        this.filler = filler;
        this.gap = gap;
        this.minFillerWidth = minFillerWidth;
    }

    /**
     * Sets the component laid out at the right edge, or none.
     */
    void setTrailing(@Nullable Component trailing) {
        this.trailing = trailing;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        if (comp == trailing) {
            trailing = null;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        int width = leadingWidth() + width(primary) + secondaryWidth() + width(filler) + width(trailing);
        return withInsets(parent, width);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return withInsets(parent, leadingWidth() + JBUI.scale(minFillerWidth) + width(trailing));
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int top = insets.top;
        int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
        int right = parent.getWidth() - insets.right;
        int x = insets.left;
        for (Component component : leading) {
            if (component.isVisible()) {
                int width = component.getPreferredSize().width;
                placeCentered(component, x, width, top, height);
                x += width + JBUI.scale(gap);
            }
        }
        int trailingWidth = width(trailing);
        int trailingX = Math.max(x, right - trailingWidth);
        int available = Math.max(0, trailingX - x - JBUI.scale(minFillerWidth));

        int primaryWidth = width(primary);
        int secondaryWidth = secondaryWidth();
        if (primaryWidth + secondaryWidth > available) {
            int shrunk = Math.max(Math.min(primaryWidth, JBUI.scale(MIN_PRIMARY_WIDTH)), available - secondaryWidth);
            primaryWidth = Math.min(shrunk, available);
            secondaryWidth = Math.max(0, available - primaryWidth);
        }
        placeCentered(primary, x, primaryWidth, top, height);
        x += primaryWidth;
        if (secondary.isVisible()) {
            int gapWidth = Math.min(JBUI.scale(gap), secondaryWidth);
            placeCentered(secondary, x + gapWidth, secondaryWidth - gapWidth, top, height);
        }
        x += secondaryWidth;
        filler.setBounds(x, top, Math.max(0, trailingX - x), height);
        if (trailing != null) {
            trailing.setBounds(trailingX, top, trailingWidth, height);
        }
    }

    private int leadingWidth() {
        int width = 0;
        for (Component component : leading) {
            if (component.isVisible()) {
                width += component.getPreferredSize().width + JBUI.scale(gap);
            }
        }
        return width;
    }

    private int secondaryWidth() {
        int width = width(secondary);
        return width > 0 ? width + JBUI.scale(gap) : 0;
    }

    private static int width(@Nullable Component component) {
        return component != null && component.isVisible() ? component.getPreferredSize().width : 0;
    }

    private static void placeCentered(@NotNull Component component, int x, int width, int top, int height) {
        int preferred = Math.min(component.getPreferredSize().height, height);
        component.setBounds(x, top + (height - preferred) / 2, width, preferred);
    }

    private Dimension withInsets(@NotNull Container parent, int width) {
        Insets insets = parent.getInsets();
        int height = 0;
        for (Component component : parent.getComponents()) {
            if (component.isVisible()) {
                height = Math.max(height, component.getPreferredSize().height);
            }
        }
        return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }
}
