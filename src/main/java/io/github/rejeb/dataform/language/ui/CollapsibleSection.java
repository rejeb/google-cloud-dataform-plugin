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

import com.intellij.icons.AllIcons;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SeparatorOrientation;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A titled group whose content collapses on a click of its header, as the sections of the
 * compiled query preview. The header shows a chevron, an optional icon, the title as selectable
 * text, a grayed subtitle, then a line up to the trailing components. A click anywhere in the
 * header but on the title toggles the group. Colours come from the theme.
 */
public class CollapsibleSection extends JPanel {

    private static final int HEADER_HEIGHT = 32;
    private static final int GAP = 6;
    private static final int LINE_MARGIN = 12;
    private static final int CONTENT_INDENT = 22;
    private static final int CONTENT_GAP = 8;
    private static final int SECTION_GAP = 10;

    private final JPanel header = new JPanel(new BorderLayout()) {
        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, JBUI.scale(HEADER_HEIGHT)));
        }
    };
    private final JComponent content;
    private final String title;
    private final JLabel toggleIcon = new JLabel(AllIcons.General.ChevronDown);
    private final JLabel typeIcon = new JLabel();
    private final JBLabel subtitle = new JBLabel();
    private boolean expanded = true;

    /**
     * Creates an expanded section.
     *
     * @param title   the selectable title, {@code "Unknown table"} when {@code null}
     * @param content the component shown below the header while expanded
     */
    public CollapsibleSection(@Nullable String title, @NotNull JComponent content) {
        super(new BorderLayout());
        this.content = content;
        this.title = title != null ? title : "Unknown table";
        setOpaque(false);
        setBorder(JBUI.Borders.emptyBottom(SECTION_GAP));

        header.setOpaque(false);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JBTextField titleField = ReadOnlyTextFields.singleLine(this.title);
        titleField.setOpaque(false);
        titleField.setFont(JBUI.Fonts.label().asBold());
        titleField.setBorder(JBUI.Borders.empty());

        typeIcon.setVisible(false);
        subtitle.setForeground(UIUtil.getContextHelpForeground());
        subtitle.setFont(JBFont.medium());
        subtitle.setVisible(false);

        JPanel titlePanel = new JPanel(new HorizontalLayout(GAP, SwingConstants.CENTER));
        titlePanel.setOpaque(false);
        titlePanel.add(toggleIcon);
        titlePanel.add(typeIcon);
        titlePanel.add(titleField);
        titlePanel.add(subtitle);
        header.add(titlePanel, BorderLayout.WEST);
        header.add(line(), BorderLayout.CENTER);

        content.setOpaque(false);
        content.setBorder(JBUI.Borders.emptyLeft(CONTENT_INDENT));
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        header.addMouseListener(toggleOnClick());
        toggleIcon.addMouseListener(toggleOnClick());
        typeIcon.addMouseListener(toggleOnClick());
        subtitle.addMouseListener(toggleOnClick());
        titlePanel.addMouseListener(toggleOnClick());
    }

    /**
     * The title shown in the header.
     */
    public @NotNull String title() {
        return title;
    }

    /**
     * Shows a grayed text right after the title, such as the type of an action; {@code null} or
     * blank hides it.
     */
    public void setSubtitle(@Nullable String text) {
        boolean shown = text != null && !text.isBlank();
        subtitle.setText(shown ? text : "");
        subtitle.setVisible(shown);
    }

    /**
     * Shows an icon between the chevron and the title, such as the type of an action; {@code null}
     * hides it.
     */
    public void setIcon(@Nullable Icon icon) {
        typeIcon.setIcon(icon);
        typeIcon.setVisible(icon != null);
    }

    /**
     * Puts components at the end of the header, in a row, such as a cost and a toolbar. A click on
     * one of them also toggles the section unless the component consumes the click itself, as a
     * toolbar button does.
     */
    public void setTrailing(@NotNull JComponent... trailing) {
        JPanel row = new JPanel(new HorizontalLayout(4, SwingConstants.CENTER));
        row.setOpaque(false);
        for (JComponent component : trailing) {
            row.add(component);
            component.addMouseListener(toggleOnClick());
        }
        row.addMouseListener(toggleOnClick());
        header.add(row, BorderLayout.EAST);
    }

    /**
     * The component shown below the header.
     */
    public @NotNull JComponent content() {
        return content;
    }

    /**
     * Whether the content is shown.
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Shows or hides the content.
     */
    public void setExpanded(boolean expanded) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        content.setVisible(expanded);
        toggleIcon.setIcon(expanded ? AllIcons.General.ChevronDown : AllIcons.General.ChevronRight);
        if (getParent() instanceof JComponent parent) parent.revalidate();
        revalidate();
        repaint();
    }

    private MouseAdapter toggleOnClick() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setExpanded(!expanded);
            }
        };
    }

    /**
     * Creates a transparent panel stacking its visible children from top to bottom, eight pixels
     * apart, each as wide as the panel.
     */
    public static @NotNull JPanel verticalPanel() {
        JPanel panel = new JPanel(new VerticalLayout(CONTENT_GAP));
        panel.setOpaque(false);
        return panel;
    }

    private @NotNull JComponent line() {
        SeparatorComponent separator = new SeparatorComponent(JBUI.CurrentTheme.Separator.color(),
                SeparatorOrientation.HORIZONTAL);
        JPanel line = new JPanel(new GridBagLayout());
        line.setOpaque(false);
        line.setBorder(JBUI.Borders.empty(0, LINE_MARGIN));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        line.add(separator, constraints);
        line.addMouseListener(toggleOnClick());
        separator.addMouseListener(toggleOnClick());
        return line;
    }

    @Override
    public Dimension getPreferredSize() {
        if (!expanded) {
            Insets ins = getInsets();
            return new Dimension(super.getPreferredSize().width, header.getPreferredSize().height + ins.top + ins.bottom);
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, expanded ? Integer.MAX_VALUE : getPreferredSize().height);
    }
}
