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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Answers Go to Declaration on a Dataform column with the column window, and leaves every other
 * name to the platform.
 *
 * <p>The gesture cannot be taken any later than this. Ctrl+Click reaches the action through the
 * keymap before the event is ever delivered to the editor, so an editor mouse listener that
 * consumes the click runs after the platform has already navigated — the window would open on top
 * of a jump the user did not ask for. Taking the action itself is what makes the two exclusive.</p>
 *
 * <p>Everything outside a known Dataform column falls through to {@code super}, including any
 * failure to decide: navigation is a gesture the whole IDE depends on, and a plugin that breaks it
 * breaks far more than it fixes.</p>
 */
public class SqlxColumnNavigationAction extends GotoDeclarationAction {

    private static final Logger LOG = Logger.getInstance(SqlxColumnNavigationAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        Project project = event.getProject();
        ColumnWindowTarget target = targetAt(event);
        if (editor == null || project == null || target == null) {
            super.actionPerformed(event);
            return;
        }
        ColumnUsagesPopup.show(project, editor, target, SqlxColumnAtCaret.referenceAt(
                event.getData(CommonDataKeys.PSI_FILE), editor.getCaretModel().getOffset()));
    }

    /**
     * The Dataform column the caret sits on, or {@code null} when there is none and when anything
     * at all goes wrong deciding.
     */
    private @Nullable ColumnWindowTarget targetAt(@NotNull AnActionEvent event) {
        try {
            Editor editor = event.getData(CommonDataKeys.EDITOR);
            PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
            if (editor == null || file == null) return null;
            return ColumnWindowTarget.at(file, editor.getCaretModel().getOffset());
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Could not decide whether the caret is on a Dataform column", e);
            return null;
        }
    }
}
