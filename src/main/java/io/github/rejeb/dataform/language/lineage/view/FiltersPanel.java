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

import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.CollapsibleSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The left sidebar of the project lineage, as in the UI prototype: one collapsible section per
 * filter (action types, tags, schemas) whose header sums up the filter, each a list of rows with a
 * check box, the name and the node count, and one link resetting every filter. The checks mirror
 * the {@link LineageModel} filters and checking a row toggles its filter. A checked type or schema
 * is shown; checked tags narrow the view to the actions having any of them.
 */
public final class FiltersPanel extends JPanel {

    private static final int WIDTH = 240;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_GAP = 8;

    private final LineageModel model;
    private final Filter types;
    private final Filter tags;
    private final Filter schemas;
    private String structure;
    private boolean updating;

    /**
     * Creates the sidebar bound to the given model.
     */
    public FiltersPanel(@NotNull LineageModel model) {
        super(new BorderLayout());
        this.model = model;
        types = new Filter("Action types", true, model::toggleType, model::enabledTypes, TypeGlyphIcon::small);
        tags = new Filter("Tags", true, model::toggleTag, model::enabledTags, item -> null);
        schemas = new Filter("Schemas", false, model::toggleSchema, model::enabledSchemas, item -> null);

        JPanel content = new JPanel(new VerticalLayout(0));
        content.setBackground(UIUtil.getPanelBackground());
        content.setBorder(JBUI.Borders.empty(6, 10, 10, 10));
        content.add(types.section);
        content.add(tags.section);
        content.add(schemas.section);
        JPanel reset = new JPanel(new BorderLayout());
        reset.setOpaque(false);
        reset.setBorder(JBUI.Borders.empty(4, 20, 0, 0));
        reset.add(new ActionLink("Reset all filters", (ActionListener) e -> model.resetFilters()), BorderLayout.WEST);
        content.add(reset);

        JBScrollPane scroll = new JBScrollPane(content,
                JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(JBUI.Borders.empty());
        add(scroll, BorderLayout.CENTER);
        setBorder(JBUI.Borders.customLine(UIUtil.getBoundsColor(), 0, 0, 0, 1));
        Dimension size = new Dimension(JBUIScale.scale(WIDTH), 0);
        setPreferredSize(size);
        setMinimumSize(size);

        refresh();
        model.addListener(m -> SwingUtilities.invokeLater(this::refresh));
    }

    List<Row> typeRows() {
        return types.rows;
    }

    List<Row> tagRows() {
        return tags.rows;
    }

    List<Row> schemaRows() {
        return schemas.rows;
    }

    /**
     * The summaries of the section headers: types, tags, then schemas.
     */
    List<String> summaries() {
        return List.of(types.summary.getText(), tags.summary.getText(), schemas.summary.getText());
    }

    private void refresh() {
        Map<String, Integer> typeCounts = orderedTypeCounts();
        Map<String, Integer> tagCounts = new TreeMap<>(model.tagCounts());
        Map<String, Integer> schemaCounts = orderedSchemaCounts();
        String signature = typeCounts + "|" + tagCounts + "|" + schemaCounts;
        updating = true;
        try {
            if (!signature.equals(structure)) {
                structure = signature;
                types.setItems(typeCounts);
                tags.setItems(tagCounts);
                schemas.setItems(schemaCounts);
            }
            types.updateChecks();
            tags.updateChecks();
            schemas.updateChecks();
        } finally {
            updating = false;
        }
        types.summary.setText(shownOf(typeCounts.keySet(), model.enabledTypes()));
        int selectedTags = model.enabledTags().size();
        tags.summary.setText(selectedTags == 0 ? "any" : selectedTags + " selected");
        schemas.summary.setText(shownOf(schemaCounts.keySet(), model.enabledSchemas()));
    }

    private static @NotNull String shownOf(@NotNull Set<String> items, @NotNull Set<String> enabled) {
        long shown = items.stream().filter(enabled::contains).count();
        return shown + " of " + items.size();
    }

    private @NotNull Map<String, Integer> orderedTypeCounts() {
        Map<String, Integer> counts = model.typeCounts();
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String type : LineageLegend.orderedTypes(counts.keySet())) ordered.put(type, counts.get(type));
        return ordered;
    }

    private @NotNull Map<String, Integer> orderedSchemaCounts() {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String schema : model.schemas()) ordered.put(schema, 0);
        for (LineageNode node : model.graph().nodes()) ordered.merge(node.schema(), 1, Integer::sum);
        return ordered;
    }

    /**
     * One filter row: its check box, its name and its node count.
     */
    final class Row extends JPanel {

        private final String item;
        private final JBCheckBox checkBox = new JBCheckBox();
        private final JBLabel label;
        private final JBLabel count;

        private Row(@NotNull String item, int nodes, @Nullable Icon icon, @NotNull Consumer<Row> onToggle) {
            super(new BorderLayout(JBUIScale.scale(ROW_GAP), 0));
            this.item = item;
            setOpaque(false);
            label = new JBLabel(item, icon, SwingConstants.LEADING);
            label.setIconTextGap(JBUIScale.scale(ROW_GAP));
            count = new JBLabel(String.valueOf(nodes));
            count.setFont(JBFont.medium());
            count.setForeground(UIUtil.getContextHelpForeground());
            checkBox.setOpaque(false);
            checkBox.getAccessibleContext().setAccessibleName(item);
            checkBox.addActionListener(e -> onToggle.accept(this));
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    checkBox.doClick();
                }
            });
            add(checkBox, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
            add(count, BorderLayout.EAST);
        }

        @NotNull String item() {
            return item;
        }

        @NotNull JBCheckBox checkBox() {
            return checkBox;
        }

        @NotNull JBLabel label() {
            return label;
        }

        @NotNull JBLabel count() {
            return count;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, JBUIScale.scale(ROW_HEIGHT));
        }
    }

    private final class Filter {

        private final CollapsibleSection section;
        private final JPanel list = new JPanel(new VerticalLayout(0));
        private final JBLabel summary = new JBLabel();
        private final Consumer<String> toggle;
        private final Supplier<Set<String>> enabled;
        private final Function<String, Icon> icon;
        private final List<Row> rows = new ArrayList<>();

        Filter(@NotNull String title, boolean expanded, @NotNull Consumer<String> toggle,
               @NotNull Supplier<Set<String>> enabled, @NotNull Function<String, Icon> icon) {
            this.toggle = toggle;
            this.enabled = enabled;
            this.icon = icon;
            list.setOpaque(false);
            list.setBorder(JBUI.Borders.emptyBottom(4));
            summary.setFont(JBFont.medium());
            summary.setForeground(UIUtil.getContextHelpForeground());
            section = new CollapsibleSection(title, list);
            section.setBorder(JBUI.Borders.empty());
            section.setTrailing(summary);
            section.setExpanded(expanded);
        }

        void setItems(@NotNull Map<String, Integer> counts) {
            rows.clear();
            list.removeAll();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                Row row = new Row(e.getKey(), e.getValue(), icon.apply(e.getKey()), this::onToggled);
                rows.add(row);
                list.add(row);
            }
            list.revalidate();
            list.repaint();
        }

        void updateChecks() {
            Set<String> on = enabled.get();
            for (Row row : rows) row.checkBox.setSelected(on.contains(row.item));
        }

        private void onToggled(@NotNull Row row) {
            if (updating) return;
            if (enabled.get().contains(row.item) != row.checkBox.isSelected()) toggle.accept(row.item);
        }
    }
}
