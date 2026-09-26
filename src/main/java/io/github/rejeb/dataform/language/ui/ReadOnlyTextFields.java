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

import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import java.awt.Color;
import java.awt.Component;

/**
 * Factory of read-only text components backed by the IntelliJ editor in viewer mode: the text shows a
 * text cursor, can be partially selected with the mouse and offers the editor context menu (Copy, Select All).
 */
public final class ReadOnlyTextFields {

    private ReadOnlyTextFields() {
    }

    /**
     * Creates a single-line read-only text on the panel background.
     */
    public static @NotNull EditorTextField singleLine(@Nullable String text) {
        return create(text, true, UIUtil.getPanelBackground());
    }

    /**
     * Creates a single-line read-only text on the given background.
     */
    public static @NotNull EditorTextField singleLine(@Nullable String text, @NotNull Color background) {
        return create(text, true, background);
    }

    /**
     * Creates a soft-wrapped multi-line read-only text on the panel background.
     */
    public static @NotNull EditorTextField multiLine(@Nullable String text) {
        return create(text, false, UIUtil.getPanelBackground());
    }

    /**
     * Creates a table cell editor that shows the cell value as read-only text, so that a click on a cell
     * lets the user select part of its text.
     */
    public static @NotNull TableCellEditor cellEditor(@NotNull JTable table) {
        EditorTextField field = create("", true, table.getBackground());
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

    private static @NotNull EditorTextField create(@Nullable String text, boolean oneLine, @NotNull Color background) {
        EditorTextField field = new EditorTextField(text != null ? text : "");
        field.setOneLineMode(oneLine);
        field.setViewer(true);
        field.setBorder(JBUI.Borders.empty());
        field.setBackground(background);
        field.addSettingsProvider(editor -> {
            editor.setBorder(JBUI.Borders.empty());
            editor.setBackgroundColor(background);
            editor.getSettings().setCaretRowShown(false);
            editor.getSettings().setUseSoftWraps(!oneLine);
        });
        return field;
    }
}
