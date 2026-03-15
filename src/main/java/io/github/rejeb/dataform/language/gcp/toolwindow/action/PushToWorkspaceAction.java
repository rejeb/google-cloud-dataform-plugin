/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import io.github.rejeb.dataform.language.gcp.toolwindow.DataformGcpPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PushToWorkspaceAction extends AnAction {

    private final Supplier<@Nullable String> workspaceIdSupplier;
    private final DataformGcpPanel.PanelCallback callback;

    public PushToWorkspaceAction(
            @NotNull Supplier<@Nullable String> workspaceIdSupplier,
            @NotNull DataformGcpPanel.PanelCallback callback
    ) {
        super("Push Files", "Push local files to the selected workspace", AllIcons.Actions.Upload);
        this.workspaceIdSupplier = workspaceIdSupplier;
        this.callback = callback;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String workspaceId = workspaceIdSupplier.get();
        if (workspaceId == null) {
            Messages.showWarningDialog(
                    "Please select a workspace before pushing.",
                    "No Workspace Selected"
            );
            return;
        }
        callback.onPush(workspaceId);
    }
}
