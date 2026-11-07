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

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The tree table model of a schema: one node per column, the fields of a RECORD as its children.
 */
final class SchemaTreeModel {

    private static final int CELL_PADDING = 10;

    private SchemaTreeModel() {
    }

    /**
     * Builds the model of the given columns, under a hidden root.
     */
    @NotNull
    static ListTreeTableModelOnColumns of(@NotNull List<ColumnInfo> columns) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        columns.forEach(column -> root.add(nodeOf(column)));
        return new ListTreeTableModelOnColumns(root, new com.intellij.util.ui.ColumnInfo[]{
                new TreeColumnInfo("Name"),
                new ValueColumn("Type", ColumnInfo::type, new ValueRenderer(SchemaTreeModel::keywordColor,
                        SchemaTreeModel::editorFont)),
                new ValueColumn("Mode", ColumnInfo::mode, new ValueRenderer(UIUtil::getContextHelpForeground, null)),
                new ValueColumn("Description", column -> column.description() != null ? column.description() : "",
                        new ValueRenderer(UIUtil::getContextHelpForeground, null))
        });
    }

    /**
     * The names of the top-level columns, one per line.
     */
    @NotNull
    static String columnNames(@NotNull List<ColumnInfo> columns) {
        return columns.stream().map(ColumnInfo::name).collect(Collectors.joining("\n"));
    }

    /**
     * The name of the column of a node after the names of the records holding it, dot separated
     * from the top-level column down, such as {@code address.geo.lat}; empty for the root.
     */
    @NotNull
    static String pathOf(@Nullable Object node) {
        Deque<String> names = new ArrayDeque<>();
        for (TreeNode current = node instanceof TreeNode treeNode ? treeNode : null; current != null;
             current = current.getParent()) {
            ColumnInfo column = columnOf(current);
            if (column != null) names.addFirst(column.name());
        }
        return String.join(".", names);
    }

    /**
     * The column a node of the model stands for, {@code null} for the root.
     */
    @Nullable
    static ColumnInfo columnOf(@Nullable Object node) {
        return node instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof ColumnInfo column
                ? column : null;
    }

    @NotNull
    private static DefaultMutableTreeNode nodeOf(@NotNull ColumnInfo column) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(column);
        column.subFields().forEach(field -> node.add(nodeOf(field)));
        return node;
    }

    @NotNull
    private static Color keywordColor() {
        TextAttributes keyword = scheme().getAttributes(DefaultLanguageHighlighterColors.KEYWORD);
        return keyword != null && keyword.getForegroundColor() != null
                ? keyword.getForegroundColor() : UIUtil.getLabelForeground();
    }

    @NotNull
    private static Font editorFont() {
        return scheme().getFont(EditorFontType.PLAIN).deriveFont(JBFont.medium().getSize2D());
    }

    @NotNull
    private static EditorColorsScheme scheme() {
        return EditorColorsManager.getInstance().getGlobalScheme();
    }

    private static final class ValueRenderer extends DefaultTableCellRenderer {

        private final Supplier<Color> foreground;
        private final @Nullable Supplier<Font> font;

        ValueRenderer(@NotNull Supplier<Color> foreground, @Nullable Supplier<Font> font) {
            this.foreground = foreground;
            this.font = font;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            if (!selected) setForeground(foreground.get());
            if (font != null) setFont(font.get());
            setBorder(JBUI.Borders.empty(0, CELL_PADDING));
            return this;
        }
    }

    private static final class ValueColumn extends com.intellij.util.ui.ColumnInfo<Object, String> {

        private final Function<ColumnInfo, String> value;
        private final TableCellRenderer renderer;

        ValueColumn(@NotNull String name, @NotNull Function<ColumnInfo, String> value,
                    @NotNull TableCellRenderer renderer) {
            super(name);
            this.value = value;
            this.renderer = renderer;
        }

        @Override
        public @NotNull TableCellRenderer getRenderer(Object node) {
            return renderer;
        }

        @Override
        public @Nullable String valueOf(Object node) {
            ColumnInfo column = columnOf(node);
            return column != null ? value.apply(column) : "";
        }

        @Override
        public boolean isCellEditable(Object node) {
            return true;
        }
    }
}
