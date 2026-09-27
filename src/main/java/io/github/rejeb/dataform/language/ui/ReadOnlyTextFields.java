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
package io.github.rejeb.dataform.language.ui;

import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;

/**
 * Factory of read-only Swing text components: the text shows a text cursor, can be partially
 * selected with the mouse and offers a context menu (Copy, Select All). Plain text components hold
 * no editor, so building many of them and updating their text on every refresh stays cheap.
 */
public final class ReadOnlyTextFields {

    private ReadOnlyTextFields() {
    }

    /**
     * Creates a single-line read-only text on the panel background.
     */
    public static @NotNull JBTextField singleLine(@Nullable String text) {
        return singleLine(text, UIUtil.getPanelBackground());
    }

    /**
     * Creates a single-line read-only text on the given background.
     */
    public static @NotNull JBTextField singleLine(@Nullable String text, @NotNull Color background) {
        JBTextField field = new JBTextField(text != null ? text : "");
        return configure(field, background);
    }

    /**
     * Creates a word-wrapped multi-line read-only text on the panel background. Put it in a scroll
     * pane when it may be given less height than its text needs.
     */
    public static @NotNull JBTextArea multiLine(@Nullable String text) {
        JBTextArea area = new JBTextArea(text != null ? text : "");
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return configure(area, UIUtil.getPanelBackground());
    }

    /**
     * Creates a table cell editor that shows the cell value as read-only text, so that a click on a cell
     * lets the user select part of its text.
     */
    public static @NotNull TableCellEditor cellEditor(@NotNull JTable table) {
        JBTextField field = singleLine("", table.getBackground());
        return new AbstractTableCellEditor() {
            @Override
            public Object getCellEditorValue() {
                return field.getText();
            }

            @Override
            public Component getTableCellEditorComponent(JTable t, Object value, boolean selected, int row, int column) {
                field.setText(value != null ? value.toString() : "");
                return field;
            }
        };
    }

    private static <T extends JTextComponent> @NotNull T configure(@NotNull T component, @NotNull Color background) {
        component.setEditable(false);
        component.setBorder(JBUI.Borders.empty());
        component.setBackground(background);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        component.getCaret().setVisible(false);
        component.setComponentPopupMenu(copyMenu(component));
        return component;
    }

    private static @NotNull JPopupMenu copyMenu(@NotNull JTextComponent component) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(e -> {
            if (component.getSelectedText() == null) {
                component.selectAll();
            }
            component.copy();
        });
        JMenuItem selectAll = new JMenuItem("Select All");
        selectAll.addActionListener(e -> {
            component.requestFocusInWindow();
            component.selectAll();
        });
        menu.add(copy);
        menu.add(selectAll);
        return menu;
    }
}
