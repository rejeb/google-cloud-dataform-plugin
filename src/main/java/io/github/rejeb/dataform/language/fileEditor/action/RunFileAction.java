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

import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.fileEditor.SqlxCompiledPreviewEditor;
import io.github.rejeb.dataform.language.fileEditor.SqlxPreviewMode;
import io.github.rejeb.dataform.language.ui.Toolbars;
import org.jetbrains.annotations.NotNull;

/**
 * The Run action / Run test button of the SQLX editor toolbar: runs the file through the run
 * configuration produced from its context, a Dataform workflow for an action file and the unit
 * test for a test file.
 */
public class RunFileAction extends RunContextAction {

    private final SqlxCompiledPreviewEditor preview;

    public RunFileAction(@NotNull SqlxCompiledPreviewEditor preview) {
        super(DefaultRunExecutor.getRunExecutorInstance());
        this.preview = preview;
        getTemplatePresentation().setText(label(SqlxPreviewMode.ACTION));
        getTemplatePresentation().setIcon(DataformIcons.RUN);
        Toolbars.withText(this);
    }

    /**
     * The text of the button for a file shown in the given preview mode.
     */
    public static @NotNull String label(@NotNull SqlxPreviewMode mode) {
        return mode == SqlxPreviewMode.UNIT_TEST ? "Run test" : "Run action";
    }

    /**
     * Updates the context run as the platform does, but keeps the button in place, disabled, when
     * the file has nothing to run.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        if (!presentation.isVisible()) {
            presentation.setVisible(true);
            presentation.setEnabled(false);
        }
        presentation.setText(label(preview.getMode()));
        presentation.setIcon(DataformIcons.RUN);
    }
}
