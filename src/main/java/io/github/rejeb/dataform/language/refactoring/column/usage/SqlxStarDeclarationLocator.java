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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the aliases that write the name of a column a query produces with a star.
 *
 * <p>A query reading a literal {@code UNNEST([STRUCT(1 AS customer_id, …)])} and selecting a star
 * names its columns in that struct. Those aliases are the declaration of the column, so a rename
 * has somewhere exact to write and the user has nothing to decide.</p>
 */
public final class SqlxStarDeclarationLocator {

    private SqlxStarDeclarationLocator() {
    }

    /**
     * The identifiers naming {@code columnName} outside the select list of the main query, which is
     * where a starred query takes the name from. Empty when the file names it nowhere.
     */
    public static @NotNull List<PsiElement> findStructAliases(@NotNull PsiFile hostFile,
                                                              @NotNull String columnName) {
        List<PsiElement> found = new ArrayList<>();
        for (PsiFile injected : InjectedSqlFiles.mainQuery(hostFile)) {
            PsiElement selectClause = mainSelectClause(injected);
            for (PsiElement expression : PsiTreeUtil.collectElements(injected,
                    element -> isType(element, SqlCompositeElementTypes.SQL_AS_EXPRESSION))) {
                if (selectClause != null && PsiTreeUtil.isAncestor(selectClause, expression, false)) {
                    continue;
                }
                PsiElement identifier = lastIdentifier(expression);
                if (identifier != null && unquoted(identifier.getText()).equalsIgnoreCase(columnName)) {
                    found.add(identifier);
                }
            }
        }
        return found;
    }

    /** Whether an element is a star of a select list rather than a name. */
    public static boolean isStar(@Nullable PsiElement element) {
        if (element == null) return false;
        String text = element.getText();
        return "*".equals(text) || text.endsWith(".*");
    }

    /** The select clause producing the rows of the main query of an injected SQL file. */
    public static @Nullable PsiElement mainSelectClause(@NotNull PsiFile injected) {
        PsiElement statement = childOfType(injected, SqlCompositeElementTypes.SQL_SELECT_STATEMENT);
        if (statement == null) return null;
        PsiElement query = childOfType(statement, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION);
        if (query == null) {
            PsiElement with = childOfType(statement, SqlCompositeElementTypes.SQL_WITH_QUERY_EXPRESSION);
            if (with != null) query = childOfType(with, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION);
        }
        return query == null ? null : childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
    }

    private static @Nullable PsiElement childOfType(@NotNull PsiElement parent,
                                                    @NotNull IElementType type) {
        for (PsiElement child : parent.getChildren()) {
            if (child.getNode() != null && child.getNode().getElementType() == type) return child;
        }
        return null;
    }

    private static @Nullable PsiElement lastIdentifier(@NotNull PsiElement parent) {
        PsiElement last = null;
        for (PsiElement child : parent.getChildren()) {
            if (isType(child, SqlCompositeElementTypes.SQL_IDENTIFIER)) last = child;
        }
        return last;
    }

    private static boolean isType(@Nullable PsiElement element, @NotNull IElementType type) {
        return element != null && element.getNode() != null
                && element.getNode().getElementType() == type;
    }

    private static @NotNull String unquoted(@NotNull String text) {
        return text.replace("`", "");
    }
}
