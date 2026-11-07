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
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.toolwindow.ManageRepositoriesDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class ManageRepositoriesAction extends AnAction implements ActionUpdateThreadAware.Recursive {

    private final Project project;
    private final Runnable onRepositoryChanged;

    /**
     * The action of the repository picker, shown with the settings gear.
     */
    public ManageRepositoriesAction(@NotNull Project project, @NotNull Runnable onRepositoryChanged) {
        this(project, onRepositoryChanged, AllIcons.General.Settings);
    }

    /**
     * The action shown with {@code icon}, which runs {@code onRepositoryChanged} once the dialog is closed.
     */
    public ManageRepositoriesAction(@NotNull Project project, @NotNull Runnable onRepositoryChanged, @NotNull Icon icon) {
        super("Manage Repositories…", "Add, edit or remove Dataform GCP repositories", icon);
        this.project = project;
        this.onRepositoryChanged = onRepositoryChanged;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        new ManageRepositoriesDialog(project).show();
        onRepositoryChanged.run();
    }
}
