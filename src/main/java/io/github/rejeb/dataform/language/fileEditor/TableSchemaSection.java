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

import com.intellij.icons.AllIcons;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;

class TableSchemaSection extends JPanel {

    private final JBTable schemaTable;
    private final DefaultTableModel tableModel;
    private final JPanel contentPanel;
    private boolean expanded = true;
    private final JPanel header;
    private final JLabel emptyLabel;

    TableSchemaSection(String tableName, List<ColumnInfo> schema) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.emptyBottom(8));

        header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(UIUtil.getPanelBackground().brighter());
        header.setBorder(JBUI.Borders.empty(5, 8));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel toggleIcon = new JLabel(AllIcons.General.ArrowDown);
        JLabel tableLabel = new JLabel(tableName != null ? tableName : "Unknown table");
        tableLabel.setFont(JBUI.Fonts.label(12).asBold());
        tableLabel.setForeground(UIUtil.getLabelForeground());
        tableLabel.setBorder(JBUI.Borders.emptyLeft(6));

        header.add(toggleIcon, BorderLayout.WEST);
        header.add(tableLabel, BorderLayout.CENTER);

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(JBUI.Borders.empty(8, 12, 4, 12));

        String[] columnNames = {"Column Name", "Type", "Mode", "Description"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        schemaTable = new JBTable(tableModel);
        schemaTable.setShowGrid(true);
        schemaTable.setGridColor(UIUtil.getBoundsColor());
        schemaTable.setIntercellSpacing(new Dimension(1, 1));
        schemaTable.setRowHeight(28);
        schemaTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        schemaTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        schemaTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        schemaTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        schemaTable.getColumnModel().getColumn(3).setPreferredWidth(300);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(JBUI.Borders.empty(4, 8));
        for (int i = 0; i < 4; i++) {
            schemaTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JTableHeader tableHeader = schemaTable.getTableHeader();
        tableHeader.setFont(JBUI.Fonts.label().asBold());
        tableHeader.setBackground(UIUtil.getPanelBackground().brighter());

        emptyLabel = new JLabel("No schema information available", SwingConstants.CENTER);
        emptyLabel.setForeground(UIUtil.getInactiveTextColor());
        emptyLabel.setFont(JBUI.Fonts.label(12));
        emptyLabel.setBorder(JBUI.Borders.empty(20));

        if (schema != null && !schema.isEmpty()) {
            for (ColumnInfo column : schema) {
                addColumnRow(column, 0);
            }

            int totalRows = tableModel.getRowCount();

            schemaTable.setPreferredSize(new Dimension(0, (totalRows + 1) * 28 + 10));
            schemaTable.setMaximumSize(new Dimension(Integer.MAX_VALUE, (totalRows + 1) * 28 + 10));

            JPanel tableWrapper = new JPanel(new BorderLayout());
            tableWrapper.setOpaque(false);
            tableWrapper.add(schemaTable.getTableHeader(), BorderLayout.NORTH);
            tableWrapper.add(schemaTable, BorderLayout.CENTER);

            contentPanel.add(tableWrapper, BorderLayout.CENTER);
        } else {
            contentPanel.add(emptyLabel, BorderLayout.CENTER);
        }

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                expanded = !expanded;
                contentPanel.setVisible(expanded);
                toggleIcon.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
                JComponent parent = (JComponent) getParent();
                if (parent != null) parent.revalidate();
                revalidate();
                repaint();
            }
        });
    }

    private void addColumnRow(ColumnInfo column, int indentLevel) {
        String indentedName = "  ".repeat(indentLevel) + column.name();
        String description = column.description() != null ? column.description() : "";

        tableModel.addRow(new Object[]{
                indentedName,
                column.type(),
                column.mode(),
                description
        });

        if (column.isRecord() && !column.subFields().isEmpty()) {
            for (ColumnInfo subField : column.subFields()) {
                addColumnRow(subField, indentLevel + 1);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (!expanded) {
            Dimension h = header.getPreferredSize();
            Insets ins = getInsets();
            return new Dimension(super.getPreferredSize().width,
                    h.height + ins.top + ins.bottom);
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        if (!expanded) {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public void dispose() {
    }
}