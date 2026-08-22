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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the Dataform column a caret sits on. Navigation and the usage search both start from
 * it, and both have to answer the same question: which column does this expression stand for.
 */
public final class SqlxColumnAtCaret {

    private SqlxColumnAtCaret() {
    }

    /** The column reference a token belongs to, or {@code null} when it is not part of one. */
    public static @Nullable PsiElement referenceOf(@NotNull PsiElement token) {
        PsiElement current = token;
        while (current != null) {
            IElementType type = current.getNode() == null ? null : current.getNode().getElementType();
            if (type == SqlCompositeElementTypes.SQL_COLUMN_REFERENCE) return current;
            if (type != SqlCompositeElementTypes.SQL_IDENTIFIER && current != token) return null;
            current = current.getParent();
        }
        return null;
    }

    /** The SQLX file a token belongs to, or {@code null} when it is in another kind of file. */
    public static @Nullable PsiFile sqlxFileOf(@NotNull PsiElement token) {
        PsiFile containing = token.getContainingFile();
        if (containing == null) return null;
        PsiFile topLevel = InjectedLanguageManager.getInstance(token.getProject())
                .getTopLevelFile(containing);
        return topLevel != null && topLevel.getName().endsWith(".sqlx") ? topLevel : null;
    }

    /**
     * The column a reference stands for: the one it declares when it is an item of the main select
     * list, and otherwise the one it resolves to.
     */
    public static @Nullable DataformDasColumn columnOf(@NotNull PsiElement reference,
                                                       @NotNull PsiFile topLevel) {
        ColumnOriginService origins = ColumnOriginService.getInstance(topLevel.getProject());
        ColumnRef declared = origins.declaredColumn(topLevel, reference);
        if (declared != null) {
            DataformDasColumn column = origins.dasColumn(declared);
            if (column != null) return column;
        }
        PsiReference psiReference = reference.getReference();
        if (psiReference == null) return null;
        PsiElement resolved = psiReference.resolve();
        return resolved instanceof DataformDasColumn column ? column : null;
    }

    /** The column reference at an offset of a SQLX file, looking through the injected SQL. */
    public static @Nullable PsiElement referenceAt(@Nullable PsiFile hostFile, int offset) {
        if (hostFile == null) return null;
        PsiElement injected = InjectedLanguageManager.getInstance(hostFile.getProject())
                .findInjectedElementAt(hostFile, offset);
        return injected == null ? null : referenceOf(injected);
    }

    /** The column at an offset of a SQLX file, looking through the injected SQL. */
    public static @Nullable DataformDasColumn columnAt(@NotNull PsiFile hostFile, int offset) {
        PsiElement injected = InjectedLanguageManager.getInstance(hostFile.getProject())
                .findInjectedElementAt(hostFile, offset);
        if (injected == null) return null;
        PsiElement reference = referenceOf(injected);
        if (reference == null) return null;
        PsiFile topLevel = sqlxFileOf(reference);
        return topLevel == null ? null : columnOf(reference, topLevel);
    }
}
