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

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class ReadOnlyEditorsTest extends BasePlatformTestCase {

    private static final String LONG_SQL = "SELECT 1\n".repeat(60);

    private EditorEx editor;

    @Override
    protected void tearDown() throws Exception {
        try {
            ReadOnlyEditors.release(editor);
        } finally {
            super.tearDown();
        }
    }

    public void testACompactSqlEditorDoesNotScrollWithTheWheel() {
        editor = ReadOnlyEditors.compactSql(getProject(), LONG_SQL, true);
        layOut(editor.getComponent());

        wheel(editor.getScrollPane());

        assertEquals(0, editor.getScrollPane().getVerticalScrollBar().getValue());
    }

    public void testAHiddenVerticalScrollbarTakesNoRoom() {
        editor = ReadOnlyEditors.compactSql(getProject(), LONG_SQL, true);

        ReadOnlyEditors.hideVerticalScrollbar(editor);
        layOut(editor.getComponent());

        JScrollPane scrollPane = editor.getScrollPane();
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        assertTrue(bar.isVisible());
        assertEquals(0, bar.getWidth());
        assertEquals(scrollPane.getWidth() - scrollPane.getInsets().right,
                scrollPane.getViewport().getBounds().getMaxX(), 0);
    }

    public void testTheWheelScrollsAnEditorWhoseVerticalScrollbarIsHidden() {
        editor = ReadOnlyEditors.compactSql(getProject(), LONG_SQL, true);
        ReadOnlyEditors.hideVerticalScrollbar(editor);
        layOut(editor.getComponent());

        wheel(editor.getScrollPane());

        assertTrue(editor.getScrollPane().getVerticalScrollBar().getValue() > 0);
    }

    private static void layOut(JComponent component) {
        component.setSize(400, 80);
        layOutTree(component);
    }

    private static void layOutTree(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOutTree(child);
            }
        }
    }

    private static void wheel(JScrollPane scrollPane) {
        scrollPane.dispatchEvent(new MouseWheelEvent(scrollPane, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                0, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1));
    }
}
