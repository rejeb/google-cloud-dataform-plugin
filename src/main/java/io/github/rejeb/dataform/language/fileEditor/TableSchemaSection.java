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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.TreeTableSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.ui.CollapsibleSection;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import io.github.rejeb.dataform.language.ui.RoundedBox;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The schema of one action in the schema preview: its columns in a tree table inside a rounded box,
 * the fields of a RECORD under it, with the type, mode and description of each. A copy button after
 * each name copies it, prefixed by the names of the records holding it.
 */
class TableSchemaSection extends CollapsibleSection {

    private static final String PLACE = "Dataform.PreviewSchema";
    private static final String NO_SCHEMA = "No schema yet";
    private static final String ASSERTION = "assertion";
    private static final String NO_ASSERTION_SCHEMA = "No schema";
    private static final String ASSERTION_REASON = "The plugin does not read the schema of assertions.";
    private static final String REFRESH_SCHEMA = "Refresh schema";
    private static final int HEADER_HEIGHT = 30;
    private static final int ROW_HEIGHT = 26;
    private static final int EMPTY_HEIGHT = 110;
    private static final int CELL_PADDING = 10;
    private static final int NAME_COLUMN = 0;
    private static final int COPY_BUTTON_GAP = 4;

    private final NameRenderer nameRenderer = new NameRenderer();
    private final TreeTableView view;

    /**
     * @param title       the name of the action shown in the header
     * @param type        the Dataform type of the action, giving the icon of the header
     * @param schema      the columns of the action, {@code null} or empty when not known yet
     * @param dryRunError the error the last dry-run of the action failed with, {@code null} if it did not fail
     * @param refresh     refreshes the schemas of the preview
     */
    TableSchemaSection(@NotNull String title, @Nullable String type, @Nullable List<ColumnInfo> schema,
                       @Nullable String dryRunError, @NotNull Runnable refresh) {
        super(title, new JPanel(new BorderLayout()));
        List<ColumnInfo> columns = schema != null ? schema : List.of();
        setIcon(ActionTypeIcons.of(type));
        setSubtitle(columns.isEmpty() ? "no schema" : columns.size() + (columns.size() == 1 ? " column" : " columns"));

        view = new TreeTableView(SchemaTreeModel.of(columns)) {
            @Override
            protected JTableHeader createDefaultTableHeader() {
                return new JBTableHeader() {
                    @Override
                    public Dimension getPreferredSize() {
                        return new Dimension(super.getPreferredSize().width, JBUI.scale(HEADER_HEIGHT));
                    }
                };
            }

            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                TableCellRenderer renderer = getColumnInfo(column).getRenderer(getRowElement(row));
                return renderer != null ? renderer : super.getCellRenderer(row, column);
            }

            @Override
            public void doLayout() {
                int[] widths = SchemaColumnWidths.of(getWidth());
                TableColumnModel model = getColumnModel();
                for (int i = 0; i < widths.length && i < model.getColumnCount(); i++) {
                    model.getColumn(i).setPreferredWidth(widths[i]);
                }
                super.doLayout();
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return getRowCount() == 0 ? new Dimension(size.width, JBUI.scale(EMPTY_HEIGHT)) : size;
            }

            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                String path = copiedPathAt(event.getPoint());
                return path != null ? "Copy " + path : super.getToolTipText(event);
            }
        };
        view.setRootVisible(false);
        view.getTree().setShowsRootHandles(true);
        view.setShowGrid(false);
        view.setRowHeight(JBUI.scale(ROW_HEIGHT));
        view.setBackground(JBColor.lazy(TableSchemaSection::editorBackground));
        view.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        view.setDefaultEditor(Object.class, ReadOnlyTextFields.cellEditor(view));
        view.setTreeCellRenderer(nameRenderer);
        installCopyButtons();
        TreeTableSpeedSearch.installOn(view).setCanExpand(true);
        if (columns.isEmpty()) {
            if (ASSERTION.equals(type)) {
                EmptyStates.show(view.getEmptyText(), NO_ASSERTION_SCHEMA, ASSERTION_REASON);
            } else {
                EmptyStates.show(view.getEmptyText(), NO_SCHEMA, missingSchemaReason(type, dryRunError),
                        REFRESH_SCHEMA, refresh);
            }
        }

        JTableHeader header = view.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setBackground(JBColor.lazy(UIUtil::getPanelBackground));
        header.setDefaultRenderer(headerRenderer());

        setTrailing(Toolbars.horizontal(PLACE, this,
                action("Copy Column Names", DataformIcons.COPY, () -> !columns.isEmpty(),
                        () -> CopyPasteManager.getInstance()
                                .setContents(new StringSelection(SchemaTreeModel.columnNames(columns)))),
                action("Refresh Schema", DataformIcons.REFRESH, () -> true, refresh)).getComponent());

        JPanel table = new JPanel(new BorderLayout());
        table.setOpaque(false);
        table.add(header, BorderLayout.NORTH);
        table.add(view, BorderLayout.CENTER);
        content().add(new RoundedBox(TableSchemaSection::editorBackground, table), BorderLayout.CENTER);
    }

    /**
     * Why an action has no schema yet: a declaration is read from BigQuery, a query with errors has
     * no dry-run schema, and any other table has not been created yet.
     *
     * @param type        the Dataform type of the action
     * @param dryRunError the error the last dry-run of the action failed with, {@code null} if it did not fail
     */
    @NotNull
    static String missingSchemaReason(@Nullable String type, @Nullable String dryRunError) {
        if ("declaration".equals(type)) return "Refresh the schema to load it from BigQuery.";
        if (dryRunError != null && !dryRunError.isBlank()) return "The query has errors. Fix them to get its schema.";
        return "The table does not exist in BigQuery yet.";
    }

    @NotNull
    TreeTableView view() {
        return view;
    }

    /**
     * Where the copy button after the name of a row is, in the coordinates of the table; empty when
     * the row is not shown.
     */
    @NotNull
    Rectangle copyButtonBounds(int row) {
        TreeTableTree tree = view.getTree();
        TreePath path = tree.getPathForRow(row);
        Rectangle node = tree.getRowBounds(row);
        if (path == null || node == null) return new Rectangle();
        Object value = path.getLastPathComponent();
        nameRenderer.getTreeCellRendererComponent(tree, value, false, tree.isExpanded(path),
                tree.getModel().isLeaf(value), row, false);
        Rectangle cell = view.getCellRect(row, NAME_COLUMN, false);
        return new Rectangle(cell.x + node.x + nameRenderer.copyButtonX(), cell.y,
                DataformIcons.COPY.getIconWidth(), cell.height);
    }

    private void installCopyButtons() {
        MouseAdapter copyButtons = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) return;
                String path = copiedPathAt(e.getPoint());
                if (path != null) {
                    CopyPasteManager.getInstance().setContents(new StringSelection(path));
                    e.consume();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                view.setCursor(copiedPathAt(e.getPoint()) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        };
        view.addMouseListener(copyButtons);
        view.addMouseMotionListener(copyButtons);
    }

    @Nullable
    private String copiedPathAt(@NotNull Point point) {
        int row = view.rowAtPoint(point);
        if (row < 0 || view.columnAtPoint(point) != NAME_COLUMN || !copyButtonBounds(row).contains(point)) return null;
        String path = SchemaTreeModel.pathOf(view.getTree().getPathForRow(row).getLastPathComponent());
        return path.isEmpty() ? null : path;
    }

    private final class NameRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
                                          boolean leaf, int row, boolean hasFocus) {
            ColumnInfo column = SchemaTreeModel.columnOf(value);
            if (column == null) return;
            setIcon(column.isRecord() ? DataformIcons.SCHEMA_RECORD : DataformIcons.SCHEMA_COLUMN);
            append(column.name());
            SpeedSearchUtil.applySpeedSearchHighlighting(view, this, true, selected);
        }

        int copyButtonX() {
            return computePreferredSize(false).width + JBUI.scale(COPY_BUTTON_GAP);
        }

        @Override
        public @NotNull Dimension getPreferredSize() {
            Dimension name = computePreferredSize(false);
            return new Dimension(copyButtonX() + DataformIcons.COPY.getIconWidth(), name.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Icon copy = DataformIcons.COPY;
            copy.paintIcon(this, g, copyButtonX(), (getHeight() - copy.getIconHeight()) / 2);
        }
    }

    @NotNull
    private static Color editorBackground() {
        return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    }

    @NotNull
    private static TableCellRenderer headerRenderer() {
        JBLabel label = new JBLabel();
        label.setOpaque(false);
        label.setBorder(JBUI.Borders.empty(0, CELL_PADDING));
        return (table, value, selected, focused, row, column) -> {
            label.setText(value != null ? value.toString() : "");
            label.setFont(JBFont.medium());
            label.setForeground(UIUtil.getContextHelpForeground());
            return label;
        };
    }

    @NotNull
    private static DumbAwareAction action(@NotNull String text, @NotNull Icon icon, @NotNull BooleanSupplier shown,
                                          @NotNull Runnable perform) {
        return new DumbAwareAction(text, text, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                perform.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabledAndVisible(shown.getAsBoolean());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }
}
