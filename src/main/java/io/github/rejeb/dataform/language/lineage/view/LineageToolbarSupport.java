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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.model.Density;
import io.github.rejeb.dataform.language.lineage.model.Direction;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.RoundedBox;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The toolbar pieces the file and the project lineage views share.
 */
final class LineageToolbarSupport {

    private static final double ZOOM_STEP = 1.2;

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

    /**
     * An action that puts the given component, such as a search field, in the toolbar.
     */
    static AnAction component(@NotNull JComponent component) {
        return new ComponentAction(component);
    }

    /**
     * Focuses the canvas on the selected action and its lineage, or leaves focus mode. Reads
     * "Focus", or "Exit focus" while focused.
     */
    static ToggleAction focusToggle(@NotNull LineageModel model) {
        return new ToggleAction("Focus", "Show only the upstream and downstream actions of the selection",
                LineageIcons.FOCUS) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return model.focusId() != null;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                if (state && model.selectedId() != null) {
                    model.focusOn(model.selectedId());
                } else {
                    model.exitFocus();
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setText(model.focusId() != null ? "Exit focus" : "Focus");
                e.getPresentation().setEnabled(model.focusId() != null || model.selectedId() != null);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    /**
     * A "Legend" text button with a drop-down arrow that shows the legend of the given action
     * types in a popup under it.
     */
    static AnAction legendAction(@NotNull Supplier<List<String>> types) {
        DefaultActionGroup legend = new DefaultActionGroup("Legend", true) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                JBPopup popup = JBPopupFactory.getInstance()
                        .createComponentPopupBuilder(LineageLegend.panel(types.get()), null)
                        .setRequestFocus(false)
                        .createPopup();
                InputEvent input = e.getInputEvent();
                if (input != null && input.getComponent() != null) {
                    popup.showUnderneathOf(input.getComponent());
                } else {
                    popup.showInBestPositionFor(e.getDataContext());
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setPerformGroup(true);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        Presentation presentation = legend.getTemplatePresentation();
        presentation.setDescription("Show what the glyphs and colours of the nodes mean");
        presentation.setIcon(null);
        presentation.setPerformGroup(true);
        presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true);
        return legend;
    }

    /**
     * Switches the layout between left to right and top to bottom; its icon shows the current
     * direction.
     */
    static AnAction directionAction(@NotNull LineageModel model) {
        return new DumbAwareAction("Layout direction", "Switch the layout direction", LineageIcons.DIRECTION_LR) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                model.toggleDirection();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                boolean topToBottom = model.direction() == Direction.TB;
                e.getPresentation().setIcon(topToBottom ? LineageIcons.DIRECTION_TB : LineageIcons.DIRECTION_LR);
                e.getPresentation().setDescription(topToBottom ? "Layout: top to bottom" : "Layout: left to right");
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    static ToggleAction densityToggle(@NotNull LineageModel model) {
        return toggle("Compact density", "Show the nodes on one line",
                () -> LineageIcons.DENSITY,
                () -> model.density() == Density.COMPACT, model::toggleDensity);
    }

    /**
     * The zoom out and zoom in buttons of the canvas.
     */
    static @NotNull List<AnAction> zoomActions(@NotNull GraphCanvas canvas) {
        return List.of(
                action("Zoom out", "Zoom out", LineageIcons.ZOOM_OUT, () -> canvas.zoomBy(1 / ZOOM_STEP)),
                action("Zoom in", "Zoom in", LineageIcons.ZOOM_IN, () -> canvas.zoomBy(ZOOM_STEP)));
    }

    /**
     * The zoom buttons in a small rounded box, laid over the bottom-left corner of the canvas.
     */
    static @NotNull JComponent zoomControls(@NotNull GraphCanvas canvas) {
        ActionToolbar toolbar = Toolbars.horizontal("Dataform.Lineage.Zoom", canvas,
                zoomActions(canvas).toArray(AnAction[]::new));
        toolbar.setMinimumButtonSize(JBUI.size(24, 24));
        JComponent buttons = toolbar.getComponent();
        buttons.setBorder(JBUI.Borders.empty(1));
        RoundedBox box = new RoundedBox(UIUtil::getPanelBackground, buttons);
        box.setFocusable(false);
        return box;
    }

    /**
     * The toolbar row above the project lineage: the toolbar on the panel background, over a line.
     */
    static @NotNull JComponent toolbarRow(@NotNull ActionToolbar toolbar) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(UIUtil.getPanelBackground());
        row.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0),
                JBUI.Borders.empty(1, 2)));
        JComponent component = toolbar.getComponent();
        component.setOpaque(false);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    static JPanel emptyCard(@NotNull String message) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        JBLabel label = new JBLabel(message, SwingConstants.CENTER);
        label.setForeground(JBColor.GRAY);
        panel.add(label);
        return panel;
    }

    private static final class ComponentAction extends DumbAwareAction implements CustomComponentAction {

        private final JComponent component;

        private ComponentAction(@NotNull JComponent component) {
            this.component = component;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
        }

        @Override
        public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
            return component;
        }
    }
}
