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
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The pieces of a SQL element every reader of the SQL PSI needs: the identifier naming it, a child
 * of a given kind, and the name behind the backquotes.
 *
 * <p>Kept in one place so that the several readers of this plugin — resolution, navigation, rename —
 * answer the same question the same way.</p>
 */
public final class SqlPsiParts {

    private SqlPsiParts() {
    }

    /**
     * The last identifier of an element, which is the one naming it: the alias of an {@code AS}
     * expression, the column of a qualified reference.
     */
    public static @Nullable PsiElement lastIdentifier(@NotNull PsiElement parent) {
        PsiElement last = null;
        for (PsiElement child : parent.getChildren()) {
            if (isType(child, SqlCompositeElementTypes.SQL_IDENTIFIER)) last = child;
        }
        return last;
    }

    /** The first child of an element carrying a given kind, or {@code null} when it has none. */
    public static @Nullable PsiElement childOfType(@NotNull PsiElement parent,
                                                   @NotNull IElementType type) {
        for (PsiElement child : parent.getChildren()) {
            if (isType(child, type)) return child;
        }
        return null;
    }

    /** Whether an element carries a given kind. */
    public static boolean isType(@Nullable PsiElement element, @NotNull IElementType type) {
        return element != null && element.getNode() != null
                && element.getNode().getElementType() == type;
    }

    /**
     * Whether an element is a star of a select list rather than a name.
     *
     * <p>Only the token itself is looked at: a star may carry an {@code EXCEPT} or {@code REPLACE}
     * list, and whether the SQL parser keeps that list inside the element or beside it is not
     * something a reader has to know.</p>
     */
    public static boolean isStar(@Nullable PsiElement element) {
        if (element == null) return false;
        String token = starTokenOf(element);
        return "*".equals(token) || token.endsWith(".*");
    }

    /**
     * The {@code *} of a star element, without the {@code EXCEPT} or {@code REPLACE} list that may
     * follow it inside the same element.
     */
    public static @NotNull String starTokenOf(@NotNull PsiElement element) {
        String text = element.getText();
        int star = text.indexOf('*');
        return star < 0 ? text : text.substring(0, star + 1);
    }

    /** The text of an identifier without the quoting BigQuery allows around it. */
    public static @NotNull String unquoted(@NotNull String text) {
        return text.replace("`", "");
    }
}
