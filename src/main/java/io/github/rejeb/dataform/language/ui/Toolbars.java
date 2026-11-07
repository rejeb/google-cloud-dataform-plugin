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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * Factory of the small toolbars embedded in the plugin views: transparent, without border, and
 * bound to the component their actions apply to.
 */
public final class Toolbars {

    private Toolbars() {
    }

    /**
     * Creates a horizontal toolbar of the given actions.
     *
     * @param place  the action place, {@code Dataform.<View>}
     * @param target the component the actions read their data context from
     */
    public static @NotNull ActionToolbar horizontal(@NotNull String place, @NotNull JComponent target,
                                                    @NotNull AnAction... actions) {
        return create(place, target, new DefaultActionGroup(actions), true);
    }

    /**
     * Creates a vertical toolbar of the given group, as on the left of a run console tree.
     *
     * @param place  the action place, {@code Dataform.<View>}
     * @param target the component the actions read their data context from
     */
    public static @NotNull ActionToolbar vertical(@NotNull String place, @NotNull JComponent target,
                                                  @NotNull ActionGroup group) {
        return create(place, target, group, false);
    }

    /**
     * Makes toolbars show the text of the action next to its icon, as the view switches of a
     * preview, and returns the action.
     */
    public static <T extends AnAction> @NotNull T withText(@NotNull T action) {
        action.getTemplatePresentation().putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true);
        return action;
    }

    private static @NotNull ActionToolbar create(@NotNull String place, @NotNull JComponent target,
                                                 @NotNull ActionGroup group, boolean horizontal) {
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(place, group, horizontal);
        toolbar.setTargetComponent(target);
        toolbar.setReservePlaceAutoPopupIcon(false);
        JComponent component = toolbar.getComponent();
        component.setOpaque(false);
        component.setBorder(JBUI.Borders.empty());
        return toolbar;
    }
}
