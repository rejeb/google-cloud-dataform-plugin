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
import com.intellij.openapi.project.DumbAwareAction;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Copies files between the project and the selected workspace; disabled while the repository's
 * main branch is shown.
 */
public final class WorkspaceTransferAction extends DumbAwareAction {

    private final Supplier<@Nullable String> workspaceIdSupplier;
    private final Consumer<String> transfer;

    private WorkspaceTransferAction(@NotNull Supplier<@Nullable String> workspaceIdSupplier, @NotNull String text,
                                    @NotNull String description, @NotNull Icon icon,
                                    @NotNull Consumer<String> transfer) {
        super(text, description, icon);
        this.workspaceIdSupplier = workspaceIdSupplier;
        this.transfer = transfer;
    }

    /**
     * The action copying the files of the selected workspace into the project.
     */
    public static @NotNull WorkspaceTransferAction pull(@NotNull Supplier<@Nullable String> workspaceIdSupplier,
                                                        @NotNull GcpPanelActionDispatcher dispatcher) {
        return new WorkspaceTransferAction(workspaceIdSupplier, "Pull", "Copy the workspace files into the project",
                DataformIcons.PULL, dispatcher::pull);
    }

    /**
     * The action copying the project files into the selected workspace.
     */
    public static @NotNull WorkspaceTransferAction push(@NotNull Supplier<@Nullable String> workspaceIdSupplier,
                                                        @NotNull GcpPanelActionDispatcher dispatcher) {
        return new WorkspaceTransferAction(workspaceIdSupplier, "Push", "Copy the project files into the workspace",
                DataformIcons.PUSH, dispatcher::push);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String workspaceId = workspaceIdSupplier.get();
        if (workspaceId != null) {
            transfer.accept(workspaceId);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(workspaceIdSupplier.get() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
