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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseWheelEvent;

/**
 * What a turn of the mouse wheel does on the lineage canvas. Held with the modifier that zooms the
 * editor font (Ctrl, or Cmd on macOS, alone) it zooms towards the pointer; otherwise it scrolls the
 * graph, down and up, or sideways while Shift is held.
 */
final class CanvasWheel {

    private static final double ZOOM_STEP = 1.1;
    private static final int SCROLL_UNIT = 16;

    private CanvasWheel() {
    }

    /**
     * Zooms or scrolls {@code viewport} as the wheel event asks, by the fractional rotation of a
     * touchpad as well as by whole notches.
     */
    static void apply(@NotNull MouseWheelEvent e, @NotNull CanvasViewport viewport) {
        double rotation = e.getPreciseWheelRotation();
        if (rotation == 0) return;
        if (isZoom(e)) {
            viewport.zoomAt(e.getPoint(), Math.pow(ZOOM_STEP, -rotation));
            return;
        }
        int distance = (int) Math.round(rotation * e.getScrollAmount() * JBUI.scale(SCROLL_UNIT));
        if (e.isShiftDown()) {
            viewport.panBy(-distance, 0);
        } else {
            viewport.panBy(0, -distance);
        }
    }

    private static boolean isZoom(@NotNull MouseWheelEvent e) {
        if (e.isAltDown() || e.isShiftDown()) return false;
        return SystemInfo.isMac ? e.isMetaDown() && !e.isControlDown() : e.isControlDown() && !e.isMetaDown();
    }
}
