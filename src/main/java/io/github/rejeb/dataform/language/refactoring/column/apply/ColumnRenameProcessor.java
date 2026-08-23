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
package io.github.rejeb.dataform.language.refactoring.column.apply;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Renames a Dataform column everywhere the plan says.
 *
 * <p>Runs as a platform refactoring, so the places can be reviewed in the rename window before
 * anything is written, and so one undo takes the whole rename back. What is written is the array of
 * usages the platform hands over, which is the plan minus whatever the user excluded.</p>
 */
public final class ColumnRenameProcessor extends BaseRefactoringProcessor {

    /** The identifier refactoring listeners and tests observe this refactoring under. */
    public static final String REFACTORING_ID = "refactoring.dataform.column.rename";

    private static final String NOTIFICATION_GROUP = "Dataform.Notifications";

    private final ColumnRenamePlan plan;

    public ColumnRenameProcessor(@NotNull Project project, @NotNull ColumnRenamePlan plan) {
        super(project);
        this.plan = plan;
        setPreviewUsages(plan.needsPreview());
    }

    @Override
    protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
        return new ColumnRenameViewDescriptor(columnElement(), plan.subject().oldName());
    }

    @Override
    protected UsageInfo @NotNull [] findUsages() {
        return ColumnRenameUsageInfo.allOf(plan);
    }

    /**
     * Stops a rename that cannot run, and shows the plan's warnings as conflicts so the user reads
     * them before the places are written.
     */
    @Override
    protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
        if (plan.refusal() != null) {
            notify(plan.refusal(), NotificationType.WARNING);
            return false;
        }
        return super.preprocessUsages(refUsages);
    }

    @Override
    protected void performRefactoring(UsageInfo @NotNull [] usages) {
        int written = ColumnRenameEdits.apply(myProject, usages);
        int excluded = plan.edits().size() - usages.length;
        notify(message(written, usages, excluded), NotificationType.INFORMATION);
    }

    @Override
    protected @NotNull String getCommandName() {
        return "Rename column " + plan.subject().oldName() + " to " + plan.newName();
    }

    @Override
    protected @Nullable String getRefactoringId() {
        return REFACTORING_ID;
    }

    private @NotNull String message(int written, UsageInfo @NotNull [] usages, int excluded) {
        Set<String> files = new LinkedHashSet<>();
        for (UsageInfo usage : usages) {
            if (usage instanceof ColumnRenameUsageInfo info) files.add(info.edit().file().getPath());
        }
        StringBuilder message = new StringBuilder()
                .append("Renamed ").append(plan.subject().oldName())
                .append(" to ").append(plan.newName())
                .append(": ").append(written).append(" places in ").append(files.size())
                .append(" files.");
        if (excluded > 0) {
            message.append(" ").append(excluded)
                    .append(" places were excluded and keep the old name.");
        }
        for (String warning : plan.warnings()) {
            message.append(" ").append(warning).append(".");
        }
        return message.toString();
    }

    private void notify(@NotNull String message, @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(message, type)
                .notify(myProject);
    }

    /** The element the window shows as the subject of the rename. */
    private @NotNull PsiElement columnElement() {
        DataformDasColumn column = ColumnOriginService.getInstance(myProject)
                .dasColumn(plan.subject().column());
        return column != null ? column : plan.subject().identifier();
    }
}
