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
package io.github.rejeb.dataform.language.gcp.toolwindow.action;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * A picker of the Dataform tool window toolbar: a button showing the current choice whose popup
 * lists the {@link #choices()}.
 */
public abstract class PickerAction extends ActionGroup implements CustomComponentAction, DumbAware {

    private final int maxLabelWidth;

    /**
     * @param description   the description of the picker
     * @param maxLabelWidth the widest the shown choice gets in the toolbar, in unscaled pixels
     */
    protected PickerAction(@NotNull String description, int maxLabelWidth) {
        this.maxLabelWidth = maxLabelWidth;
        Presentation template = getTemplatePresentation();
        template.setPopupGroup(true);
        template.setDisableGroupIfEmpty(false);
        template.setDescription(description);
    }

    /**
     * The choices the popup lists, in order.
     */
    public abstract @NotNull DefaultActionGroup choices();

    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public final AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return choices().getChildActionsOrStubs();
    }

    @Override
    public final @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
        return new PickerButton(this, presentation, place, maxLabelWidth);
    }
}
