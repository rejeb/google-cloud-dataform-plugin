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

import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.clone.DataformCloneDialogs;
import io.github.rejeb.dataform.language.gcp.service.DataformGcpEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * Reaches the Git plugin through its action and the clone dialog, so that it stays optional.
 */
final class IdeGitLinks implements GitLinks {

    private static final String NEW_BRANCH_ACTION = "Git.CreateNewBranch";
    private static final String PLACE = "Dataform.Changes";
    private static final String CANNOT_CREATE_BRANCH = "The IDE cannot create a Git branch here yet. Check that the "
            + "project folder is listed under Settings | Version Control | Directory Mappings and has a first commit.";

    private final Project project;

    IdeGitLinks(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nullable Runnable newBranch(@NotNull JComponent source) {
        AnAction action = ActionManager.getInstance().getAction(NEW_BRANCH_ACTION);
        return action == null ? null : () -> run(action, source);
    }

    @Override
    public @Nullable Runnable cloneRepository() {
        return DataformCloneDialogs.isAvailable() ? () -> DataformCloneDialogs.open(project) : null;
    }

    private void run(@NotNull AnAction action, @NotNull JComponent source) {
        AnActionEvent event = AnActionEvent.createEvent(action, DataManager.getInstance().getDataContext(source),
                action.getTemplatePresentation().clone(), PLACE, ActionUiKind.NONE, null);
        ActionUtil.updateAction(action, event);
        if (event.getPresentation().isEnabledAndVisible()) {
            ActionUtil.performAction(action, event);
        } else {
            project.getMessageBus().syncPublisher(DataformGcpEvent.TOPIC)
                    .onNotification(CANNOT_CREATE_BRANCH, NotificationType.WARNING);
        }
    }
}
