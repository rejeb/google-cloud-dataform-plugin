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

import java.util.function.Supplier;

public class PullFromWorkspaceAction extends AnAction {

    private final Supplier<@Nullable String> workspaceIdSupplier;
    private final GcpPanelActionDispatcher dispatcher;

    public PullFromWorkspaceAction(
            @NotNull Supplier<@Nullable String> workspaceIdSupplier,
            @NotNull GcpPanelActionDispatcher dispatcher
    ) {
        super(() -> "Fetch Files from Workspace to Local", IconUtil.colorize(AllIcons.Actions.CheckOut, JBColor.GREEN));
        this.workspaceIdSupplier = workspaceIdSupplier;
        this.dispatcher = dispatcher;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String workspaceId = workspaceIdSupplier.get();
        if (workspaceId == null) {
            Messages.showWarningDialog("Please select a workspace before pulling.", "No Workspace Selected");
            return;
        }
        dispatcher.pull(workspaceId);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        String workspaceId = workspaceIdSupplier.get();
        boolean hasWorkspace = workspaceId != null;
        e.getPresentation().setEnabled(hasWorkspace);
        e.getPresentation().setText(hasWorkspace
                ? "Pull workspace '" + workspaceId + "' files to local"
                : "Select a Workspace to Enable Pull");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}

