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
package io.github.rejeb.dataform.language.refactoring.column;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import io.github.rejeb.dataform.language.refactoring.column.apply.ColumnRenameEdits;
import io.github.rejeb.dataform.language.refactoring.column.apply.ColumnRenameUsageInfo;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlanner;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory;
import io.github.rejeb.dataform.language.refactoring.column.ui.DataformColumnRenameDialog;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Renames a Dataform column when the gesture starts outside an editor: the column window, the
 * lineage view, the Find Usages window.
 *
 * <p>Registered before the processor of the SQL plugin, which would hand the column to the platform
 * and get a refusal: a schema column has no source of its own to write.</p>
 */
public class DataformColumnRenamePsiElementProcessor extends RenamePsiElementProcessor {

    /**
     * A plan and the gesture it was built for.
     *
     * @param element the element {@link #prepareRenaming} was called with
     * @param newName the name that rename is giving it
     * @param plan    what that rename is going to write
     */
    private record PreparedPlan(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull ColumnRenamePlan plan) {
    }

    private volatile PreparedPlan prepared;

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return element instanceof DataformDasColumn;
    }

    /**
     * The platform's rename dialog, with its preview button and its scope chooser, running this
     * refactoring rather than the platform's own.
     */
    @Override
    public @NotNull RenameDialog createRenameDialog(@NotNull Project project,
                                                    @NotNull PsiElement element,
                                                    PsiElement nameSuggestionContext,
                                                    Editor editor) {
        return new DataformColumnRenameDialog(project, element, nameSuggestionContext, editor);
    }

    /** The in-place rename is offered by the handler, which knows where the caret is. */
    @Override
    public boolean isInplaceRenameSupported() {
        return false;
    }

    /**
     * Plans the rename, before the platform opens its write action.
     *
     * <p>Planning reads the lineage graph, which parses SQL on threads of its own. Doing that from
     * inside a write action deadlocks: those threads wait for a read lock the write action holds.
     * So the plan is built here, where nothing is locked yet, and only written in
     * {@link #renameElement}.</p>
     *
     * <p>The platform keeps one processor for the whole IDE, so only the plan of the gesture under
     * way is held: a plan points into every file it touches, and a rename the user leaves after the
     * preview would otherwise hold that for as long as the IDE runs.</p>
     */
    @Override
    public void prepareRenaming(@NotNull PsiElement element,
                                @NotNull String newName,
                                @NotNull Map<PsiElement, String> allRenames) {
        prepared = null;
        if (!(element instanceof DataformDasColumn column)) return;
        ColumnRenameSubjectFactory.of(column)
                .map(subject -> ColumnRenamePlanner.getInstance(column.getProject())
                        .plan(subject, newName))
                .ifPresent(plan -> prepared = new PreparedPlan(element, newName, plan));
    }

    /**
     * Writes the places of the plan built by {@link #prepareRenaming}. A plan that needs an answer
     * about a star is left alone: the question belongs to the dialog, which asks it before starting
     * the refactoring.
     */
    @Override
    public void renameElement(@NotNull PsiElement element,
                              @NotNull String newName,
                              UsageInfo @NotNull [] usages,
                              @Nullable RefactoringElementListener listener) {
        PreparedPlan planned = prepared;
        prepared = null;
        if (planned == null || planned.element() != element
                || !planned.newName().equals(newName)) {
            return;
        }
        ColumnRenamePlan plan = planned.plan();
        if (!plan.isRunnable() || plan.needsStarDecision()) return;
        ColumnRenameEdits.apply(element.getProject(), ColumnRenameUsageInfo.allOf(plan));
    }
}
