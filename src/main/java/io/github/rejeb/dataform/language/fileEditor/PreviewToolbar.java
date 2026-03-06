/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;

public class PreviewToolbar extends JPanel {

    private Runnable onExecute;
    private Runnable onCost;
    private Runnable onCompile;
    private Runnable onSettings;

    private final JLabel statusLabel;

    public PreviewToolbar() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(UIUtil.getPanelBackground());
        setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                JBUI.Borders.empty(6, 10)
        ));

        JButton execBtn     = buildToolbarButton("Exécuter",   AllIcons.Actions.Execute);
        JButton costBtn     = buildToolbarButton("Coût",       AllIcons.General.InspectionsEye);

        execBtn.addActionListener(e -> { if (onExecute != null) onExecute.run(); });
        costBtn.addActionListener(e -> { if (onCost != null) onCost.run(); });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(execBtn);
        actions.add(costBtn);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        center.setOpaque(false);
        center.add(actions);

        statusLabel = new JLabel();
        statusLabel.setFont(JBUI.Fonts.label(11));
        statusLabel.setForeground(UIUtil.getContextHelpForeground());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(statusLabel);

        add(center, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void setOnExecute(@Nullable Runnable cb) { this.onExecute = cb; }
    public void setOnCost(@Nullable Runnable cb) { this.onCost = cb; }
    public void setOnCompile(@Nullable Runnable cb) { this.onCompile = cb; }
    public void setOnSettings(@Nullable Runnable cb) { this.onSettings = cb; }

    public void setStatusText(@Nullable String text) {
        statusLabel.setText(text);
    }

    private JButton buildToolbarButton(String text, Icon icon) {
        JButton btn = new JButton(text, icon);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(JBUI.Fonts.label(12));
        btn.setForeground(UIUtil.getLabelForeground());
        btn.setBorder(JBUI.Borders.empty(4, 6));
        return btn;
    }
}
