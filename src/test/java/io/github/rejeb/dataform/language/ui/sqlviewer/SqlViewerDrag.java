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

import io.github.rejeb.dataform.language.ui.CodeBlock;

import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * Drives the bottom edge of a {@link SqlViewer} with the mouse, as the user resizing it.
 */
public final class SqlViewerDrag {

    private SqlViewerDrag() {
    }

    /**
     * Lays the viewer out at its preferred height.
     */
    public static void layOut(SqlViewer viewer) {
        viewer.setSize(400, viewer.getPreferredSize().height);
        SwingTestLayout.layOut(viewer);
    }

    /**
     * Drags the bottom edge of the viewer by the given distance, downwards when positive.
     */
    public static void drag(SqlViewer viewer, int distance) {
        layOut(viewer);
        CodeBlock block = block(viewer);
        int edge = block.getHeight() - 2;
        mouse(block, MouseEvent.MOUSE_PRESSED, edge, 1);
        mouse(block, MouseEvent.MOUSE_DRAGGED, edge + distance, 0);
        mouse(block, MouseEvent.MOUSE_RELEASED, edge + distance, 1);
        layOut(viewer);
    }

    /**
     * Sends a mouse event at the given height of the code block.
     */
    public static void mouse(CodeBlock block, int id, int y, int clicks) {
        boolean pressed = id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_DRAGGED;
        int button = id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_DRAGGED
                ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        block.dispatchEvent(new MouseEvent(block, id, System.currentTimeMillis(),
                pressed ? InputEvent.BUTTON1_DOWN_MASK : 0, 10, y, clicks, false, button));
    }

    /**
     * The code block of the viewer.
     */
    public static CodeBlock block(SqlViewer viewer) {
        return (CodeBlock) SwingUtilities.getAncestorOfClass(CodeBlock.class, viewer.editor().getComponent());
    }
}
