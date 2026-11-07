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

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Resizes a component by dragging a strip along its bottom edge, which shows the resize cursor;
 * double-clicking the strip gives the component its automatic height back.
 */
final class BottomEdgeResizer extends MouseAdapter {

    private static final int EDGE = 6;

    /**
     * The height the strip resizes.
     */
    interface Target {

        /**
         * The current height.
         */
        int height();

        /**
         * Gives the height the user dragged to; the target keeps it within its limits.
         */
        void resizeTo(int height);

        /**
         * Gives the target its automatic height back.
         */
        void reset();
    }

    private final JComponent owner;
    private final Target target;
    private int pressY = -1;
    private int pressHeight;

    private BottomEdgeResizer(@NotNull JComponent owner, @NotNull Target target) {
        this.owner = owner;
        this.target = target;
    }

    /**
     * Lets the user resize the target by dragging the bottom edge of the owner.
     */
    static void install(@NotNull JComponent owner, @NotNull Target target) {
        BottomEdgeResizer resizer = new BottomEdgeResizer(owner, target);
        owner.addMouseListener(resizer);
        owner.addMouseMotionListener(resizer);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        owner.setCursor(onEdge(e) ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : null);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (pressY < 0) owner.setCursor(null);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && onEdge(e)) {
            pressY = e.getY();
            pressHeight = target.height();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (pressY >= 0) target.resizeTo(pressHeight + e.getY() - pressY);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        pressY = -1;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && onEdge(e)) target.reset();
    }

    private boolean onEdge(@NotNull MouseEvent e) {
        return e.getY() >= owner.getHeight() - JBUI.scale(EDGE);
    }
}
