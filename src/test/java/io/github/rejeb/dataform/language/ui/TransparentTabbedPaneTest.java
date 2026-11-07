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
import com.intellij.util.ui.JBUI;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class TransparentTabbedPaneTest extends BasePlatformTestCase {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 140;

    public void testTheContentAreaIsNotFilled() {
        TransparentTabbedPane tabs = new TransparentTabbedPane();
        JPanel content = content();
        tabs.addTab("Query", content);

        BufferedImage image = paint(tabs);

        assertFalse(tabs.isOpaque());
        assertEquals(0, image.getRGB(WIDTH / 2, HEIGHT - 4) >>> 24);
        assertEquals(0, image.getRGB(2, content.getY() + 2) >>> 24);
    }

    public void testTheContentAreaStaysUnfilledAfterALookAndFeelChange() {
        TransparentTabbedPane tabs = new TransparentTabbedPane();
        tabs.addTab("Query", content());

        tabs.updateUI();
        BufferedImage image = paint(tabs);

        assertEquals(0, image.getRGB(WIDTH / 2, HEIGHT - 4) >>> 24);
    }

    public void testTheLookAndFeelDefaultIsLeftUnchanged() {
        boolean before = UIManager.getBoolean("TabbedPane.contentOpaque");

        new TransparentTabbedPane().updateUI();

        assertEquals(before, UIManager.getBoolean("TabbedPane.contentOpaque"));
    }

    public void testASeparatorLineRunsUnderTheTabsAcrossTheWholeWidth() {
        TransparentTabbedPane tabs = new TransparentTabbedPane();
        JPanel content = content();
        tabs.addTab("Query", content);

        BufferedImage image = paint(tabs);

        assertEquals(JBUI.CurrentTheme.Separator.color().getRGB(), image.getRGB(WIDTH - 2, content.getY() - 1));
    }

    public void testTheTabContentKeepsItsOwnBorderWithoutExtraInsets() {
        TransparentTabbedPane tabs = new TransparentTabbedPane();
        JPanel content = content();
        content.setBorder(JBUI.Borders.emptyTop(8));

        tabs.addTab("Query", content);

        assertEquals(JBUI.insetsTop(8), content.getInsets());
    }

    private static JPanel content() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setPreferredSize(new Dimension(WIDTH, 80));
        return content;
    }

    private static BufferedImage paint(JComponent component) {
        component.setSize(WIDTH, HEIGHT);
        layout(component);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            component.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }
}
