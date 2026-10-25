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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import io.github.rejeb.dataform.language.gcp.toolwindow.dispatcher.GcpPanelActionDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class WorkspaceTransferAction extends AnAction {

    private final Supplier<@Nullable String> workspaceIdSupplier;
    private final String verb;
    private final Function<String, String> enabledText;
    private final String disabledText;
    private final Consumer<String> transfer;

    private WorkspaceTransferAction(@NotNull Supplier<@Nullable String> workspaceIdSupplier, @NotNull String text,
                                    @NotNull Icon icon, @NotNull String verb,
                                    @NotNull Function<String, String> enabledText, @NotNull String disabledText,
                                    @NotNull Consumer<String> transfer) {
        super(() -> text, icon);
        this.workspaceIdSupplier = workspaceIdSupplier;
        this.verb = verb;
        this.enabledText = enabledText;
        this.disabledText = disabledText;
        this.transfer = transfer;
    }

    /**
     * The action fetching the files of the selected workspace into the local project.
     */
    public static @NotNull WorkspaceTransferAction pull(@NotNull Supplier<@Nullable String> workspaceIdSupplier,
                                                        @NotNull GcpPanelActionDispatcher dispatcher) {
        return new WorkspaceTransferAction(workspaceIdSupplier, "Fetch Files from Workspace to Local",
                IconUtil.colorize(AllIcons.Actions.CheckOut, JBColor.GREEN), "pulling",
                id -> "Pull workspace '" + id + "' files to local", "Select a Workspace to Enable Pull",
                dispatcher::pull);
    }

    /**
     * The action sending the local files to the selected workspace.
     */
    public static @NotNull WorkspaceTransferAction push(@NotNull Supplier<@Nullable String> workspaceIdSupplier,
                                                        @NotNull GcpPanelActionDispatcher dispatcher) {
        return new WorkspaceTransferAction(workspaceIdSupplier, "Send Local Files to Workspace",
                IconUtil.colorize(AllIcons.Vcs.Push, JBColor.BLUE), "pushing",
                id -> "Push local files to workspace '" + id + "'", "Select a workspace to enable push",
                dispatcher::push);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String workspaceId = workspaceIdSupplier.get();
        if (workspaceId == null) {
            Messages.showWarningDialog("Please select a workspace before " + verb + ".", "No Workspace Selected");
            return;
        }
        transfer.accept(workspaceId);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        String workspaceId = workspaceIdSupplier.get();
        e.getPresentation().setEnabled(workspaceId != null);
        e.getPresentation().setText(workspaceId != null ? enabledText.apply(workspaceId) : disabledText);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
