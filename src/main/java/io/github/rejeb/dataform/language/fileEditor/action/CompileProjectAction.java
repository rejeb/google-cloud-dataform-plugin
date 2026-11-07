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
package io.github.rejeb.dataform.language.fileEditor.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnimatedIcon;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.compilation.DataformBuildContext;
import io.github.rejeb.dataform.language.compilation.DataformBuildManager;
import org.jetbrains.annotations.NotNull;

/**
 * The Compile button of the SQLX editor toolbar: starts a Dataform build of the project and shows a
 * spinner while a build runs.
 */
public class CompileProjectAction extends AnAction implements DumbAware {

    private static final String DESCRIPTION = "Compile the Dataform project";
    private static final String RUNNING_DESCRIPTION = "The Dataform project is compiling";

    private final Project project;

    public CompileProjectAction(@NotNull Project project) {
        super("Compile", DESCRIPTION, DataformIcons.COMPILE);
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DataformBuildManager.build(project);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean running = DataformBuildContext.isBuildRunning(project);
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(!running);
        presentation.setDescription(running ? RUNNING_DESCRIPTION : DESCRIPTION);
        presentation.setIcon(running ? AnimatedIcon.Default.INSTANCE : DataformIcons.COMPILE);
        presentation.setDisabledIcon(running ? AnimatedIcon.Default.INSTANCE : null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
