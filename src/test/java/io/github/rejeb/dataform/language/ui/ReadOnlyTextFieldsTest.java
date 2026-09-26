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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.table.JBTable;

import javax.swing.ScrollPaneConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;

public class ReadOnlyTextFieldsTest extends BasePlatformTestCase {

    public void testTheTextIsAReadOnlyViewerWithTheEditorContextMenu() {
        EditorTextField field = ReadOnlyTextFields.singleLine("orders_daily");
        field.addNotify();
        try {
            EditorEx editor = (EditorEx) field.getEditor();
            assertTrue(field.isViewer());
            assertTrue(editor.isViewer());
            assertEquals("orders_daily", editor.getDocument().getText());
            assertEquals(IdeActions.GROUP_BASIC_EDITOR_POPUP, editor.getContextMenuGroupId());
        } finally {
            field.removeNotify();
        }
    }

    public void testAMultiLineTextScrollsWhenItDoesNotFit() {
        EditorTextField field = ReadOnlyTextFields.multiLine("first line\nsecond line");
        field.addNotify();
        try {
            EditorEx editor = (EditorEx) field.getEditor();
            assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    editor.getScrollPane().getVerticalScrollBarPolicy());
        } finally {
            field.removeNotify();
        }
    }

    public void testASingleLineTextNeverScrolls() {
        EditorTextField field = ReadOnlyTextFields.singleLine("orders_daily");
        field.addNotify();
        try {
            EditorEx editor = (EditorEx) field.getEditor();
            assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                    editor.getScrollPane().getVerticalScrollBarPolicy());
        } finally {
            field.removeNotify();
        }
    }

    public void testAMissingTextIsShownEmpty() {
        assertEquals("", ReadOnlyTextFields.multiLine(null).getText());
    }

    public void testTheCellEditorShowsTheCellValue() {
        JBTable table = new JBTable(new DefaultTableModel(new Object[][]{{"customer_id", null}}, new Object[]{"a", "b"}));
        TableCellEditor cellEditor = ReadOnlyTextFields.cellEditor(table);

        EditorTextField first = (EditorTextField) cellEditor.getTableCellEditorComponent(table, "customer_id", false, 0, 0);
        assertEquals("customer_id", first.getText());
        assertEquals("customer_id", cellEditor.getCellEditorValue());

        EditorTextField second = (EditorTextField) cellEditor.getTableCellEditorComponent(table, null, false, 0, 1);
        assertEquals("", second.getText());
    }
}
