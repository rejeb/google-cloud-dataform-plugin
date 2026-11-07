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
import com.intellij.ui.AnimatedIcon;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class RotatingIconsTest extends BasePlatformTestCase {

    private static final Icon MARK = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(Color.RED);
            g.fillRect(x + 11, y + 1, 2, 2);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    };

    public void testEveryFrameKeepsTheSizeOfTheIcon() {
        List<Icon> frames = RotatingIcons.frames(MARK, 12);

        assertEquals(12, frames.size());
        frames.forEach(frame -> {
            assertEquals(16, frame.getIconWidth());
            assertEquals(16, frame.getIconHeight());
        });
    }

    public void testTheFirstFrameIsTheIconItself() {
        BufferedImage first = paint(RotatingIcons.frames(MARK, 12).get(0));

        assertTrue(isPainted(first, 11, 1));
        assertFalse(isPainted(first, 13, 11));
    }

    public void testAQuarterOfTheFramesTurnsTheIconAQuarterClockwise() {
        BufferedImage quarter = paint(RotatingIcons.frames(MARK, 12).get(3));

        assertFalse(isPainted(quarter, 11, 1));
        assertTrue(isPainted(quarter, 13, 11));
    }

    public void testTheSpinnerAnimatesTheFrames() {
        assertInstanceOf(RotatingIcons.spinner(MARK), AnimatedIcon.class);
    }

    private static BufferedImage paint(Icon icon) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static boolean isPainted(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) > 0x80;
    }
}
