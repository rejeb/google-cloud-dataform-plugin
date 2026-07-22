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

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Set;

/**
 * Right-hand details panel for the selected node: header, metadata, action buttons, and
 * clickable upstream/downstream dependency lists. Rebuilt on model change; hidden by the
 * host when no node is selected.
 */
public final class DetailsPanel extends JPanel {

    private final Project project;
    private final LineageModel model;
    private final JPanel content = new JPanel(new VerticalLayout(JBUIScale.scale(8)));

    public DetailsPanel(@NotNull Project project, @NotNull LineageModel model) {
        super(new BorderLayout());
        this.project = project;
        this.model = model;
        setPreferredSize(new Dimension(JBUIScale.scale(300), 0));
        content.setBorder(JBUI.Borders.empty(10));
        content.setBackground(UIUtil.getPanelBackground());

        JBScrollPane scroll = new JBScrollPane(content);
        scroll.setBorder(JBUI.Borders.empty());
        add(scroll, BorderLayout.CENTER);

        rebuild();
        model.addListener(m -> onModelChanged());
    }

    private String lastSignature;

    private void onModelChanged() {
        String signature = System.identityHashCode(model.graph()) + "|" + model.selectedId()
                + "|" + model.focusId();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        javax.swing.SwingUtilities.invokeLater(this::rebuild);
    }

    private void rebuild() {
        content.removeAll();
        LineageNode node = model.selectedId() != null ? model.graph().node(model.selectedId()) : null;
        if (node != null) {
            content.add(header(node));
            content.add(new JBLabel(node.schema() + "." + node.name()));
            content.add(metaRow("Type", GraphCanvas.glyphFor(node.dataformType()) + "  " + node.dataformType()));
            content.add(metaRow("Schema", node.schema()));
            content.add(metaRow("File", node.fileName() != null ? node.fileName() : "—"));
            content.add(tagsRow(node));
            content.add(actionButtons(node));
            content.add(dependencyList("Upstream", model.graph().predecessors(node.id())));
            content.add(dependencyList("Downstream", model.graph().successors(node.id())));
        }
        content.revalidate();
        content.repaint();
    }

    private JComponent header(@NotNull LineageNode node) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JBLabel title = new JBLabel(GraphCanvas.glyphFor(node.dataformType()) + "  " + node.name());
        title.setFont(monospaceBold(title.getFont()));
        panel.add(title, BorderLayout.CENTER);
        JButton close = new JButton("✕");
        close.setToolTipText("Close");
        close.addActionListener(e -> model.select(null));
        panel.add(close, BorderLayout.EAST);
        return panel;
    }

    private JComponent metaRow(@NotNull String key, @NotNull String value) {
        JPanel panel = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        panel.setOpaque(false);
        JBLabel k = new JBLabel(key);
        k.setForeground(UIUtil.getLabelDisabledForeground());
        k.setPreferredSize(new Dimension(JBUIScale.scale(60), k.getPreferredSize().height));
        panel.add(k, BorderLayout.WEST);
        JBLabel v = new JBLabel("<html>" + escape(value) + "</html>");
        panel.add(v, BorderLayout.CENTER);
        return panel;
    }

    private JComponent tagsRow(@NotNull LineageNode node) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUIScale.scale(4), JBUIScale.scale(2)));
        panel.setOpaque(false);
        JBLabel k = new JBLabel("Tags");
        k.setForeground(UIUtil.getLabelDisabledForeground());
        panel.add(k);
        if (node.tags().isEmpty()) {
            panel.add(new JBLabel("—"));
        } else {
            for (String tag : node.tags()) {
                JBLabel chip = new JBLabel(tag);
                chip.setBorder(BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(UIUtil.getBoundsColor()), JBUI.Borders.empty(1, 6)));
                panel.add(chip);
            }
        }
        return panel;
    }

    private JComponent actionButtons(@NotNull LineageNode node) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUIScale.scale(4), JBUIScale.scale(4)));
        panel.setOpaque(false);
        boolean focused = node.id().equals(model.focusId());
        JButton focus = new JButton(focused ? "Exit focus" : "Focus on lineage");
        focus.addActionListener(e -> {
            if (node.id().equals(model.focusId())) {
                model.exitFocus();
            } else {
                model.focusOn(node.id());
            }
        });
        panel.add(focus);
        if (node.fileName() != null) {
            JButton open = new JButton("Open SQL");
            open.addActionListener(e -> LineageActions.openSource(project, node));
            panel.add(open);
            JButton run = new JButton("Run");
            run.addActionListener(e -> LineageActions.runAction(project, node));
            panel.add(run);
        }
        return panel;
    }

    private JComponent dependencyList(@NotNull String title, @NotNull Set<String> ids) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        JBLabel header = new JBLabel(title + " (" + ids.size() + ")");
        header.setForeground(UIUtil.getLabelDisabledForeground());
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(header);
        for (String id : ids) {
            LineageNode dep = model.graph().node(id);
            if (dep == null) continue;
            JButton row = new JButton(GraphCanvas.glyphFor(dep.dataformType()) + "  " + dep.name()
                    + "   " + dep.schema());
            row.setHorizontalAlignment(SwingConstants.LEFT);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            row.addActionListener(e -> model.select(id));
            panel.add(row);
        }
        if (ids.isEmpty()) {
            JBLabel none = new JBLabel("—");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(none);
        }
        panel.add(Box.createVerticalStrut(JBUIScale.scale(4)));
        return panel;
    }

    private static @NotNull Font monospaceBold(@NotNull Font base) {
        return new Font(Font.MONOSPACED, Font.BOLD, base.getSize() + 1);
    }

    private static @NotNull String escape(@Nullable String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
