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
package io.github.rejeb.dataform.language.ui.sqlviewer;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class SqlViewerScrollTest extends BasePlatformTestCase {

    private SqlViewer viewer;
    private JScrollPane page;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        viewer = new SqlViewer(getProject(), SqlViewerOptions.fitText(5));
        viewer.setText("SELECT 1\n".repeat(59) + "SELECT 1");
        JPanel filler = new JPanel();
        filler.setPreferredSize(new Dimension(10, 2000));
        JPanel content = new JPanel(new VerticalLayout(0));
        content.add(viewer);
        content.add(filler);
        page = new JBScrollPane(content);
        page.setSize(400, 300);
        SwingTestLayout.layOut(page);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            viewer.dispose();
        } finally {
            super.tearDown();
        }
    }

    public void testTheWheelScrollsTheBlockBeforeThePage() {
        wheel(1);

        assertTrue(blockBar().getValue() > 0);
        assertEquals(0, page.getVerticalScrollBar().getValue());
    }

    public void testAtTheBottomOfTheBlockTheWheelScrollsThePage() {
        JScrollBar block = blockBar();
        block.setValue(block.getMaximum() - block.getVisibleAmount());

        wheel(1);

        assertTrue(page.getVerticalScrollBar().getValue() > 0);
    }

    public void testAtTheTopOfTheBlockTheWheelScrollsThePageUp() {
        page.getVerticalScrollBar().setValue(200);

        wheel(-1);

        assertTrue(page.getVerticalScrollBar().getValue() < 200);
        assertEquals(0, blockBar().getValue());
    }

    private JScrollBar blockBar() {
        return viewer.editor().getScrollPane().getVerticalScrollBar();
    }

    private void wheel(int rotation) {
        JScrollPane block = viewer.editor().getScrollPane();
        block.dispatchEvent(new MouseWheelEvent(block, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 10, 10,
                0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation));
    }
}
