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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the select-list item of a common table expression that a qualified column reference reads.
 *
 * <p>The qualifier is an alias bound in the {@code FROM} of the enclosing query. It names a CTE
 * only when the with-clause of the same statement declares a query of that name; a qualifier
 * naming a table is left alone, since the schema resolves it already.</p>
 */
public final class SqlxCteItemLocator {

    private SqlxCteItemLocator() {
    }

    /**
     * The item of the CTE's select list producing {@code columnName}, or {@code null} when the
     * qualifier is not a CTE alias of the enclosing statement.
     */
    public static @Nullable PsiElement findCteItem(@NotNull PsiElement reference,
                                                   @NotNull String qualifier,
                                                   @NotNull String columnName) {
        PsiElement statement = enclosing(reference, SqlCompositeElementTypes.SQL_SELECT_STATEMENT);
        if (statement == null) return null;
        PsiElement with = childOfType(statement, SqlCompositeElementTypes.SQL_WITH_QUERY_EXPRESSION);
        if (with == null) return null;
        PsiElement withClause = childOfType(with, SqlCompositeElementTypes.SQL_WITH_CLAUSE);
        if (withClause == null) return null;

        String cteName = cteNameForAlias(with, qualifier);
        if (cteName == null) return null;

        for (PsiElement definition : withClause.getChildren()) {
            if (definition.getNode().getElementType()
                    != SqlCompositeElementTypes.SQL_NAMED_QUERY_DEFINITION) {
                continue;
            }
            PsiElement name = childOfType(definition, SqlCompositeElementTypes.SQL_IDENTIFIER);
            if (name == null || !unquoted(name.getText()).equalsIgnoreCase(cteName)) continue;
            PsiElement query = childOfType(definition, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION);
            if (query == null) return null;
            PsiElement clause = childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
            return clause == null ? null : itemNamed(clause, columnName);
        }
        return null;
    }

    /**
     * The table an alias is bound to in the body of the with-expression, written
     * {@code customer_orders AS co}. Only an alias over a table reference is considered: an
     * aliased select item has the same shape and must never be read as a source of rows.
     */
    private static @Nullable String cteNameForAlias(@NotNull PsiElement withExpression,
                                                    @NotNull String alias) {
        PsiElement body = childOfType(withExpression, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION);
        if (body == null) return null;
        for (PsiElement candidate : PsiTreeUtil.findChildrenOfType(body, PsiElement.class)) {
            if (candidate.getNode().getElementType() != SqlCompositeElementTypes.SQL_AS_EXPRESSION) {
                continue;
            }
            PsiElement table = childOfType(candidate, SqlCompositeElementTypes.SQL_TABLE_REFERENCE);
            if (table == null) continue;
            PsiElement bound = lastChildOfType(candidate, SqlCompositeElementTypes.SQL_IDENTIFIER);
            if (bound == null || !unquoted(bound.getText()).equalsIgnoreCase(alias)) continue;
            return unquoted(table.getText());
        }
        return null;
    }

    private static @Nullable PsiElement itemNamed(@NotNull PsiElement selectClause,
                                                  @NotNull String columnName) {
        for (PsiElement item : selectClause.getChildren()) {
            IElementType type = item.getNode().getElementType();
            if (type != SqlCompositeElementTypes.SQL_AS_EXPRESSION
                    && type != SqlCompositeElementTypes.SQL_COLUMN_REFERENCE) {
                continue;
            }
            PsiElement name = lastChildOfType(item, SqlCompositeElementTypes.SQL_IDENTIFIER);
            if (name != null && unquoted(name.getText()).equalsIgnoreCase(columnName)) return name;
        }
        return null;
    }

    private static @Nullable PsiElement enclosing(@NotNull PsiElement element,
                                                  @NotNull IElementType type) {
        for (PsiElement current = element; current != null; current = current.getParent()) {
            if (current.getNode() != null && current.getNode().getElementType() == type) {
                return current;
            }
        }
        return null;
    }

    private static @Nullable PsiElement childOfType(@NotNull PsiElement parent,
                                                    @NotNull IElementType type) {
        for (PsiElement child : parent.getChildren()) {
            if (child.getNode().getElementType() == type) return child;
        }
        return null;
    }

    private static @Nullable PsiElement lastChildOfType(@NotNull PsiElement parent,
                                                        @NotNull IElementType type) {
        PsiElement last = null;
        for (PsiElement child : parent.getChildren()) {
            if (child.getNode().getElementType() == type) last = child;
        }
        return last;
    }

    private static @NotNull String unquoted(@NotNull String text) {
        return text.replace("`", "");
    }
}
