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
package io.github.rejeb.dataform.language.refactoring.column.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameDialog;
import io.github.rejeb.dataform.language.refactoring.column.apply.ColumnRenameProcessor;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlanner;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarResolution;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The rename dialog of the platform, running the Dataform column rename.
 *
 * <p>Everything the dialog already offers is kept — the name field, the preview button, the search
 * options — and only what it does on OK changes: the places are planned from the lineage of the
 * column rather than from the references of a PSI element. An element that is no Dataform column is
 * handed back to the platform, which renames it the usual way.</p>
 */
public final class DataformColumnRenameDialog extends RenameDialog {

    public DataformColumnRenameDialog(@NotNull Project project,
                                      @NotNull PsiElement element,
                                      PsiElement nameSuggestionContext,
                                      Editor editor) {
        super(project, element, nameSuggestionContext, editor);
    }

    @Override
    protected void doAction() {
        if (!(getPsiElement() instanceof DataformDasColumn column)) {
            super.doAction();
            return;
        }
        Optional<ColumnRenameSubject> subject = ColumnRenameSubjectFactory.of(column);
        if (subject.isEmpty()) {
            close(CANCEL_EXIT_CODE);
            return;
        }
        ColumnRenamePlan plan = ColumnRenamePlanner
                .planUnderProgress(getProject(), subject.get(), getNewName());
        if (plan == null) {
            close(CANCEL_EXIT_CODE);
            return;
        }
        if (plan.needsStarDecision()) {
            StarResolution resolution = StarResolutionChooser.getInstance(getProject())
                    .choose(getProject(), plan);
            plan = ColumnRenamePlanner.getInstance(getProject()).resolve(plan, resolution);
        }
        invokeRefactoring(new ColumnRenameProcessor(getProject(), plan));
    }
}
