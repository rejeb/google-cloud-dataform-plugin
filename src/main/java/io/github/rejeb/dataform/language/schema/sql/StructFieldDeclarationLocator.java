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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sql.psi.SqlCommonKeywords;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Locates where a field of a struct column is written, in the file of the action building it.
 *
 * <p>A struct column is built by a {@code STRUCT(...)} constructor whose items each name a field,
 * and a field holding fields is another constructor inside one of those items. The shape repeats at
 * every level, so one step per segment walks a path of any depth down to its leaf.</p>
 *
 * <p>The walk stops on the deepest segment it can account for rather than giving up: a file that
 * selects a star, or builds the column by calling something instead of writing a constructor, still
 * has a line worth landing on, and landing on the column is more use than landing nowhere.</p>
 */
public final class StructFieldDeclarationLocator {

    private StructFieldDeclarationLocator() {
    }

    /**
     * The element naming the last segment of {@code segments} in the file's main query, or
     * {@code null} when the file declares no column of that name at all.
     */
    public static @Nullable PsiElement declaringElement(@NotNull PsiFile producingFile,
                                                       @NotNull List<String> segments) {
        if (segments.isEmpty()) return null;
        PsiElement declared = SqlxOutputColumnLocator.findOutputColumn(producingFile,
                segments.getFirst());
        if (declared == null) return null;

        PsiElement owner = declared.getParent();
        for (String segment : segments.subList(1, segments.size())) {
            if (!SqlPsiParts.isType(owner, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) return declared;
            PsiElement constructor = structConstructorIn(owner);
            if (constructor == null) return declared;
            PsiElement field = fieldNamed(constructor, segment);
            if (field == null) return declared;
            PsiElement name = SqlPsiParts.lastIdentifier(field);
            if (name == null) return declared;
            declared = name;
            owner = field;
        }
        return declared;
    }

    /**
     * The {@code STRUCT(...)} an item is built by, looked for past whatever wraps it: an aggregate
     * collecting the structs of a group holds one just as a bare item does. Items of a constructor
     * are skipped, because a constructor inside one of them builds a field rather than this one.
     */
    private static @Nullable PsiElement structConstructorIn(@NotNull PsiElement expression) {
        for (PsiElement child : expression.getChildren()) {
            if (SqlPsiParts.isType(child, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) continue;
            if (isStructConstructor(child)) return child;
            PsiElement nested = structConstructorIn(child);
            if (nested != null) return nested;
        }
        return null;
    }

    /** Whether an element is a {@code STRUCT(...)} constructor rather than any other parenthesis. */
    static boolean isStructConstructor(@NotNull PsiElement element) {
        return SqlPsiParts.isType(element, SqlCompositeElementTypes.SQL_PARENTHESIZED_EXPRESSION)
                && SqlPsiParts.childOfType(element, SqlCommonKeywords.SQL_STRUCT) != null;
    }

    private static @Nullable PsiElement fieldNamed(@NotNull PsiElement constructor,
                                                   @NotNull String name) {
        for (PsiElement child : constructor.getChildren()) {
            if (!SqlPsiParts.isType(child, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) continue;
            PsiElement identifier = SqlPsiParts.lastIdentifier(child);
            if (identifier == null) continue;
            if (SqlPsiParts.unquoted(identifier.getText()).equalsIgnoreCase(name)) return child;
        }
        return null;
    }
}
