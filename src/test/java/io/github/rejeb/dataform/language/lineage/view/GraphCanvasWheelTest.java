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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class GraphCanvasWheelTest extends BasePlatformTestCase {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final int ZOOM_MODIFIER = SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    public void testWheelDownScrollsTheGraphDownWithoutZooming() {
        GraphCanvas canvas = canvas();
        GraphCanvas.ViewportState before = canvas.viewportState();
        turnWheel(canvas, 0, 1);
        GraphCanvas.ViewportState after = canvas.viewportState();
        assertEquals(before.zoom(), after.zoom());
        assertEquals(before.offsetX(), after.offsetX());
        assertTrue(after.offsetY() < before.offsetY());
    }

    public void testWheelUpScrollsTheGraphUpWithoutZooming() {
        GraphCanvas canvas = canvas();
        GraphCanvas.ViewportState before = canvas.viewportState();
        turnWheel(canvas, 0, -1);
        GraphCanvas.ViewportState after = canvas.viewportState();
        assertEquals(before.zoom(), after.zoom());
        assertEquals(before.offsetX(), after.offsetX());
        assertTrue(after.offsetY() > before.offsetY());
    }

    public void testShiftWheelScrollsTheGraphSideways() {
        GraphCanvas canvas = canvas();
        GraphCanvas.ViewportState before = canvas.viewportState();
        turnWheel(canvas, InputEvent.SHIFT_DOWN_MASK, 1);
        GraphCanvas.ViewportState after = canvas.viewportState();
        assertEquals(before.zoom(), after.zoom());
        assertEquals(before.offsetY(), after.offsetY());
        assertTrue(after.offsetX() < before.offsetX());
    }

    public void testZoomModifierWithWheelUpZoomsIn() {
        GraphCanvas canvas = canvas();
        double before = canvas.viewportState().zoom();
        turnWheel(canvas, ZOOM_MODIFIER, -1);
        assertTrue(canvas.viewportState().zoom() > before);
    }

    public void testZoomModifierWithWheelDownZoomsOut() {
        GraphCanvas canvas = canvas();
        double before = canvas.viewportState().zoom();
        turnWheel(canvas, ZOOM_MODIFIER, 1);
        assertTrue(canvas.viewportState().zoom() < before);
    }

    public void testATouchpadStepScrollsLessThanAWheelNotch() {
        GraphCanvas notch = canvas();
        double notchStart = notch.viewportState().offsetY();
        turnWheel(notch, 0, 1, 1.0);
        GraphCanvas touchpad = canvas();
        double touchpadStart = touchpad.viewportState().offsetY();
        turnWheel(touchpad, 0, 0, 0.25);
        double notchDistance = notchStart - notch.viewportState().offsetY();
        double touchpadDistance = touchpadStart - touchpad.viewportState().offsetY();
        assertTrue(touchpadDistance > 0);
        assertTrue(touchpadDistance < notchDistance);
    }

    private GraphCanvas canvas() {
        GraphCanvas canvas = new GraphCanvas(getProject(), LineageViewFixtures.model());
        canvas.setBounds(0, 0, WIDTH, HEIGHT);
        return canvas;
    }

    private static void turnWheel(GraphCanvas canvas, int modifiers, int rotation) {
        turnWheel(canvas, modifiers, rotation, rotation);
    }

    private static void turnWheel(GraphCanvas canvas, int modifiers, int rotation, double precise) {
        canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, modifiers,
                WIDTH / 2, HEIGHT / 2, WIDTH / 2, HEIGHT / 2, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation, precise));
    }
}
