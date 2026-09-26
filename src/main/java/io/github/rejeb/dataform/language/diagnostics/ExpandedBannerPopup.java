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
package io.github.rejeb.dataform.language.diagnostics;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Shows a full-size banner drawn over the editor, exactly on top of the collapsed banner it
 * expands, and closes it as soon as the mouse leaves it.
 */
final class ExpandedBannerPopup {

    private ExpandedBannerPopup() {
    }

    /**
     * Opens the expanded banner over the anchor, at the anchor width.
     */
    @NotNull
    static JBPopup show(@NotNull JComponent anchor, @NotNull JComponent expanded) {
        expanded.setPreferredSize(sizeAtWidth(expanded, anchor.getWidth()));
        JBPopup popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(expanded, null)
                .setRequestFocus(false)
                .setFocusable(true)
                .setResizable(false)
                .setMovable(false)
                .setShowBorder(false)
                .setShowShadow(true)
                .setCancelOnClickOutside(true)
                .setCancelOnWindowDeactivation(true)
                .setCancelKeyEnabled(true)
                .createPopup();
        closeWhenMouseLeaves(expanded, popup);
        popup.show(new RelativePoint(anchor, new Point(0, 0)));
        return popup;
    }

    /**
     * Lays the component out at the given width and returns the size its content needs there.
     */
    @NotNull
    static Dimension sizeAtWidth(@NotNull JComponent component, int width) {
        component.setSize(width, Short.MAX_VALUE);
        layOutTree(component);
        return new Dimension(width, component.getPreferredSize().height);
    }

    private static void layOutTree(@NotNull Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layOutTree(nested);
            }
        }
    }

    private static void closeWhenMouseLeaves(@NotNull JComponent expanded, @NotNull JBPopup popup) {
        MouseAdapter listener = new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent event) {
                SwingUtilities.invokeLater(() -> {
                    if (!popup.isDisposed() && expanded.getMousePosition(true) == null) {
                        popup.cancel();
                    }
                });
            }
        };
        addToTree(expanded, listener);
    }

    private static void addToTree(@NotNull Component component, @NotNull MouseAdapter listener) {
        component.addMouseListener(listener);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                addToTree(child, listener);
            }
        }
    }
}
