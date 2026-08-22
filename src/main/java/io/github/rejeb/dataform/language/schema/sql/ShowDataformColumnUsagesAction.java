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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.usages.ColumnUsagesPopup;
import io.github.rejeb.dataform.language.schema.sql.usages.ColumnWindowTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens the usage view of the Dataform column under the caret, as a popup that lists where the
 * column is declared and every place that reads it, and that opens into the Find window.
 *
 * <p>The same window Ctrl+Click opens on a column, reachable from the keyboard and the editor
 * menu.</p>
 */
public class ShowDataformColumnUsagesAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(columnAt(event) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        ColumnWindowTarget target = columnAt(event);
        if (editor == null || target == null || event.getProject() == null) return;
        ColumnUsagesPopup.show(event.getProject(), editor, target, null);
    }

    private @Nullable ColumnWindowTarget columnAt(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) return null;
        return ColumnWindowTarget.at(file, editor.getCaretModel().getOffset());
    }
}
