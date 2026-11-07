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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareToggleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.function.BooleanSupplier;

/**
 * One choice of a Dataform tool window picker: checked while it is the current choice, with an
 * optional grayed hint after its name.
 */
public final class PickerChoice extends DumbAwareToggleAction {

    private final @Nullable String hint;
    private final BooleanSupplier chosen;
    private final Runnable choose;

    /**
     * A choice named {@code text}; {@code chosen} tells whether it is the current one and
     * {@code choose} makes it current.
     */
    public PickerChoice(@NotNull String text, @Nullable Icon icon, @Nullable String hint,
                        @NotNull BooleanSupplier chosen, @NotNull Runnable choose) {
        super(text, null, icon);
        getTemplatePresentation().setText(text, false);
        this.hint = hint;
        this.chosen = chosen;
        this.choose = choose;
        if (hint != null) {
            getTemplatePresentation().putClientProperty(ActionUtil.SECONDARY_TEXT, hint);
        }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return chosen.getAsBoolean();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        choose.run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (hint != null) {
            e.getPresentation().putClientProperty(ActionUtil.SECONDARY_TEXT, hint);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
