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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SeparatorOrientation;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import io.github.rejeb.dataform.language.ui.Toolbars;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Right-hand details panel of the project lineage, as in the UI prototype: the glyph, name, schema
 * and type of the selected action, buttons to open its SQLX, run it and focus on its lineage, its
 * file, schema and tags, then its upstream and downstream actions, a click selecting one. Shows an
 * empty state while nothing is selected.
 */
public final class DetailsPanel extends JPanel {

    private static final String PLACE = "Dataform.Lineage.Details";
    private static final int KEY_WIDTH = 70;
    private static final int ROW_HEIGHT = 26;
    private static final String NO_VALUE = "—";

    private final Project project;
    private final LineageModel model;
    private final JPanel north = new JPanel(new BorderLayout());
    private final JPanel headerHolder = new JPanel(new BorderLayout());
    private @NotNull Runnable hideHandler = () -> {};
    private final InplaceButton minus = new InplaceButton(new IconButton("Hide details", LineageIcons.MINUS),
            e -> hideHandler.run());
    private final StatusText emptyText = new StatusText(this) {
        @Override
        protected boolean isStatusVisible() {
            return isEmptyStateShown();
        }
    };
    private final JPanel content = new WidthTrackingPanel();
    private final JBScrollPane scroll;
    private final ActionToolbar toolbar;
    private final DependencyList upstream;
    private final DependencyList downstream;
    private final DependencyList upstreamColumns;
    private final DependencyList downstreamColumns;
    private final Map<String, JComponent> values = new LinkedHashMap<>();
    private @Nullable LineageNode node;
    private @Nullable JBTextField title;
    private @Nullable JBLabel subtitle;
    private @Nullable JBLabel glyph;
    private String lastSignature;

    /**
     * Creates the panel bound to the given model.
     */
    public DetailsPanel(@NotNull Project project, @NotNull LineageModel model) {
        super(new BorderLayout());
        this.project = project;
        this.model = model;
        setBackground(UIUtil.getPanelBackground());
        emptyText.setText("Select a node to see its details");

        upstream = new DependencyList("Upstream", "No upstream actions", new NodeRenderer(), this::nodeName, model::select);
        downstream = new DependencyList("Downstream", "No downstream actions", new NodeRenderer(), this::nodeName, model::select);
        upstreamColumns = new DependencyList("Upstream columns", "No upstream columns", new ColumnRenderer(), this::columnName, model::selectColumn);
        downstreamColumns = new DependencyList("Downstream columns", "No downstream columns", new ColumnRenderer(), this::columnName, model::selectColumn);

        toolbar = Toolbars.horizontal(PLACE, this,
                Toolbars.withText(nodeAction("Open SQLX", "Open the SQLX file of the action", DataformIcons.FILE,
                        false, LineageActions::openSource)),
                Toolbars.withText(nodeAction("Run", "Run the action", DataformIcons.RUN,
                        true, LineageActions::runAction)),
                Toolbars.withText(LineageToolbarSupport.focusToggle(model)));

        headerHolder.setOpaque(false);
        JPanel minusHolder = new JPanel(new BorderLayout());
        minusHolder.setOpaque(false);
        minusHolder.setBorder(JBUI.Borders.empty(10, 0, 0, 8));
        minusHolder.add(minus, BorderLayout.NORTH);
        JComponent toolbarComponent = toolbar.getComponent();
        toolbarComponent.setBorder(JBUI.Borders.empty(0, 6, 6, 6));
        north.setOpaque(false);
        north.add(headerHolder, BorderLayout.CENTER);
        north.add(minusHolder, BorderLayout.EAST);
        north.add(toolbarComponent, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        content.setOpaque(false);
        content.setBorder(JBUI.Borders.empty(12, 12, 12, 8));
        scroll = new JBScrollPane(content,
                JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        rebuild();
        model.addListener(m -> onModelChanged());
    }

    /**
     * Sets the action run when the header's minus button is pressed: hiding the panel without
     * clearing the selection.
     */
    public void setHideHandler(@NotNull Runnable handler) {
        this.hideHandler = handler;
    }

    /**
     * Whether the panel shows its empty state, as while nothing is selected.
     */
    public boolean isEmptyStateShown() {
        return node == null;
    }

    /**
     * The message shown while nothing is selected.
     */
    public @NotNull StatusText getEmptyText() {
        return emptyText;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        emptyText.paint(this, g);
    }

    ActionToolbar toolbar() {
        return toolbar;
    }

    JBList<String> upstream() {
        return upstream.list;
    }

    JBList<String> downstream() {
        return downstream.list;
    }

    @Nullable String headerTitle() {
        return title != null ? title.getText() : null;
    }

    @Nullable String headerSubtitle() {
        return subtitle != null ? subtitle.getText() : null;
    }

    @Nullable Icon headerIcon() {
        return glyph != null ? glyph.getIcon() : null;
    }

    /**
     * The text shown for the given key of the form rows, such as "Tags".
     */
    @Nullable String valueOf(@NotNull String key) {
        JComponent value = values.get(key);
        if (value instanceof JBTextField field) return field.getText();
        if (value instanceof ActionLink link) return link.getText();
        if (value instanceof JBLabel label) return label.getText();
        return null;
    }

    private void onModelChanged() {
        String signature = System.identityHashCode(model.graph()) + "|" + model.selectedId()
                + "|" + model.focusId() + "|" + model.selectedColumnId();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        SwingUtilities.invokeLater(this::rebuild);
    }

    private void rebuild() {
        content.removeAll();
        headerHolder.removeAll();
        values.clear();
        title = null;
        subtitle = null;
        glyph = null;
        node = shownNode();
        LineageNode shown = node;
        north.setBorder(shown != null
                ? JBUI.Borders.customLine(JBUI.CurrentTheme.Separator.color(), 0, 0, 1, 0)
                : JBUI.Borders.empty());
        toolbar.getComponent().setVisible(shown != null);
        scroll.setVisible(shown != null);
        if (shown != null) {
            headerHolder.add(header(shown), BorderLayout.CENTER);
            content.add(form(shown));
            upstream.show(content, model.graph().predecessors(shown.id()), false);
            downstream.show(content, model.graph().successors(shown.id()), true);
            ColumnLineageGraph columns = model.columnGraph();
            String columnId = model.selectedColumnId();
            if (columnId != null && columns != null) {
                ColumnRef selected = columns.column(columnId);
                if (selected != null) {
                    content.add(separator("Column lineage", null, true));
                    content.add(columnForm(selected));
                }
                upstreamColumns.show(content, columns.predecessors(columnId), true);
                downstreamColumns.show(content, columns.successors(columnId), true);
            }
        }
        toolbar.updateActionsAsync();
        revalidate();
        repaint();
    }

    private @Nullable LineageNode shownNode() {
        LineageNode selected = model.selectedId() != null ? model.graph().node(model.selectedId()) : null;
        if (selected == null && model.selectedColumnId() != null && model.columnGraph() != null) {
            ColumnRef ref = model.columnGraph().column(model.selectedColumnId());
            if (ref != null) selected = model.graph().node(ref.tableNodeId());
        }
        return selected;
    }

    private @NotNull JComponent header(@NotNull LineageNode shown) {
        JPanel panel = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(10, 12, 6, 0));

        glyph = new JBLabel(TypeGlyphIcon.of(shown.dataformType()));
        glyph.setVerticalAlignment(SwingConstants.TOP);
        glyph.setBorder(JBUI.Borders.emptyTop(1));
        panel.add(glyph, BorderLayout.WEST);

        JPanel texts = new JPanel(new VerticalLayout(JBUIScale.scale(2)));
        texts.setOpaque(false);
        title = ReadOnlyTextFields.singleLine(shown.name());
        title.setToolTipText(shown.fullName());
        title.setFont(JBFont.label().biggerOn(1f).asBold());
        texts.add(title);
        subtitle = new JBLabel(UiText.dot(shown.schema(), shown.dataformType()));
        subtitle.setFont(JBFont.medium());
        subtitle.setForeground(UIUtil.getContextHelpForeground());
        texts.add(subtitle);
        panel.add(texts, BorderLayout.CENTER);
        return panel;
    }

    private @NotNull JComponent form(@NotNull LineageNode shown) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(JBUI.Borders.emptyBottom(12));
        JComponent file;
        if (shown.fileName() != null) {
            ActionLink link = new ActionLink(shown.fileName(), (ActionListener) e -> LineageActions.openSource(project, shown));
            link.setToolTipText(shown.fileName());
            link.setMinimumSize(new Dimension(0, link.getPreferredSize().height));
            file = link;
        } else {
            file = ReadOnlyTextFields.singleLine(NO_VALUE);
        }
        addRow(form, 0, "File", file);
        addRow(form, 1, "Schema", ReadOnlyTextFields.singleLine(shown.schema()));
        addRow(form, 2, "Tags", ReadOnlyTextFields.singleLine(
                shown.tags().isEmpty() ? NO_VALUE : String.join(", ", shown.tags())));
        return form;
    }

    private @NotNull JComponent columnForm(@NotNull ColumnRef column) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        JBTextField table = ReadOnlyTextFields.singleLine(tableWithoutProject(column.tableFullName()));
        table.setToolTipText(column.tableFullName());
        addRow(form, 0, "Column", ReadOnlyTextFields.singleLine(column.columnName()));
        addRow(form, 1, "Table", table);
        return form;
    }

    private static @NotNull String tableWithoutProject(@NotNull String tableFullName) {
        return tableFullName.substring(tableFullName.indexOf('.') + 1);
    }

    private void addRow(@NotNull JPanel form, int row, @NotNull String key, @NotNull JComponent value) {
        JBLabel label = new JBLabel(key);
        label.setForeground(UIUtil.getContextHelpForeground());
        label.setPreferredSize(new Dimension(JBUIScale.scale(KEY_WIDTH), label.getPreferredSize().height));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insetsTop(row == 0 ? 0 : 8);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = JBUI.insets(row == 0 ? 0 : 8, 12, 0, 0);
        form.add(value, c);
        values.put(key, value);
    }

    private static @NotNull JComponent separator(@NotNull String title, @Nullable String count, boolean spaced) {
        JPanel row = new JPanel(new BorderLayout(JBUIScale.scale(8), 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyTop(spaced ? 10 : 0));
        JPanel texts = new JPanel(new HorizontalLayout(JBUIScale.scale(8), SwingConstants.CENTER));
        texts.setOpaque(false);
        JBLabel name = new JBLabel(title);
        name.setFont(JBFont.label().asBold());
        texts.add(name);
        if (count != null) {
            JBLabel number = new JBLabel(count);
            number.setForeground(UIUtil.getContextHelpForeground());
            texts.add(number);
        }
        row.add(texts, BorderLayout.WEST);
        JPanel line = new JPanel(new GridBagLayout());
        line.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        line.add(new SeparatorComponent(JBUI.CurrentTheme.Separator.color(), SeparatorOrientation.HORIZONTAL), c);
        row.add(line, BorderLayout.CENTER);
        return row;
    }

    private AnAction nodeAction(@NotNull String text, @NotNull String description, @NotNull Icon icon,
                                boolean hiddenForDeclarations, @NotNull java.util.function.BiConsumer<Project, LineageNode> run) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (node != null) run.accept(project, node);
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                boolean declaration = node != null && "declaration".equals(node.dataformType());
                e.getPresentation().setVisible(!(hiddenForDeclarations && declaration));
                e.getPresentation().setEnabled(node != null && node.fileName() != null);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    private @NotNull String nodeName(@NotNull String id) {
        LineageNode dependency = model.graph().node(id);
        return dependency != null ? dependency.name() : id;
    }

    private @NotNull String columnName(@NotNull String id) {
        ColumnLineageGraph columns = model.columnGraph();
        ColumnRef ref = columns != null ? columns.column(id) : null;
        return ref != null ? ref.columnName() : id;
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        private WidthTrackingPanel() {
            super(new VerticalLayout(0));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return JBUIScale.scale(ROW_HEIGHT);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class DependencyList {

        private final String title;
        private final JBLabel empty;
        private final CollectionListModel<String> items = new CollectionListModel<>();
        private final JBList<String> list = new JBList<>(items);

        DependencyList(@NotNull String title, @NotNull String emptyText,
                       @NotNull ColoredListCellRenderer<String> renderer,
                       @NotNull Function<String, String> searchText,
                       @NotNull Consumer<String> onSelect) {
            this.title = title;
            this.empty = new JBLabel(emptyText);
            empty.setForeground(UIUtil.getContextHelpForeground());
            empty.setBorder(JBUI.Borders.empty(4, 8));
            list.setCellRenderer(renderer);
            list.setFixedCellHeight(JBUIScale.scale(ROW_HEIGHT));
            list.setOpaque(false);
            list.setBackground(UIUtil.getPanelBackground());
            list.setBorder(JBUI.Borders.emptyTop(4));
            ListSpeedSearch.installOn(list, searchText);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0) onSelect.accept(items.getElementAt(index));
                }
            });
            list.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    String id = list.getSelectedValue();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER && id != null) onSelect.accept(id);
                }
            });
        }

        void show(@NotNull JPanel parent, @NotNull Collection<String> ids, boolean spaced) {
            items.replaceAll(List.copyOf(ids));
            parent.add(separator(title, String.valueOf(ids.size()), spaced));
            parent.add(ids.isEmpty() ? empty : list);
        }
    }

    private final class NodeRenderer extends ColoredListCellRenderer<String> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends String> list, String id, int index,
                                             boolean selected, boolean hasFocus) {
            setIconTextGap(JBUIScale.scale(8));
            LineageNode dependency = model.graph().node(id);
            if (dependency == null) {
                append(id);
                return;
            }
            setIcon(TypeGlyphIcon.small(dependency.dataformType()));
            append(dependency.name());
            append("  " + dependency.schema(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    private final class ColumnRenderer extends ColoredListCellRenderer<String> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends String> list, String id, int index,
                                             boolean selected, boolean hasFocus) {
            ColumnLineageGraph columns = model.columnGraph();
            ColumnRef ref = columns != null ? columns.column(id) : null;
            if (ref == null) {
                append(id);
                return;
            }
            append(ref.columnName());
            append("  " + ref.tableFullName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }
}
