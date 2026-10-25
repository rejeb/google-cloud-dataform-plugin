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
package io.github.rejeb.dataform.language.columns.analysis;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.sql.psi.SqlExpression;
import com.intellij.sql.psi.SqlReferenceExpression;
import com.intellij.util.containers.ContainerUtil;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.sql.psi.SqlCompositeElementTypes.*;

/**
 * Walks a parsed BigQuery statement. Nodes are recognised by their element type, which the SQL
 * plugin publishes, rather than by the name of their implementation class, which it does not; a
 * reference is recognised by the PSI interface every kind of reference implements.
 */
final class SqlPsiNavigator {

    static final Predicate<PsiElement> WITH_QUERY = ofType(SQL_WITH_QUERY_EXPRESSION);
    static final Predicate<PsiElement> UNION = ofType(SQL_UNION_EXPRESSION);
    static final Predicate<PsiElement> QUERY = ofType(SQL_QUERY_EXPRESSION);
    static final Predicate<PsiElement> SELECT_CLAUSE = ofType(SQL_SELECT_CLAUSE);
    static final Predicate<PsiElement> REFERENCE = element -> element instanceof SqlReferenceExpression;
    static final Predicate<PsiElement> IDENTIFIER = ofType(SQL_IDENTIFIER);
    static final Predicate<PsiElement> FUNCTION_CALL = ofType(SQL_FUNCTION_CALL);
    static final Predicate<PsiElement> PARENTHESIZED = ofType(SQL_PARENTHESIZED_QUERY_EXPRESSION);
    static final Predicate<PsiElement> AS_EXPRESSION = ofType(SQL_AS_EXPRESSION);
    static final Predicate<PsiElement> FROM_CLAUSE = ofType(SQL_FROM_CLAUSE);
    static final Predicate<PsiElement> CLAUSE = ofType(SQL_CLAUSE, SQL_AS_QUERY_CLAUSE);
    static final Predicate<PsiElement> PIVOTED_QUERY = ofType(SQL_PIVOTED_QUERY_EXPRESSION);
    static final Predicate<PsiElement> STRUCT_EXPRESSION = ofType(SQL_PARENTHESIZED_EXPRESSION);

    private static final TokenSet NOT_EXPRESSIONS =
            TokenSet.create(SQL_ARRAY_LITERAL, SQL_NAMED_QUERY_DEFINITION, SQL_TABLE_COLUMNS_LIST);

    private SqlPsiNavigator() {
    }

    static @NotNull List<PsiElement> expressionChildren(@NotNull PsiElement element) {
        return ContainerUtil.filter(element.getChildren(), SqlPsiNavigator::isExpression);
    }

    static boolean isExpression(@NotNull PsiElement element) {
        return element instanceof SqlExpression && !NOT_EXPRESSIONS.contains(element.getNode().getElementType());
    }

    /**
     * The test recognising the nodes of the given element types.
     */
    static @NotNull Predicate<PsiElement> ofType(@NotNull IElementType... types) {
        TokenSet set = TokenSet.create(types);
        return element -> set.contains(PsiUtilCore.getElementType(element));
    }

    static @NotNull List<PsiElement> topLevelColumnRefs(@NotNull PsiElement expression) {
        List<PsiElement> refs = new ArrayList<>();
        collectColumnRefs(expression, refs);
        return refs;
    }

    /**
     * Collects column references from an expression, skipping function names (callees) and
     * digging into their arguments, so {@code SAFE_CAST(x AS INT64)} yields {@code x}, not
     * {@code SAFE_CAST}. A plain column reference is added without descending into it (its
     * qualifier is part of the same reference). A nested subquery contributes only the columns of
     * its own SELECT list, never the tables of its FROM clause, which are not columns at all.
     */
    static void collectColumnRefs(@NotNull PsiElement element, @NotNull List<PsiElement> refs) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, QUERY) || isType(child, WITH_QUERY)) {
                PsiElement selectClause = directChild(child, SELECT_CLAUSE);
                if (selectClause != null) collectColumnRefs(selectClause, refs);
            } else if (isType(child, REFERENCE)) {
                if (isFunctionCallee(child)) {
                    collectColumnRefs(child, refs);
                } else if (!isStar(child.getText())) {
                    refs.add(child);
                }
            } else {
                collectColumnRefs(child, refs);
            }
        }
    }

    static boolean isFunctionCallee(@NotNull PsiElement reference) {
        PsiElement parent = reference.getParent();
        return isType(parent, FUNCTION_CALL);
    }

    static @Nullable String qualifier(@NotNull PsiElement reference) {
        PsiElement child = directChild(reference, REFERENCE);
        return child != null ? child.getText() : null;
    }

    static @Nullable String lastIdentifier(@NotNull PsiElement element) {
        PsiElement identifier = SqlPsiParts.lastIdentifier(element);
        return identifier != null ? identifier.getText() : null;
    }

    static @Nullable PsiElement firstExpression(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (isExpression(child)) return child;
        }
        return null;
    }

    /**
     * The SELECT queries a scope body is made of. A union contributes every branch, and a branch
     * may itself be parenthesized or a nested union, so branches are expanded recursively rather
     * than taken as direct children only.
     */
    static @NotNull List<PsiElement> expandQueries(@Nullable PsiElement expression) {
        List<PsiElement> queries = new ArrayList<>();
        if (expression == null) return queries;
        if (isType(expression, UNION)) {
            for (PsiElement child : expression.getChildren()) {
                if (isType(child, QUERY) || isType(child, UNION) || isType(child, PARENTHESIZED)) {
                    queries.addAll(expandQueries(child));
                }
            }
        } else if (isType(expression, QUERY)) {
            queries.add(expression);
        } else {
            PsiElement inner = firstQueryOrUnion(expression);
            if (inner != null) queries.addAll(expandQueries(inner));
        }
        return queries;
    }

    /** The query, union or WITH expression nested directly in a parenthesized scope. */
    static @Nullable PsiElement firstQueryScope(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, QUERY) || isType(child, UNION) || isType(child, WITH_QUERY)) return child;
        }
        return null;
    }

    static @Nullable PsiElement firstQueryOrUnion(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, QUERY) || isType(child, UNION)) return child;
        }
        return null;
    }

    static @Nullable PsiElement firstComposite(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (!(child.getNode() instanceof LeafElement)) return child;
        }
        return null;
    }

    static @Nullable PsiElement directChild(@NotNull PsiElement element, @NotNull Predicate<PsiElement> kind) {
        return ContainerUtil.find(element.getChildren(), child -> isType(child, kind));
    }

    static @NotNull List<PsiElement> directChildren(@NotNull PsiElement element, @NotNull Predicate<PsiElement> kind) {
        return ContainerUtil.filter(element.getChildren(), child -> isType(child, kind));
    }

    static @Nullable PsiElement findDescendant(@NotNull PsiElement root, @NotNull Predicate<PsiElement> kind) {
        for (PsiElement child : root.getChildren()) {
            if (isType(child, kind)) return child;
            PsiElement found = findDescendant(child, kind);
            if (found != null) return found;
        }
        return null;
    }

    static boolean isType(@Nullable PsiElement element, @NotNull Predicate<PsiElement> kind) {
        return element != null && kind.test(element);
    }

    static boolean isStar(@NotNull String text) {
        return SqlPsiParts.isStarText(text);
    }
}
