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

import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.refactoring.column.apply.ColumnRenameProcessor;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.refactoring.column.plan.ColumnRenamePlanner;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarResolution;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory;
import io.github.rejeb.dataform.language.refactoring.column.ui.StarResolutionChooser;
import io.github.rejeb.dataform.language.refactoring.column.usage.InjectedSqlFiles;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The live template of a column rename.
 *
 * <p>The template covers the occurrences of the file being edited, which is all a template can
 * cover. When it is committed the platform puts the file back as it was and calls
 * {@link #performRefactoringRename}, where the rename of the whole project is planned and run.</p>
 */
public final class DataformColumnInplaceRenamer extends MemberInplaceRenamer {

    private final VirtualFile hostVirtualFile;
    private final int hostOffset;
    private final String oldName;

    public DataformColumnInplaceRenamer(@NotNull DataformColumnRenameAnchor anchor,
                                        @NotNull ColumnRenameSubject subject,
                                        @NotNull Editor editor,
                                        int hostOffset) {
        this(anchor, subject, editor, hostOffset, subject.oldName());
    }

    private DataformColumnInplaceRenamer(@NotNull DataformColumnRenameAnchor anchor,
                                         @NotNull ColumnRenameSubject subject,
                                         @NotNull Editor editor,
                                         int hostOffset,
                                         @NotNull String initialName) {
        super(anchor, subject.identifier(), editor, initialName, subject.oldName());
        this.hostVirtualFile = subject.hostFile().getVirtualFile();
        this.hostOffset = hostOffset;
        this.oldName = subject.oldName();
    }

    /**
     * The renamer the platform starts over with, after the rename options were changed from the
     * template or an invalid name was typed. The document has been rolled back by then, so the
     * column is read again from where the gesture started; the generic renamer the platform would
     * build otherwise renames through {@link DataformColumnRenameAnchor#setName}, which refuses.
     */
    @Override
    protected @NotNull VariableInplaceRenamer createInplaceRenamerToRestart(PsiNamedElement variable,
                                                                            Editor editor,
                                                                            String initialName) {
        PsiFile hostFile = hostFile();
        Optional<ColumnRenameSubject> subject = hostFile == null
                ? Optional.empty()
                : ColumnRenameSubjectFactory.at(hostFile, hostOffset);
        if (subject.isEmpty()) {
            return super.createInplaceRenamerToRestart(variable, editor, initialName);
        }
        DataformColumnRenameAnchor anchor =
                new DataformColumnRenameAnchor(subject.get().identifier(), subject.get().oldName());
        return new DataformColumnInplaceRenamer(anchor, subject.get(), editor, hostOffset,
                initialName == null ? subject.get().oldName() : initialName);
    }

    /**
     * The identifier the template is typed into. The element being renamed is not part of the tree,
     * so the platform is pointed at the identifier the caret sits on.
     */
    @Override
    protected @Nullable PsiElement getNameIdentifier() {
        return getVariable() instanceof DataformColumnRenameAnchor anchor
                ? anchor.getNameIdentifier()
                : super.getNameIdentifier();
    }

    /**
     * No reference is collected for the template. The occurrences of the file are added as
     * additional elements instead: a Dataform column is resolved through the schema, and searching
     * for its references would run a project-wide search on every keystroke of the gesture.
     */
    @Override
    protected @NotNull Collection<PsiReference> collectRefs(@NotNull SearchScope referencesSearchScope) {
        return List.of();
    }

    /**
     * The other places of the file that show the same name: the reads of the column in the query and
     * in the operations blocks. A name resolving to a column of another table is left out.
     */
    @Override
    protected void collectAdditionalElementsToRename(
            @NotNull List<? super Pair<PsiElement, TextRange>> stringUsages) {
        PsiFile hostFile = hostFile();
        if (hostFile == null) return;
        PsiElement primary = getNameIdentifier();
        for (PsiFile injected : InjectedSqlFiles.all(hostFile)) {
            for (PsiElement reference : PsiTreeUtil.collectElements(injected,
                    element -> SqlPsiParts.isType(element,
                            SqlCompositeElementTypes.SQL_COLUMN_REFERENCE))) {
                PsiElement identifier = SqlPsiParts.lastIdentifier(reference);
                if (identifier == null || identifier == primary) continue;
                if (!SqlPsiParts.unquoted(identifier.getText()).equalsIgnoreCase(oldName)) continue;
                if (resolvesElsewhere(reference)) continue;
                stringUsages.add(Pair.create(identifier,
                        TextRange.from(0, identifier.getTextLength())));
            }
        }
    }

    @Override
    protected @Nullable PsiElement checkLocalScope() {
        return hostFile();
    }

    @Override
    protected boolean isIdentifier(String newName, com.intellij.lang.Language language) {
        return DataformColumnNameValidator.isValid(newName);
    }

    /**
     * Plans and runs the rename of the whole project, the template having been rolled back by the
     * platform beforehand.
     */
    @Override
    protected void performRefactoringRename(@NotNull String newName, StartMarkAction markAction) {
        try {
            tryRollback();
            if (newName.equals(oldName)) return;
            PsiFile hostFile = hostFile();
            if (hostFile == null) return;
            Optional<ColumnRenameSubject> subject =
                    ColumnRenameSubjectFactory.at(hostFile, hostOffset);
            if (subject.isEmpty()) return;
            ColumnRenamePlan plan =
                    ColumnRenamePlanner.planUnderProgress(myProject, subject.get(), newName);
            if (plan == null) return;
            if (plan.needsStarDecision()) {
                StarResolution resolution =
                        StarResolutionChooser.getInstance(myProject).choose(myProject, plan);
                plan = ColumnRenamePlanner.getInstance(myProject).resolve(plan, resolution);
            }
            new ColumnRenameProcessor(myProject, plan).run();
        } finally {
            FinishMarkAction.finish(myProject, myEditor, markAction);
        }
    }

    private @Nullable PsiFile hostFile() {
        return hostVirtualFile == null || !hostVirtualFile.isValid()
                ? null
                : PsiManager.getInstance(myProject).findFile(hostVirtualFile);
    }

    /**
     * Whether a reference of the file stands for a column of another table that happens to carry the
     * same name.
     *
     * <p>The name is what decides here, where the plan asks whether the column is one of those the
     * lineage puts in the rename. The two cannot ask the same question: the template runs before
     * anything is planned, and the reads of the file are of the column of its source, which the
     * lineage reaches and a template cannot.</p>
     */
    private boolean resolvesElsewhere(@NotNull PsiElement reference) {
        PsiReference psiReference = reference.getReference();
        PsiElement resolved = psiReference == null ? null : psiReference.resolve();
        if (!(resolved instanceof DataformDasColumn column)) return false;
        ColumnRef declared = ColumnOriginService.getInstance(myProject).reference(column);
        return declared != null && !declared.columnName().equalsIgnoreCase(oldName);
    }
}
