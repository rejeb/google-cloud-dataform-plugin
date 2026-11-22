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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Toolbar actions made of lambdas, for the buttons of the plugin's panels.
 */
public final class Actions {

    private Actions() {
    }

    /**
     * An action that always runs {@code perform}.
     */
    public static @NotNull DumbAwareAction of(@NotNull String text, @NotNull String description, @Nullable Icon icon,
                                              @NotNull Runnable perform) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                perform.run();
            }
        };
    }

    /**
     * An action enabled while {@code enabled} holds, checked on the event dispatch thread.
     */
    public static @NotNull DumbAwareAction enabledWhen(@NotNull String text, @NotNull String description,
                                                       @Nullable Icon icon, @NotNull BooleanSupplier enabled,
                                                       @NotNull Runnable perform) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                perform.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(enabled.getAsBoolean());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    /**
     * An action shown and enabled while {@code shown} holds, checked on the event dispatch thread.
     */
    public static @NotNull DumbAwareAction shownWhen(@NotNull String text, @NotNull String description,
                                                     @Nullable Icon icon, @NotNull BooleanSupplier shown,
                                                     @NotNull Runnable perform) {
        return new DumbAwareAction(text, description, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                perform.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabledAndVisible(shown.getAsBoolean());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    /**
     * A toggle selected while {@code state} holds; choosing the other state runs {@code toggle}. Its
     * icon is read again on each update.
     */
    public static @NotNull ToggleAction toggle(@NotNull String text, @NotNull String description,
                                               @NotNull Supplier<Icon> icon, @NotNull BooleanSupplier state,
                                               @NotNull Runnable toggle) {
        return new ToggleAction(text, description, icon.get()) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return state.getAsBoolean();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean requested) {
                if (requested != state.getAsBoolean()) toggle.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setIcon(icon.get());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }
}
