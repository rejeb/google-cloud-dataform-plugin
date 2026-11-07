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
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.fileEditor.SqlxCompiledPreviewEditor;
import io.github.rejeb.dataform.language.fileEditor.SqlxPreviewMode;
import org.jetbrains.annotations.NotNull;

public class ExecuteQueryAction extends AnAction {

    private final SqlxCompiledPreviewEditor preview;

    public ExecuteQueryAction(@NotNull SqlxCompiledPreviewEditor preview) {
        super("Run query", "Run the compiled query in BigQuery", DataformIcons.RUN);
        this.preview = preview;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        preview.executeQuery(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean unitTest = preview.getMode() == SqlxPreviewMode.UNIT_TEST;
        e.getPresentation().setText(unitTest ? "Run test query" : "Run query");
        e.getPresentation().setDescription(unitTest
                ? "Run the test query or the expected output in BigQuery"
                : "Run the compiled query in BigQuery");
        e.getPresentation().setEnabled(preview.hasQuery());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}