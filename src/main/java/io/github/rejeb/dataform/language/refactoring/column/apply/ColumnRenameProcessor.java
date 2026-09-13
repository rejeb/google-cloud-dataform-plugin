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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.usage.HostRanges;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
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
    private UsageInfo[] kept = UsageInfo.EMPTY_ARRAY;

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
            prepareSuccessful();
            notify(plan.refusal(), NotificationType.WARNING);
            return false;
        }
        return super.preprocessUsages(refUsages);
    }

    /**
     * Keeps the places the user reviewed. Nothing is written here: the platform calls
     * {@link #performPsiSpoilingRefactoring()} right after this, and that is where a refactoring
     * writing documents rather than PSI belongs.
     */
    @Override
    protected void performRefactoring(UsageInfo @NotNull [] usages) {
        kept = usages;
    }

    /** Writes the places, the platform having finished with the PSI the usages point at. */
    @Override
    protected void performPsiSpoilingRefactoring() {
        Set<ColumnRef> renamed = columnsTheKeptPlacesDeclare();
        int written = ColumnRenameEdits.apply(myProject, kept);
        int excluded = plan.edits().size() - kept.length;
        publishNewName(renamed);
        notify(message(written, kept, excluded), NotificationType.INFORMATION);
        kept = UsageInfo.EMPTY_ARRAY;
        DataformEditorRefresher.refresh(myProject);
    }

    /**
     * Tells the schemas that the actions of the rename publish their column under the new name, and
     * repaints what shows it.
     *
     * <p>Without this the column stays unresolved — painted as unknown wherever it is read — until
     * the next compilation and its schema extraction have both finished, which is far longer than a
     * rename feels like it should take.</p>
     */
    private void publishNewName(@NotNull Set<ColumnRef> renamed) {
        if (renamed.isEmpty()) return;
        DataformTableSchemaService.getInstance(myProject)
                .renameColumn(renamed, plan.newName(), writtenFiles());
    }

    /**
     * The columns whose declaration is among the places the user kept, which are the ones the
     * actions really start publishing under the new name.
     *
     * <p>An excluded place is not written, so its action goes on publishing the old name; claiming
     * otherwise would paint the column as unknown everywhere it is read. A column produced by a star
     * the rename could not expand falls out of this on its own — nothing writes where it is
     * declared.</p>
     *
     * <p>Asked before the first character is written: afterwards the old name is gone from the files
     * and the declarations can no longer be found.</p>
     */
    private @NotNull Set<ColumnRef> columnsTheKeptPlacesDeclare() {
        Set<ColumnRef> renamed = new LinkedHashSet<>();
        ColumnOriginService origins = ColumnOriginService.getInstance(myProject);
        for (ColumnRef column : plan.columns()) {
            PsiElement declaration = origins.declaringElement(column);
            if (declaration == null) continue;
            PsiFile hostFile = HostRanges.hostPsiFileOf(declaration);
            TextRange declared = HostRanges.hostRangeOf(declaration,
                    TextRange.from(0, declaration.getTextLength()));
            if (hostFile == null || declared == null) continue;
            if (isKept(hostFile.getVirtualFile(), declared)) renamed.add(column);
        }
        return renamed;
    }

    /** Whether one of the places the user kept writes over a range of a file. */
    private boolean isKept(@Nullable VirtualFile file, @NotNull TextRange declared) {
        for (UsageInfo usage : kept) {
            if (!(usage instanceof ColumnRenameUsageInfo info)) continue;
            TextRange place = info.edit().currentRange();
            if (place != null && info.edit().file().equals(file) && place.intersects(declared)) {
                return true;
            }
        }
        return false;
    }

    /** The files the rename wrote, which is what its claim about the schemas rests on. */
    private @NotNull Set<VirtualFile> writtenFiles() {
        Set<VirtualFile> files = new LinkedHashSet<>();
        for (UsageInfo usage : kept) {
            if (usage instanceof ColumnRenameUsageInfo info) files.add(info.edit().file());
        }
        return files;
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
