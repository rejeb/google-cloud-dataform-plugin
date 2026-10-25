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

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

class TableSchemaSection extends CollapsibleSection {

    TableSchemaSection(String tableName, List<ColumnInfo> schema) {
        super(tableName, new JPanel(new BorderLayout()));

        String[] columnNames = {"Column Name", "Type", "Mode", "Description"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };

        JBTable schemaTable = new JBTable(tableModel);
        schemaTable.setDefaultEditor(Object.class, ReadOnlyTextFields.cellEditor(schemaTable));
        schemaTable.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
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

        JLabel emptyLabel = new JLabel("No schema information available", SwingConstants.CENTER);
        emptyLabel.setForeground(UIUtil.getInactiveTextColor());
        emptyLabel.setFont(JBUI.Fonts.label(12));
        emptyLabel.setBorder(JBUI.Borders.empty(20));

        if (schema != null && !schema.isEmpty()) {
            for (ColumnInfo column : schema) {
                addColumnRow(tableModel, column, 0);
            }

            int totalRows = tableModel.getRowCount();

            schemaTable.setPreferredSize(new Dimension(0, (totalRows + 1) * 28 + 10));
            schemaTable.setMaximumSize(new Dimension(Integer.MAX_VALUE, (totalRows + 1) * 28 + 10));

            JPanel tableWrapper = new JPanel(new BorderLayout());
            tableWrapper.setOpaque(false);
            tableWrapper.add(schemaTable.getTableHeader(), BorderLayout.NORTH);
            tableWrapper.add(schemaTable, BorderLayout.CENTER);

            content().add(tableWrapper, BorderLayout.CENTER);
        } else {
            content().add(emptyLabel, BorderLayout.CENTER);
        }
    }

    private static void addColumnRow(DefaultTableModel tableModel, ColumnInfo column, int indentLevel) {
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
                addColumnRow(tableModel, subField, indentLevel + 1);
            }
        }
    }
}
