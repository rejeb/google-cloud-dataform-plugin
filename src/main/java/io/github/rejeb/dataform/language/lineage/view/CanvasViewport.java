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

import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.function.IntConsumer;

/**
 * Pan and zoom state of the lineage canvas: converts screen to world coordinates, applies the
 * transform to a {@link Graphics2D}, zooms towards the cursor and fits a bounding box to the
 * visible area.
 */
final class CanvasViewport {

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 2.5;
    private static final int FIT_PAD = 60;
    private static final double FIT_MAX_ZOOM = 1.4;

    private final Runnable onChanged;

    private double zoom = 1.0;
    private double offsetX;
    private double offsetY;
    private IntConsumer zoomListener;

    CanvasViewport(@NotNull Runnable onChanged) {
        this.onChanged = onChanged;
    }

    void setZoomListener(@NotNull IntConsumer listener) {
        this.zoomListener = listener;
        notifyZoom();
    }

    double zoom() {
        return zoom;
    }

    double offsetY() {
        return offsetY;
    }

    double worldX(double screenX) {
        return (screenX - offsetX) / zoom;
    }

    double worldY(double screenY) {
        return (screenY - offsetY) / zoom;
    }

    void applyTo(@NotNull Graphics2D g2) {
        g2.translate(offsetX, offsetY);
        g2.scale(zoom, zoom);
    }

    void panBy(int dx, int dy) {
        offsetX += dx;
        offsetY += dy;
        onChanged.run();
    }

    /** Zooms by {@code factor} keeping the world point under the cursor in place. */
    void zoomAt(@NotNull Point cursor, double factor) {
        double newZoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == zoom) return;
        double wx = worldX(cursor.x);
        double wy = worldY(cursor.y);
        zoom = newZoom;
        offsetX = cursor.x - wx * zoom;
        offsetY = cursor.y - wy * zoom;
        notifyZoom();
        onChanged.run();
    }

    /** Centres {@code bounds} in a viewport of the given size, scaling it down to fit. */
    void fit(@NotNull Rectangle2D.Double bounds, int viewWidth, int viewHeight) {
        if (bounds.width <= 0 || bounds.height <= 0 || viewWidth <= 0 || viewHeight <= 0) return;
        double availW = viewWidth - 2.0 * FIT_PAD;
        double availH = viewHeight - 2.0 * FIT_PAD;
        double scale = Math.min(availW / bounds.width, availH / bounds.height);
        zoom = clamp(scale, MIN_ZOOM, FIT_MAX_ZOOM);
        offsetX = (viewWidth - bounds.width * zoom) / 2.0 - bounds.x * zoom;
        offsetY = (viewHeight - bounds.height * zoom) / 2.0 - bounds.y * zoom;
        notifyZoom();
        onChanged.run();
    }

    private void notifyZoom() {
        if (zoomListener != null) zoomListener.accept((int) Math.round(zoom * 100));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
