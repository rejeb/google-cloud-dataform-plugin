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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.util.Arrays;
import java.util.List;

public class ReadOnlyTextFieldsTest extends BasePlatformTestCase {

    private static List<String> menuItems(JTextComponent component) {
        JPopupMenu menu = component.getComponentPopupMenu();
        assertNotNull(menu);
        return Arrays.stream(menu.getComponents())
                .filter(JMenuItem.class::isInstance)
                .map(item -> ((JMenuItem) item).getText())
                .toList();
    }

    public void testTheTextIsReadOnlySelectableAndCopyable() {
        JBTextField field = ReadOnlyTextFields.singleLine("orders_daily");

        assertFalse(field.isEditable());
        assertEquals("orders_daily", field.getText());
        assertEquals(Cursor.TEXT_CURSOR, field.getCursor().getType());
        assertEquals(List.of("Copy", "Select All"), menuItems(field));

        field.select(0, 6);
        assertEquals("orders", field.getSelectedText());
    }

    public void testAMultiLineTextWrapsItsWords() {
        JBTextArea area = ReadOnlyTextFields.multiLine("first line\nsecond line");

        assertFalse(area.isEditable());
        assertTrue(area.getLineWrap());
        assertTrue(area.getWrapStyleWord());
        assertEquals(List.of("Copy", "Select All"), menuItems(area));
    }

    public void testUpdatingTheTextNeedsNoWriteAction() {
        JBTextField field = ReadOnlyTextFields.singleLine("—");

        field.setText("RUNNING");

        assertEquals("RUNNING", field.getText());
    }

    public void testAMissingTextIsShownEmpty() {
        assertEquals("", ReadOnlyTextFields.multiLine(null).getText());
    }

    public void testTheCellEditorShowsTheCellValue() {
        JBTable table = new JBTable(new DefaultTableModel(new Object[][]{{"customer_id", null}}, new Object[]{"a", "b"}));
        TableCellEditor cellEditor = ReadOnlyTextFields.cellEditor(table);

        JTextComponent first = (JTextComponent) cellEditor.getTableCellEditorComponent(table, "customer_id", false, 0, 0);
        assertEquals("customer_id", first.getText());
        assertEquals("customer_id", cellEditor.getCellEditorValue());
        assertFalse(first.isEditable());

        JTextComponent second = (JTextComponent) cellEditor.getTableCellEditorComponent(table, null, false, 0, 1);
        assertEquals("", second.getText());
    }

    public void testTheCellEditorKeepsTheLookOfTheCell() {
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 11);
        Color color = new Color(0xCF8E6D);
        JBTable table = new JBTable(new DefaultTableModel(new Object[][]{{"STRING"}}, new Object[]{"Type"}));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focused, row, column);
                setFont(font);
                setForeground(color);
                setBorder(JBUI.Borders.empty(0, 10));
                return this;
            }
        });
        TableCellEditor cellEditor = ReadOnlyTextFields.cellEditor(table);

        JTextComponent field = (JTextComponent) cellEditor.getTableCellEditorComponent(table, "STRING", true, 0, 0);

        assertEquals(font, field.getFont());
        assertEquals(color, field.getForeground());
        assertEquals(JBUI.scale(10), field.getInsets().left);
    }
}
