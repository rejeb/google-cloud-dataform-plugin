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

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Left sidebar with collapsible filter sections bound to the {@link LineageModel},
 * rendered to match the design reference: action-type rows (checkbox + glyph + label +
 * count), wrapping tag pill chips, schema rows (inverse-default), a focus card, a
 * fit-to-view button, and a type legend. Non-resizable; its width is sized to the longest
 * element that cannot wrap. Rebuilt only when a relevant signature changes.
 */
public final class FiltersPanel extends JPanel {

    private static final List<String> CANONICAL_TYPES = List.of(
            "declaration", "view", "incremental", "table", "operation", "assertion");
    private static final int MIN_WIDTH = 210;
    private static final int MAX_WIDTH = 360;

    private final LineageModel model;
    private final Runnable onFit;
    private final JPanel content = new JPanel(new VerticalLayout(0));
    private final Map<String, Boolean> sectionOpen = new HashMap<>();
    private String lastSignature;
    private int contentWidth = JBUI.scale(240);

    public FiltersPanel(@NotNull LineageModel model, @NotNull Runnable onFit) {
        super(new BorderLayout());
        this.model = model;
        this.onFit = onFit;
        content.setBackground(UIUtil.getPanelBackground());
        content.setBorder(JBUI.Borders.emptyTop(4));

        sectionOpen.put("Action types", true);
        sectionOpen.put("Tags", true);
        sectionOpen.put("Schemas", false);
        sectionOpen.put("Focus", false);

        JBScrollPane scroll = new JBScrollPane(content,
                JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(JBUI.Borders.empty());
        add(scroll, BorderLayout.CENTER);

        rebuild();
        model.addListener(m -> onModelChanged());
    }

    private void onModelChanged() {
        String signature = model.typeCounts().keySet() + "|" + model.tagCounts().keySet() + "|"
                + model.schemas() + "|" + model.enabledTypes() + "|" + model.enabledTags() + "|"
                + model.enabledSchemas() + "|" + model.focusId();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        SwingUtilities.invokeLater(this::rebuild);
    }

    private void rebuild() {
        contentWidth = computeWidth();
        setPreferredSize(new Dimension(contentWidth, 0));
        setMinimumSize(new Dimension(contentWidth, 0));
        setMaximumSize(new Dimension(contentWidth, Integer.MAX_VALUE));

        content.removeAll();
        content.add(typesSection());
        content.add(tagsSection());
        content.add(schemasSection());
        content.add(focusSection());
        content.add(legend());
        content.revalidate();
        content.repaint();
        revalidate();
    }

    private @NotNull List<String> displayTypes() {
        Set<String> present = model.typeCounts().keySet();
        Set<String> ordered = new LinkedHashSet<>();
        for (String type : CANONICAL_TYPES) {
            if (present.contains(type)) ordered.add(type);
        }
        ordered.addAll(present);
        return new ArrayList<>(ordered);
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private JComponent typesSection() {
        int enabled = model.enabledTypes().size();
        int total = model.typeCounts().size();
        Runnable clear = enabled != total ? this::enableAllTypes : null;
        Section section = new Section("Action types", enabled + "/" + total, clear);
        for (String type : displayTypes()) {
            int count = model.typeCounts().getOrDefault(type, 0);
            section.body.add(checkRow(GraphCanvas.glyphFor(type), GraphCanvas.typeColor(type),
                    type, count, model.enabledTypes().contains(type), () -> model.toggleType(type), false));
        }
        return section;
    }

    private JComponent tagsSection() {
        int selected = model.enabledTags().size();
        Runnable clear = selected > 0 ? this::clearTags : null;
        Section section = new Section("Tags", selected > 0 ? String.valueOf(selected)
                : String.valueOf(model.tagCounts().size()), clear);
        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4)));
        chips.setOpaque(false);
        model.tagCounts().entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> chips.add(new Chip(e.getKey(), e.getValue(),
                        model.enabledTags().contains(e.getKey()), () -> model.toggleTag(e.getKey()))));
        if (model.tagCounts().isEmpty()) {
            section.body.add(hint("No tags."));
        } else {
            section.body.add(chips);
            section.body.add(hint(selected == 0 ? "No tag filter — all shown"
                    : "Match ANY selected (" + selected + ")"));
        }
        return section;
    }

    private JComponent schemasSection() {
        int enabled = model.enabledSchemas().size();
        int total = model.schemas().size();
        Runnable clear = enabled != total ? this::enableAllSchemas : null;
        Section section = new Section("Schemas", enabled + "/" + total, clear);
        Map<String, Integer> counts = schemaCounts();
        for (String schema : model.schemas()) {
            boolean shown = model.enabledSchemas().contains(schema);
            section.body.add(checkRow(null, null, schema, counts.getOrDefault(schema, 0),
                    shown, () -> model.toggleSchema(schema), true));
        }
        if (model.schemas().isEmpty()) section.body.add(hint("No schemas."));
        return section;
    }

    private JComponent focusSection() {
        Section section = new Section("Focus", null, null);
        String focusId = model.focusId();
        if (focusId != null) {
            LineageNode node = model.graph().node(focusId);
            JPanel card = new JPanel(new VerticalLayout(JBUI.scale(4)));
            card.setBorder(BorderFactory.createCompoundBorder(
                    JBUI.Borders.customLine(UIUtil.getBoundsColor()), JBUI.Borders.empty(8)));
            card.setOpaque(false);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.add(dim("FOCUSED ON"), BorderLayout.WEST);
            row.add(link("exit", model::exitFocus), BorderLayout.EAST);
            card.add(row);
            card.add(new JBLabel(GraphCanvas.glyphFor(node != null ? node.dataformType() : "")
                    + "  " + (node != null ? node.name() : focusId)));
            card.add(hint("Showing only upstream + downstream of this node."));
            section.body.add(card);
        } else {
            section.body.add(hint("Right-click a node → Focus on lineage to isolate its dependencies."));
        }
        section.body.add(fitButton());
        return section;
    }

    private JComponent legend() {
        JPanel panel = new JPanel(new VerticalLayout(JBUI.scale(2)));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.empty(12, 12, 0, 12),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createDashedBorder(UIUtil.getBoundsColor()),
                        JBUI.Borders.empty(8))));
        panel.add(dim("LEGEND"));
        for (String type : displayTypes()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
            row.setOpaque(false);
            row.add(glyphLabel(GraphCanvas.glyphFor(type), GraphCanvas.typeColor(type)));
            row.add(new JBLabel(type));
            panel.add(row);
        }
        return panel;
    }

    // ------------------------------------------------------------------
    // Rows / widgets
    // ------------------------------------------------------------------

    private JComponent checkRow(@Nullable String glyph, @Nullable Color glyphColor, @NotNull String label,
                                int count, boolean checked, @NotNull Runnable onToggle, boolean mono) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.empty(2, 4));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        left.setOpaque(false);
        JBCheckBox box = new JBCheckBox(null, checked);
        box.setOpaque(false);
        box.addActionListener(e -> onToggle.run());
        left.add(box);
        if (glyph != null && glyphColor != null) left.add(glyphLabel(glyph, glyphColor));
        JBLabel name = new JBLabel(label);
        if (mono) name.setFont(monospace(name.getFont()));
        left.add(name);
        row.add(left, BorderLayout.WEST);

        JBLabel countLabel = new JBLabel(String.valueOf(count));
        countLabel.setFont(monospace(countLabel.getFont()));
        countLabel.setForeground(UIUtil.getLabelDisabledForeground());
        row.add(countLabel, BorderLayout.EAST);

        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                box.doClick();
            }
        });
        return row;
    }

    private JComponent fitButton() {
        JPanel button = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        button.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(UIUtil.getBoundsColor()), JBUI.Borders.empty(5, 8)));
        button.add(new JBLabel("Fit graph to view"));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onFit.run();
            }
        });
        return button;
    }

    private static JBLabel glyphLabel(@NotNull String glyph, @NotNull Color color) {
        JBLabel label = new JBLabel(glyph);
        label.setForeground(color);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f)));
        return label;
    }

    private static JBLabel dim(@NotNull String text) {
        JBLabel label = new JBLabel(text);
        label.setForeground(UIUtil.getLabelDisabledForeground());
        label.setFont(label.getFont().deriveFont(JBUI.scale(10f)));
        return label;
    }

    private JBLabel hint(@NotNull String text) {
        int wrap = Math.max(JBUI.scale(120), contentWidth - JBUI.scale(44));
        JBLabel label = new JBLabel("<html><div width='" + wrap + "'>" + text + "</div></html>");
        label.setForeground(UIUtil.getLabelDisabledForeground());
        label.setBorder(JBUI.Borders.emptyTop(4));
        return label;
    }

    private static JComponent link(@NotNull String text, @NotNull Runnable action) {
        JBLabel label = new JBLabel(text);
        label.setForeground(accent());
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return label;
    }

    private static Font monospace(@NotNull Font base) {
        return new Font(Font.MONOSPACED, Font.PLAIN, base.getSize());
    }

    private static Color accent() {
        return new JBColor(new Color(0x3574F0), new Color(0x548AF7));
    }

    private void enableAllTypes() {
        for (String type : model.typeCounts().keySet()) {
            if (!model.enabledTypes().contains(type)) model.toggleType(type);
        }
    }

    private void clearTags() {
        for (String tag : model.enabledTags()) model.toggleTag(tag);
    }

    private void enableAllSchemas() {
        for (String schema : model.schemas()) {
            if (!model.enabledSchemas().contains(schema)) model.toggleSchema(schema);
        }
    }

    private Map<String, Integer> schemaCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (LineageNode node : model.graph().nodes()) counts.merge(node.schema(), 1, Integer::sum);
        return counts;
    }

    // ------------------------------------------------------------------
    // Width sizing (longest non-wrapping element)
    // ------------------------------------------------------------------

    private int computeWidth() {
        Font base = getFont() != null ? getFont() : UIUtil.getLabelFont();
        FontMetrics fm = getFontMetrics(base);
        FontMetrics mono = getFontMetrics(new Font(Font.MONOSPACED, Font.PLAIN, base.getSize()));
        int text = 0;

        int rowChrome = JBUI.scale(20 + 16 + 6 + 14);
        for (String type : displayTypes()) {
            text = Math.max(text, rowChrome + fm.stringWidth(type));
        }
        for (String schema : model.schemas()) {
            text = Math.max(text, JBUI.scale(20 + 14) + mono.stringWidth(schema));
        }
        for (Map.Entry<String, Integer> e : model.tagCounts().entrySet()) {
            text = Math.max(text, mono.stringWidth(e.getKey() + "  " + e.getValue()) + JBUI.scale(26));
        }
        for (String title : new String[]{"Action types", "Tags", "Schemas", "Focus"}) {
            text = Math.max(text, fm.stringWidth("▾  " + title) + JBUI.scale(60));
        }
        for (String type : displayTypes()) {
            text = Math.max(text, JBUI.scale(24) + fm.stringWidth(type));
        }
        String focusId = model.focusId();
        if (focusId != null) {
            LineageNode node = model.graph().node(focusId);
            text = Math.max(text, JBUI.scale(24) + fm.stringWidth(node != null ? node.name() : focusId));
        }

        int total = text + JBUI.scale(12 + 12 + 12);
        return Math.max(JBUI.scale(MIN_WIDTH), Math.min(JBUI.scale(MAX_WIDTH), total));
    }

    // ------------------------------------------------------------------
    // Collapsible section
    // ------------------------------------------------------------------

    private final class Section extends JPanel {
        private final JPanel body = new JPanel(new VerticalLayout(JBUI.scale(2)));

        Section(@NotNull String title, @Nullable String count, @Nullable Runnable onClear) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 1, 0));
            body.setOpaque(false);
            body.setBorder(JBUI.Borders.empty(2, 12, 10, 12));

            boolean open = sectionOpen.getOrDefault(title, true);
            body.setVisible(open);

            JPanel head = new JPanel(new BorderLayout(JBUI.scale(6), 0));
            head.setOpaque(false);
            head.setBorder(JBUI.Borders.empty(8, 12));
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JBLabel titleLabel = new JBLabel((open ? "▾  " : "▸  ") + title);
            head.add(titleLabel, BorderLayout.WEST);

            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0));
            east.setOpaque(false);
            if (count != null) {
                JBLabel countLabel = new JBLabel(count);
                countLabel.setFont(monospace(countLabel.getFont()).deriveFont(JBUI.scale(10f)));
                countLabel.setForeground(UIUtil.getLabelDisabledForeground());
                east.add(countLabel);
            }
            if (onClear != null) east.add(link("clear", onClear));
            head.add(east, BorderLayout.EAST);

            head.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    boolean next = !body.isVisible();
                    body.setVisible(next);
                    sectionOpen.put(title, next);
                    titleLabel.setText((next ? "▾  " : "▸  ") + title);
                    revalidate();
                    repaint();
                }
            });

            add(head, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
        }
    }

    // ------------------------------------------------------------------
    // Tag pill chip
    // ------------------------------------------------------------------

    private static final class Chip extends JComponent {
        private final String text;
        private boolean active;
        private final Runnable onClick;

        Chip(@NotNull String tag, int count, boolean active, @NotNull Runnable onClick) {
            this.text = tag + "  " + count;
            this.active = active;
            this.onClick = onClick;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(10.5f)));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Chip.this.active = !Chip.this.active;
                    repaint();
                    onClick.run();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int textW = getFontMetrics(getFont()).stringWidth(text);
            return new Dimension(textW + JBUI.scale(26), JBUI.scale(20));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(active ? tint(accent(), 0.18f) : UIUtil.getPanelBackground());
                g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.setColor(active ? accent() : UIUtil.getBoundsColor());
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

                int dot = JBUI.scale(6);
                int dy = (h - dot) / 2;
                g2.setColor(active ? accent() : UIUtil.getLabelDisabledForeground());
                g2.fillOval(JBUI.scale(8), dy, dot, dot);

                g2.setColor(active ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, JBUI.scale(8) + dot + JBUI.scale(5),
                        (h + fm.getAscent() - fm.getDescent()) / 2);
            } finally {
                g2.dispose();
            }
        }

        private static Color tint(@NotNull Color base, float alpha) {
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) (alpha * 255));
        }
    }
}
