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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

public class WrappingEditorNotificationPanelTest extends BasePlatformTestCase {

    private static final String LONG_MESSAGE = "Dataform: " + "could not resolve the referenced table ".repeat(40);

    public void testLongTextIsCutAfterTheLineLimitWithAnEllipsis() {
        WrappingEditorNotificationPanel panel = layOut(new WrappingEditorNotificationPanel(fileEditor(), LONG_MESSAGE, 3));
        JTextArea area = UIUtil.findComponentOfType(panel, JTextArea.class);

        assertNotNull(area);
        String[] lines = area.getText().split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[2], lines[2].endsWith(BannerTextClamp.ELLIPSIS));
        assertTrue(panel.isTruncated());
    }

    public void testSingleLineBannerIsOneRowHigh() {
        WrappingEditorNotificationPanel panel = layOut(new WrappingEditorNotificationPanel(fileEditor(), LONG_MESSAGE, 1));
        JTextArea area = UIUtil.findComponentOfType(panel, JTextArea.class);

        assertNotNull(area);
        assertFalse(area.getText(), area.getText().contains("\n"));
        assertTrue(area.getText(), area.getText().endsWith(BannerTextClamp.ELLIPSIS));
        assertEquals(area.getFontMetrics(area.getFont()).getHeight(), area.getPreferredSize().height);
    }

    public void testExpandedBannerShowsTheWholeTextWrappedAtTheBannerWidth() {
        WrappingEditorNotificationPanel panel = layOut(new WrappingEditorNotificationPanel(fileEditor(), LONG_MESSAGE, 1));
        JComponent expanded = panel.createExpandedBanner();

        Dimension size = ExpandedBannerPopup.sizeAtWidth(expanded, panel.getWidth());
        JTextArea area = UIUtil.findComponentOfType(expanded, JTextArea.class);

        assertNotNull(area);
        assertEquals(LONG_MESSAGE, area.getText());
        assertEquals(panel.getWidth(), size.width);
        assertTrue(size.height > panel.getPreferredSize().height);
    }

    public void testShortTextIsNotExpandable() {
        WrappingEditorNotificationPanel panel = layOut(new WrappingEditorNotificationPanel(fileEditor(), "Dataform: boom", 1));
        JTextArea area = UIUtil.findComponentOfType(panel, JTextArea.class);

        assertNotNull(area);
        assertEquals("Dataform: boom", area.getText());
        assertFalse(panel.isTruncated());
    }

    private FileEditor fileEditor() {
        myFixture.configureByText("a.sqlx", "select 1\n");
        return TextEditorProvider.getInstance().getTextEditor(myFixture.getEditor());
    }

    private static WrappingEditorNotificationPanel layOut(WrappingEditorNotificationPanel panel) {
        panel.setSize(400, 600);
        for (int pass = 0; pass < 3; pass++) {
            layOutTree(panel);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        }
        return panel;
    }

    private static void layOutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOutTree(nested);
            }
        }
    }
}
