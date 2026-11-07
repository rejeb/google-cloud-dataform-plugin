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
import com.intellij.ui.RoundedLineBorder;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class RoundedBoxTest extends BasePlatformTestCase {

    private static final Color BACKGROUND = new Color(0x10, 0x20, 0x30);
    private static final Color CONTENT = new Color(0xC0, 0x30, 0x30);

    public void testTheBoxHasAThinRoundedBorderInTheSeparatorColor() {
        RoundedBox box = new RoundedBox(() -> BACKGROUND, new JPanel());

        assertFalse(box.isOpaque());
        RoundedLineBorder line = assertInstanceOf(box.getBorder(), RoundedLineBorder.class);
        assertEquals(JBUI.CurrentTheme.Separator.color(), line.getLineColor());
        assertEquals(1, box.getInsets().top);
    }

    public void testTheCornersAreRoundedAndTheInsideTakesTheBackground() {
        JPanel content = new JPanel();
        content.setOpaque(false);

        BufferedImage image = paint(new RoundedBox(() -> BACKGROUND, content));

        assertEquals(0, image.getRGB(0, 0) >>> 24);
        assertEquals(0, image.getRGB(199, 79) >>> 24);
        assertEquals(BACKGROUND.getRGB(), image.getRGB(100, 40));
    }

    public void testAnOpaqueContentDoesNotSquareTheCorners() {
        JPanel content = new JPanel();
        content.setOpaque(true);
        content.setBackground(CONTENT);

        BufferedImage image = paint(new RoundedBox(() -> BACKGROUND, content));

        assertTrue(image.getRGB(1, 1) >>> 24 < 128);
        assertTrue(image.getRGB(198, 78) >>> 24 < 128);
        assertEquals(CONTENT.getRGB(), image.getRGB(100, 40));
    }

    public void testTheBorderTakesTheFocusColorWhileHighlighted() {
        RoundedBox box = new RoundedBox(() -> BACKGROUND, new JPanel());

        box.setHighlighted(true);
        assertEquals(JBUI.CurrentTheme.Focus.focusColor(), ((RoundedLineBorder) box.getBorder()).getLineColor());

        box.setHighlighted(false);
        assertEquals(JBUI.CurrentTheme.Separator.color(), ((RoundedLineBorder) box.getBorder()).getLineColor());
    }

    public void testTheBorderFollowsTheFocusWithinTheBox() {
        JPanel inner = new JPanel();
        JPanel content = new JPanel();
        content.add(inner);
        RoundedBox box = new RoundedBox(() -> BACKGROUND, content);

        box.focusOwnerChanged(inner);
        assertEquals(JBUI.CurrentTheme.Focus.focusColor(), ((RoundedLineBorder) box.getBorder()).getLineColor());

        box.focusOwnerChanged(new JPanel());
        assertEquals(JBUI.CurrentTheme.Separator.color(), ((RoundedLineBorder) box.getBorder()).getLineColor());

        box.focusOwnerChanged(null);
        assertEquals(JBUI.CurrentTheme.Separator.color(), ((RoundedLineBorder) box.getBorder()).getLineColor());
    }

    public void testTheContentFillsTheBox() {
        JPanel content = new JPanel();

        RoundedBox box = new RoundedBox(() -> BACKGROUND, content);

        assertSame(box, content.getParent());
    }

    private static BufferedImage paint(RoundedBox box) {
        box.setSize(200, 80);
        box.doLayout();
        BufferedImage image = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            box.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }
}
