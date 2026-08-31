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
package io.github.rejeb.dataform.language.refactoring.column.target;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Decides which Dataform column a caret means.
 *
 * <p>Answers from the schema alone. The lineage graph is not read here: this runs on every rename
 * gesture, including the ones meant for another handler, and reading the graph parses SQL.</p>
 */
public final class ColumnRenameSubjectFactory {

    private ColumnRenameSubjectFactory() {
    }

    /**
     * The rename subject at an offset of a SQLX file, empty when the offset holds no Dataform
     * column and the gesture belongs to another handler.
     */
    public static @NotNull Optional<ColumnRenameSubject> at(@Nullable PsiFile hostFile, int offset) {
        if (hostFile == null || !hostFile.getName().endsWith(".sqlx")) return Optional.empty();
        PsiElement injected = InjectedLanguageManager.getInstance(hostFile.getProject())
                .findInjectedElementAt(hostFile, offset);
        if (injected == null) return Optional.empty();

        Optional<ColumnRenameSubject> alias = fromAlias(injected, hostFile);
        return alias.isPresent() ? alias : fromReference(injected, hostFile);
    }

    /**
     * A column named by the alias of an {@code AS} expression. Only an alias of the main select list
     * names a column of the table the file builds; an alias inside a common table expression is
     * local to the query and is left to the SQL plugin.
     */
    private static @NotNull Optional<ColumnRenameSubject> fromAlias(@NotNull PsiElement token,
                                                                    @NotNull PsiFile hostFile) {
        PsiElement identifier = token.getParent();
        if (!SqlPsiParts.isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)) {
            return Optional.empty();
        }
        PsiElement expression = identifier.getParent();
        if (!SqlPsiParts.isType(expression, SqlCompositeElementTypes.SQL_AS_EXPRESSION)
                || SqlPsiParts.lastIdentifier(expression) != identifier) {
            return Optional.empty();
        }
        ColumnRef declared = ColumnOriginService.getInstance(hostFile.getProject())
                .declaredColumn(hostFile, expression);
        return declared == null
                ? Optional.empty()
                : Optional.of(new ColumnRenameSubject(declared, identifier, hostFile,
                        ColumnRenameSubject.Kind.SELECT_ALIAS));
    }

    /**
     * A column named by a reference: the one the reference declares when it is an item of the main
     * select list, and otherwise the one it resolves to.
     */
    private static @NotNull Optional<ColumnRenameSubject> fromReference(@NotNull PsiElement token,
                                                                        @NotNull PsiFile hostFile) {
        PsiElement reference = SqlxColumnAtCaret.referenceOf(token);
        if (reference == null) return Optional.empty();
        PsiElement identifier = SqlPsiParts.lastIdentifier(reference);
        if (identifier == null || !identifier.getTextRange().contains(token.getTextRange())) {
            return Optional.empty();
        }
        ColumnOriginService origins = ColumnOriginService.getInstance(hostFile.getProject());

        ColumnRef declared = origins.declaredColumn(hostFile, reference);
        if (declared != null) {
            return Optional.of(new ColumnRenameSubject(declared, identifier, hostFile,
                    ColumnRenameSubject.Kind.SELECT_ITEM));
        }
        PsiReference psiReference = reference.getReference();
        PsiElement resolved = psiReference == null ? null : psiReference.resolve();
        if (!(resolved instanceof DataformDasColumn column)) return Optional.empty();
        ColumnRef read = origins.reference(column);
        return read == null
                ? Optional.empty()
                : Optional.of(new ColumnRenameSubject(read, identifier, hostFile,
                        ColumnRenameSubject.Kind.READ));
    }

    /**
     * The rename subject of a schema column, for the gestures that start outside an editor: the
     * usage window, the lineage view, Find Usages. The subject is anchored on the element declaring
     * the column, which is where its file names it.
     */
    public static @NotNull Optional<ColumnRenameSubject> of(@NotNull DataformDasColumn column) {
        ColumnOriginService origins = ColumnOriginService.getInstance(column.getProject());
        ColumnRef reference = origins.reference(column);
        if (reference == null) return Optional.empty();
        PsiElement declaration = origins.declaringElement(reference);
        if (declaration == null) return Optional.empty();
        PsiFile hostFile = InjectedLanguageManager.getInstance(column.getProject())
                .getTopLevelFile(declaration.getContainingFile());
        return hostFile == null
                ? Optional.empty()
                : Optional.of(new ColumnRenameSubject(reference, declaration, hostFile,
                        ColumnRenameSubject.Kind.SELECT_ITEM));
    }
}
