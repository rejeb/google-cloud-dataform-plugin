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
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.auth.AuthStatus;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.toolwindow.WorkspaceSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.List;

/**
 * The workspace picker of the Dataform tool window toolbar: shows the selected workspace and lists
 * the main branch when it can be read and the workspaces of the active repository, then the action
 * creating one.
 */
public class WorkspacePickerAction extends ActionGroup implements CustomComponentAction, DumbAware {

    /**
     * The widest the workspace name gets in the toolbar, in unscaled pixels.
     */
    public static final int MAX_LABEL_WIDTH = 130;

    private static final String LOADING = "Loading…";

    private final WorkspaceSelection selection;
    private final AnAction newWorkspace;

    public WorkspacePickerAction(@NotNull WorkspaceSelection selection, @NotNull AnAction newWorkspace) {
        this.selection = selection;
        this.newWorkspace = newWorkspace;
        Presentation template = getTemplatePresentation();
        template.setPopupGroup(true);
        template.setDisableGroupIfEmpty(false);
        template.setDescription("Workspace");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setIcon(DataformIcons.BRANCH);
        if (selection.activeRepository() == null) {
            presentation.setText(WorkspaceSelection.MAIN_BRANCH, false);
            presentation.setEnabled(false);
            return;
        }
        List<String> workspaces = selection.workspaceIds();
        String selected = selection.selectedWorkspaceId();
        boolean signedOut = DataformAuthState.getInstance().getStatus() != AuthStatus.NONE;
        boolean loading = workspaces == null && !signedOut;
        presentation.setEnabled(workspaces != null && !signedOut);
        presentation.setText(loading ? LOADING : selected != null ? selected : selection.noWorkspaceName(), false);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return choices().getChildActionsOrStubs();
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
        return new PickerButton(this, presentation, place, MAX_LABEL_WIDTH);
    }

    /**
     * The choices of the picker: the main branch when it can be read, the workspaces of the active
     * repository, then the action creating a workspace.
     */
    public @NotNull DefaultActionGroup choices() {
        DefaultActionGroup group = new DefaultActionGroup();
        String mainBranch = selection.mainBranchChoice();
        if (mainBranch != null) {
            group.add(new PickerChoice(mainBranch, null, null,
                    () -> selection.selectedWorkspaceId() == null, () -> selection.selectWorkspace(null)));
        }
        List<String> workspaces = selection.workspaceIds();
        for (String workspace : workspaces != null ? workspaces : List.<String>of()) {
            group.add(new PickerChoice(workspace, DataformIcons.BRANCH, null,
                    () -> workspace.equals(selection.selectedWorkspaceId()), () -> selection.selectWorkspace(workspace)));
        }
        if (group.getChildrenCount() > 0) {
            group.addSeparator();
        }
        group.add(newWorkspace);
        return group;
    }
}
