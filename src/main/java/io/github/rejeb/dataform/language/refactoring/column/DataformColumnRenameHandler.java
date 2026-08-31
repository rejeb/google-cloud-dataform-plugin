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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubjectFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Takes the rename gesture when the caret is on a Dataform column, and leaves every other name
 * alone.
 *
 * <p>Registered before the handlers of the SQL plugin, which offers an in-place rename of its own on
 * a select-list alias. That rename rewrites the query it is in and nothing else, which for a column
 * of a Dataform table means every action reading it stops compiling. Everything that is not a
 * Dataform column — a table alias, the name of a common table expression, a column of a CTE — is
 * declined here and keeps the behaviour it has today.</p>
 */
public class DataformColumnRenameHandler extends VariableInplaceRenameHandler {

    private static final Logger LOG = Logger.getInstance(DataformColumnRenameHandler.class);

    @Override
    protected boolean isAvailable(@Nullable PsiElement element,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file) {
        return subjectAt(editor, file.getProject()).isPresent();
    }

    @Override
    public @Nullable InplaceRefactoring doRename(@NotNull PsiElement elementToRename,
                                                 @NotNull Editor editor,
                                                 @Nullable DataContext dataContext) {
        Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
        Optional<ColumnRenameSubject> subject = subjectAt(editor, elementToRename.getProject());
        if (subject.isEmpty()) {
            return super.doRename(elementToRename, editor, dataContext);
        }
        ColumnRenameSubject renamed = subject.get();
        DataformColumnRenameAnchor anchor =
                new DataformColumnRenameAnchor(renamed.identifier(), renamed.oldName());
        DataformColumnInplaceRenamer renamer = new DataformColumnInplaceRenamer(anchor, renamed,
                editor, hostEditor.getCaretModel().getOffset());
        renamer.performInplaceRefactoring(null);
        return renamer;
    }

    /**
     * The column the caret is on, read from the editor alone.
     *
     * <p>The element the platform hands over is the one the caret resolves to, which for a Dataform
     * column is a synthetic element whose file is the file that <em>declares</em> the column — not
     * the file being edited. Deciding anything from it renames the wrong file, or nothing at all.
     * The caret is the only thing that says where the gesture happened.</p>
     */
    private static @NotNull Optional<ColumnRenameSubject> subjectAt(@NotNull Editor editor,
                                                                    @NotNull Project project) {
        try {
            Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
            PsiFile hostFile = PsiDocumentManager.getInstance(project)
                    .getPsiFile(hostEditor.getDocument());
            return ColumnRenameSubjectFactory.at(hostFile, hostEditor.getCaretModel().getOffset());
        } catch (Exception e) {
            LOG.warn("Could not decide whether the caret is on a Dataform column", e);
            return Optional.empty();
        }
    }
}
