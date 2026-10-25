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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.columns.rename.apply.ColumnRenameProcessor;
import io.github.rejeb.dataform.language.columns.rename.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.util.DataformNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * Renames the reads of a column still made under its old name downstream of the action it was
 * renamed in, through the column rename refactoring and its preview.
 */
public final class DownstreamRenameFix implements IntentionAction {

    private final DownstreamRename rename;

    public DownstreamRenameFix(@NotNull DownstreamRename rename) {
        this.rename = rename;
    }

    /**
     * The text of the fix and of the link offering it.
     */
    public static @NotNull String textOf(@NotNull DownstreamRename rename) {
        return "Rename downstream usages of `" + rename.oldName() + "` to `" + rename.newName() + "`";
    }

    /**
     * Plans the rename under a cancellable progress, then shows its preview. Called on the event
     * thread.
     */
    public static void run(@NotNull Project project, @NotNull DownstreamRename rename) {
        ColumnRenamePlan plan = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> ReadAction.computeBlocking(() -> DownstreamRenamePlanner.plan(project, rename)),
                "Finding Where " + rename.oldName() + " Is Still Read", true, project);
        if (plan == null) return;
        if (plan.refusal() == null && plan.edits().isEmpty()) {
            String warnings = plan.warnings().isEmpty() ? "" : " " + String.join(". ", plan.warnings()) + ".";
            DataformNotifications.create("No downstream read of `" + rename.oldName() + "` can be renamed."
                    + warnings, NotificationType.WARNING).notify(project);
            return;
        }
        ColumnRenameProcessor processor = new ColumnRenameProcessor(project, plan);
        processor.setPreviewUsages(true);
        processor.run();
    }

    @Override
    public @NotNull String getText() {
        return textOf(rename);
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Rename downstream usages of a renamed column";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        run(project, rename);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor,
                                                         @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }
}
