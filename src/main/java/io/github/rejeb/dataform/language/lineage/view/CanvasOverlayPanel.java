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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

/**
 * The graph area of a lineage view: the canvas filling it, with a banner floating over its top and
 * zoom controls in its bottom-left corner, as in the UI prototype.
 */
final class CanvasOverlayPanel extends JPanel {

    private static final int BANNER_SIDE = 16;
    private static final int BANNER_TOP = 12;
    private static final int CONTROLS_MARGIN = 14;

    private final GraphCanvas canvas;
    private final @Nullable JComponent banner;
    private final @Nullable JComponent zoomControls;

    /**
     * Lays the given banner and zoom controls over the canvas; either may be {@code null}.
     */
    CanvasOverlayPanel(@NotNull GraphCanvas canvas, @Nullable JComponent banner, @Nullable JComponent zoomControls) {
        this.canvas = canvas;
        this.banner = banner;
        this.zoomControls = zoomControls;
        setLayout(new OverlayLayout());
        setOpaque(false);
        if (banner != null) add(banner);
        if (zoomControls != null) add(zoomControls);
        add(canvas);
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    private final class OverlayLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public void layoutContainer(Container parent) {
            int width = parent.getWidth();
            int height = parent.getHeight();
            canvas.setBounds(0, 0, width, height);
            if (banner != null) {
                int side = JBUI.scale(BANNER_SIDE);
                int bannerWidth = Math.max(0, width - 2 * side);
                banner.setSize(bannerWidth, Short.MAX_VALUE);
                int bannerHeight = banner.getPreferredSize().height;
                banner.setBounds(side, JBUI.scale(BANNER_TOP), bannerWidth, bannerHeight);
            }
            if (zoomControls != null) {
                Dimension size = zoomControls.getPreferredSize();
                int margin = JBUI.scale(CONTROLS_MARGIN);
                zoomControls.setBounds(margin, height - margin - size.height, size.width, size.height);
            }
        }
    }
}
