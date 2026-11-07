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
package io.github.rejeb.dataform.language.gcp.toolwindow;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPopupMenu;
import java.awt.Component;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Reduces the options menu of the Dataform Tools tool window to the Dataform actions, as in the
 * prototype: the platform always appends its own items (Show Toolbar, View Mode, Move To,
 * Resize…), so they are removed once the menu is filled, just before it shows. Menus holding none
 * of the Dataform actions are left alone.
 */
public final class OptionsMenuTrimmer implements ActionPopupMenuListener {

    private final Set<AnAction> kept = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * A trimmer keeping only the given actions, and the separators between them.
     */
    public OptionsMenuTrimmer(@NotNull Collection<? extends AnAction> kept) {
        this.kept.addAll(kept);
    }

    @Override
    public void actionPopupMenuCreated(@NotNull ActionPopupMenu menu) {
        if (!ActionPlaces.TOOLWINDOW_POPUP.equals(menu.getPlace())) {
            return;
        }
        JPopupMenu popup = menu.getComponent();
        Component[] components = popup.getComponents();
        boolean ours = false;
        for (Component component : components) {
            ours |= isKept(component);
        }
        if (!ours) {
            return;
        }
        popup.removeAll();
        Component separator = null;
        for (Component component : components) {
            if (component instanceof JPopupMenu.Separator) {
                if (separator == null && popup.getComponentCount() > 0) {
                    separator = component;
                }
            } else if (isKept(component)) {
                if (separator != null) {
                    popup.add(separator);
                }
                popup.add(component);
                separator = null;
            }
        }
    }

    private boolean isKept(@NotNull Component component) {
        return component instanceof ActionMenuItem item && kept.contains(item.getAnAction());
    }
}
