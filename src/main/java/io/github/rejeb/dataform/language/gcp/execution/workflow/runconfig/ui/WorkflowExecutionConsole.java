/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class WorkflowExecutionConsole implements ExecutionConsole {

    private final WorkflowExecutionPanel panel;

    public WorkflowExecutionConsole(@NotNull String invocationName, @NotNull Project project) {
        this.panel = new WorkflowExecutionPanel(invocationName, project);
    }

    public void updateProgress(@NotNull WorkflowInvocationProgress progress) {
        panel.updateProgress(progress);
    }

    @Override
    public @NotNull JComponent getComponent() { return panel; }

    @Override
    public JComponent getPreferredFocusableComponent() { return panel; }

    @Override
    public void dispose() {
        panel.release();
    }
}