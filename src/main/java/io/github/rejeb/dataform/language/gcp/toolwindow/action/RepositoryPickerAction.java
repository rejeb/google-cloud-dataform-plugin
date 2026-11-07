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
import io.github.rejeb.dataform.language.gcp.settings.DataformRepositoryConfig;
import io.github.rejeb.dataform.language.gcp.toolwindow.WorkspaceSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/**
 * The repository picker of the Dataform tool window toolbar: shows the active repository and
 * lists every configured one, then the action managing them.
 */
public class RepositoryPickerAction extends ActionGroup implements CustomComponentAction, DumbAware {

    /**
     * The widest the repository name gets in the toolbar, in unscaled pixels.
     */
    public static final int MAX_LABEL_WIDTH = 120;

    private static final String NO_REPOSITORY = "No repository";

    private final WorkspaceSelection selection;
    private final AnAction manageRepositories;

    public RepositoryPickerAction(@NotNull WorkspaceSelection selection, @NotNull AnAction manageRepositories) {
        this.selection = selection;
        this.manageRepositories = manageRepositories;
        Presentation template = getTemplatePresentation();
        template.setPopupGroup(true);
        template.setDisableGroupIfEmpty(false);
        template.setDescription("Repository");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        DataformRepositoryConfig active = selection.activeRepository();
        Presentation presentation = e.getPresentation();
        presentation.setIcon(DataformIcons.REPOSITORY);
        presentation.setText(active != null ? active.displayName() : NO_REPOSITORY, false);
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
     * The choices of the picker: every configured repository, then the action managing them.
     */
    public @NotNull DefaultActionGroup choices() {
        DefaultActionGroup group = new DefaultActionGroup();
        for (DataformRepositoryConfig repository : selection.repositories()) {
            group.add(new PickerChoice(repository.displayName(), null, repository.projectId(),
                    () -> isActive(repository), () -> selection.selectRepository(repository)));
        }
        group.addSeparator();
        group.add(manageRepositories);
        return group;
    }

    private boolean isActive(@NotNull DataformRepositoryConfig repository) {
        DataformRepositoryConfig active = selection.activeRepository();
        return active != null && active.repositoryConfigId().equals(repository.repositoryConfigId());
    }
}
