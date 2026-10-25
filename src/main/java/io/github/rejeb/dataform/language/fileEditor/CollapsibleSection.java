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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class CollapsibleSection extends JPanel {

    private final JPanel header = new JPanel(new BorderLayout());
    private final JComponent content;
    private boolean expanded = true;

    CollapsibleSection(@Nullable String title, @NotNull JComponent content) {
        super(new BorderLayout());
        this.content = content;
        setOpaque(false);
        setBorder(JBUI.Borders.emptyBottom(8));

        header.setOpaque(true);
        header.setBackground(UIUtil.getPanelBackground().brighter());
        header.setBorder(JBUI.Borders.empty(5, 8));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel toggleIcon = new JLabel(AllIcons.General.ArrowDown);
        JBTextField titleField = ReadOnlyTextFields.singleLine(
                title != null ? title : "Unknown table", header.getBackground());
        titleField.setFont(JBUI.Fonts.label(12).asBold());
        titleField.setBorder(JBUI.Borders.emptyLeft(6));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.add(toggleIcon, BorderLayout.WEST);
        titlePanel.add(titleField, BorderLayout.CENTER);
        header.add(titlePanel, BorderLayout.WEST);

        content.setOpaque(false);
        content.setBorder(JBUI.Borders.empty(8, 12, 4, 12));
        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                expanded = !expanded;
                content.setVisible(expanded);
                toggleIcon.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
                if (getParent() instanceof JComponent parent) parent.revalidate();
                revalidate();
                repaint();
            }
        });
    }

    JComponent content() {
        return content;
    }

    static JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
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
