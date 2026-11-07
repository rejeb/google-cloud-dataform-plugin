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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;

/**
 * The Hide item of the Dataform Tools options menu: hides the tool window and shows the shortcut of
 * the platform Hide Active Tool Window action. It replaces the platform Hide item, removed with the
 * other platform options.
 */
public final class HideDataformToolsAction extends DumbAwareAction {

    private static final String HIDE_ACTIVE_WINDOW = "HideActiveWindow";

    /**
     * The Hide action, showing the platform shortcut when the platform action exists.
     */
    public HideDataformToolsAction() {
        super("Hide");
        AnAction platformHide = ActionManager.getInstance().getAction(HIDE_ACTIVE_WINDOW);
        if (platformHide != null) {
            copyShortcutFrom(platformHide);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getData(PlatformDataKeys.TOOL_WINDOW) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
        if (toolWindow != null) {
            toolWindow.hide();
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
