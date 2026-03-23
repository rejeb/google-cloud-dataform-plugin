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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.Mode;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Set;

public class DataformWorkflowSettingsEditor
        extends SettingsEditor<DataformWorkflowRunConfiguration> {

    private final ComboBox<String> workspaceCombo = new ComboBox<>();

    private final CheckboxMultiSelectField targetsField = new CheckboxMultiSelectField();
    private final CheckboxMultiSelectField tagsField = new CheckboxMultiSelectField();

    private final JBCheckBox transitiveDeps = new JBCheckBox("Include upstream dependencies");
    private final JBCheckBox transitiveDependents = new JBCheckBox("Include downstream dependents");
    private final JBCheckBox fullRefresh = new JBCheckBox("Full refresh (will recreate incremental tables)");

    private SegmentedButton btnActions;
    private SegmentedButton btnTags;
    private SegmentedButton btnAll;

    private final JPanel cardPanel = new JPanel(new CardLayout());
    private final JPanel mainPanel;
    private final CompiledGraph graph;

    private Mode selectedMode = Mode.ACTIONS;

    public DataformWorkflowSettingsEditor(@NotNull Project project) {
        DataformGcpService.getInstance(project)
                .listWorkspaces()
                .forEach(ws -> workspaceCombo.addItem(ws.workspaceId()));

        this.graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        tagsField.setItems(graph != null ? graph.getTags() : Set.of());
        targetsField.setItems(graph != null ? graph.getAllTargets() : List.of());

        transitiveDeps.addActionListener(e -> transitiveDeps.setSelected(transitiveDeps.isSelected()));
        transitiveDependents.addActionListener(e -> transitiveDependents.setSelected(transitiveDependents.isSelected()));

        cardPanel.add(buildActionsView(), Mode.ACTIONS.name());
        cardPanel.add(buildTagsView(), Mode.TAGS.name());
        cardPanel.add(buildAllView(), Mode.ALL.name());

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Workspace"), workspaceCombo)
                .addSeparator()
                .addComponent(buildModeBar())
                .addComponent(cardPanel)
                .addSeparator()
                .addComponent(transitiveDeps)
                .addComponent(transitiveDependents)
                .addComponent(fullRefresh)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        selectMode(Mode.ALL);
    }

    private JPanel buildActionsView() {
        return FormBuilder.createFormBuilder()
                .addComponent(targetsField)
                .getPanel();
    }

    private JPanel buildTagsView() {
        return FormBuilder.createFormBuilder()
                .addComponent(tagsField)
                .getPanel();
    }

    private JPanel buildAllView() {
        return FormBuilder.createFormBuilder()
                .getPanel();
    }

    private JPanel buildModeBar() {
        btnActions = new SegmentedButton("Actions", Position.LEFT);
        btnTags = new SegmentedButton("Tags", Position.MIDDLE);
        btnAll = new SegmentedButton("All", Position.RIGHT);

        btnActions.addActionListener(e -> selectMode(Mode.ACTIONS));
        btnTags.addActionListener(e -> selectMode(Mode.TAGS));
        btnAll.addActionListener(e -> selectMode(Mode.ALL));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        bar.add(btnActions);
        bar.add(btnTags);
        bar.add(btnAll);
        return bar;
    }

    private void selectMode(Mode mode) {
        btnActions.setSelectedOption(mode == Mode.ACTIONS);
        btnTags.setSelectedOption(mode == Mode.TAGS);
        btnAll.setSelectedOption(mode == Mode.ALL);
        transitiveDeps.setVisible(mode != Mode.ALL);
        transitiveDependents.setVisible(mode != Mode.ALL);
        selectedMode = mode;
        if (mode != Mode.ACTIONS) {
            targetsField.setSelectedItems(List.of());
        }

        if (mode != Mode.TAGS) {
            tagsField.setSelectedItems(List.of());
        }

        ((CardLayout) cardPanel.getLayout()).show(cardPanel, mode.name());
    }

    private enum Position {LEFT, MIDDLE, RIGHT}

    private static final class SegmentedButton extends JButton {

        private boolean selected = false;
        private final Position position;

        private static final JBColor COLOR_SELECTED_BG = JBColor.BLUE;
        private static final JBColor COLOR_SELECTED_FG = JBColor.WHITE;
        private static final JBColor COLOR_BORDER = JBColor.GRAY;

        SegmentedButton(String label, Position position) {
            super(label);
            this.position = position;
            setFocusable(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setMargin(JBUI.insets(3, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        public void setSelectedOption(boolean selected) {
            this.selected = selected;
            repaint();
        }

        public boolean isSelected() {
            return selected;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int arc = 6;

            Color bg = selected
                    ? COLOR_SELECTED_BG
                    : (getModel().isRollover()
                    ? UIManager.getColor("Button.hoverBackground") != null
                    ? UIManager.getColor("Button.hoverBackground")
                    : JBColor.WHITE
                    : UIManager.getColor("Panel.background"));

            g2.setColor(bg);

            switch (position) {
                case LEFT -> {
                    g2.fill(new RoundRectangle2D.Float(0, 0, w + arc, h, arc * 2, arc * 2));
                }
                case RIGHT -> {
                    g2.fill(new RoundRectangle2D.Float(-arc, 0, w + arc, h, arc * 2, arc * 2));
                }
                default -> {
                    g2.fillRect(0, 0, w, h);
                }
            }

            g2.setColor(COLOR_BORDER);
            g2.setStroke(new BasicStroke(1f));

            g2.drawLine(0, 0, w, 0);
            g2.drawLine(0, h - 1, w, h - 1);
            if (position == Position.LEFT || position == Position.MIDDLE) {
                g2.drawLine(0, 0, 0, h);
            }

            if (position == Position.MIDDLE || position == Position.RIGHT) {
                g2.drawLine(w - 1, 0, w - 1, h);
            }
            if (position == Position.RIGHT) {
                g2.drawLine(w - 1, 0, w - 1, h);
            }

            g2.setColor(selected ? COLOR_SELECTED_FG : UIManager.getColor("Button.foreground"));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);

            g2.dispose();
        }
    }

    @Override
    protected void resetEditorFrom(@NotNull DataformWorkflowRunConfiguration config) {
        workspaceCombo.setSelectedItem(config.getWorkspaceId());
        tagsField.setSelectedItems(config.getIncludedTags().stream().filter(this.graph.getTags()::contains).toList());
        targetsField.setSelectedItems(config.getIncludedTargets().stream().filter(this.graph.getAllTargets()::contains).toList());

        boolean deps = config.isTransitiveDependenciesIncluded();
        boolean dependents = config.isTransitiveDependentsIncluded();
        transitiveDeps.setSelected(deps);
        transitiveDependents.setSelected(dependents);

       selectMode(config.getSelectedMode());
    }

    @Override
    protected void applyEditorTo(@NotNull DataformWorkflowRunConfiguration config) {
        config.setWorkspaceId((String) workspaceCombo.getSelectedItem());
        config.setIncludedTags(tagsField.getSelectedItems());
        config.setIncludedTargets(targetsField.getSelectedItems());
        config.setTransitiveDependenciesIncluded(transitiveDeps.isSelected());
        config.setTransitiveDependentsIncluded(transitiveDependents.isSelected());
        config.setFullyRefreshIncrementalTables(fullRefresh.isSelected());
        config.setSelectedMode(selectedMode);
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return mainPanel;
    }
}
