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

import com.intellij.ui.AnimatedIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a still icon into the frames of a spinner: the same icon, rotated clockwise around its
 * centre by an equal step from one frame to the next.
 */
public final class RotatingIcons {

    private static final int SPINNER_FRAMES = 12;
    private static final int SPINNER_FRAME_DELAY_MS = 75;

    private RotatingIcons() {
    }

    /**
     * Animates the given icon as a spinner that makes a full turn in 0.9 s.
     */
    public static @NotNull AnimatedIcon spinner(@NotNull Icon icon) {
        return new AnimatedIcon(SPINNER_FRAME_DELAY_MS, frames(icon, SPINNER_FRAMES).toArray(Icon[]::new));
    }

    /**
     * Returns {@code count} frames of the given icon, the first one unrotated and each next one turned
     * a further {@code 360 / count} degrees clockwise.
     */
    public static @NotNull List<Icon> frames(@NotNull Icon icon, int count) {
        List<Icon> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            frames.add(new RotatedIcon(icon, 2 * Math.PI * i / count));
        }
        return frames;
    }

    private record RotatedIcon(@NotNull Icon icon, double radians) implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.rotate(radians, x + icon.getIconWidth() / 2.0, y + icon.getIconHeight() / 2.0);
                icon.paintIcon(c, g2, x, y);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return icon.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return icon.getIconHeight();
        }
    }
}
