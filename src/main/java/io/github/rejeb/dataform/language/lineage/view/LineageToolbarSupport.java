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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The toolbar pieces the file and the project lineage views share.
 */
final class LineageToolbarSupport {

    private LineageToolbarSupport() {
    }

    static DumbAwareAction action(@NotNull String text, @NotNull String description,
                                  @NotNull Icon icon, @NotNull Runnable run) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                run.run();
            }
        };
    }

    static ToggleAction toggle(@NotNull String text, @NotNull String description,
                               @NotNull Supplier<Icon> icon,
                               @NotNull BooleanSupplier state,
                               @NotNull Runnable toggle) {
        return new ToggleAction(text, description, icon.get()) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return state.getAsBoolean();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean requested) {
                if (requested != state.getAsBoolean()) toggle.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setIcon(icon.get());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    static ToggleAction directionToggle(@NotNull LineageModel model) {
        return toggle("Layout direction", "Toggle left-to-right / top-to-bottom",
                () -> model.direction() == Direction.TB ? LineageIcons.DIRECTION_TB : LineageIcons.DIRECTION_LR,
                () -> model.direction() == Direction.TB, model::toggleDirection);
    }

    static ToggleAction densityToggle(@NotNull LineageModel model) {
        return toggle("Density", "Toggle compact / comfortable node density",
                () -> model.density() == Density.COMPACT ? LineageIcons.DENSITY_COMPACT : LineageIcons.DENSITY_COMFORTABLE,
                () -> model.density() == Density.COMPACT, model::toggleDensity);
    }

    static JComponent header(@NotNull String crumbText, @Nullable JComponent center, @NotNull ActionToolbar toolbar) {
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUIScale.scale(6), JBUIScale.scale(2)));
        title.setOpaque(false);
        JBLabel name = new JBLabel("Lineage", DataformIcons.LINEAGE, SwingConstants.LEADING);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        title.add(name);
        JBLabel crumb = new JBLabel(crumbText);
        crumb.setForeground(UIUtil.getLabelDisabledForeground());
        title.add(crumb);

        JPanel north = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        north.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                JBUI.Borders.empty(2, 6)));
        north.add(title, BorderLayout.WEST);
        if (center != null) north.add(center, BorderLayout.CENTER);
        north.add(toolbar.getComponent(), BorderLayout.EAST);
        return north;
    }

    static JPanel emptyCard(@NotNull String message) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        JBLabel label = new JBLabel(message, SwingConstants.CENTER);
        label.setForeground(JBColor.GRAY);
        panel.add(label);
        return panel;
    }
}
