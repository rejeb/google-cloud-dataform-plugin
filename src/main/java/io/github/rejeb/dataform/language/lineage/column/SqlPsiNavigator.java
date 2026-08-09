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
package io.github.rejeb.dataform.language.lineage.column;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a parsed BigQuery statement. The SQL plugin exposes no stable interface for its PSI, so
 * nodes are recognised by the simple name of their implementation class; keeping that convention in
 * one place stops it from leaking through the analysis.
 */
final class SqlPsiNavigator {

    static final String WITH_QUERY = "SqlWithQueryExpressionImpl";
    static final String UNION = "BigQueryUnionExpressionImpl";
    static final String QUERY = "SqlQueryExpressionImpl";
    static final String SELECT_CLAUSE = "SqlSelectClauseImpl";
    static final String REFERENCE = "SqlReferenceExpressionImpl";
    static final String IDENTIFIER = "SqlIdentifierImpl";
    static final String FUNCTION_CALL = "SqlFunctionCallExpressionImpl";
    static final String PARENTHESIZED = "SqlParenthesizedExpressionImpl";

    private static final String TOKEN = "SqlTokenElement";
    private static final String EXPRESSION_SUFFIX = "Expression";
    private static final String EXPRESSION_IMPL_SUFFIX = "ExpressionImpl";

    private SqlPsiNavigator() {
    }

    static @NotNull List<PsiElement> selectItems(@NotNull PsiElement selectClause) {
        return expressionChildren(selectClause);
    }

    static @NotNull List<PsiElement> expressionChildren(@NotNull PsiElement element) {
        List<PsiElement> items = new ArrayList<>();
        for (PsiElement child : element.getChildren()) {
            if (isExpression(child)) items.add(child);
        }
        return items;
    }

    static boolean isExpression(@NotNull PsiElement element) {
        String name = element.getClass().getSimpleName();
        return name.endsWith(EXPRESSION_SUFFIX) || name.endsWith(EXPRESSION_IMPL_SUFFIX);
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
        return parent != null && parent.getClass().getSimpleName().equals(FUNCTION_CALL);
    }

    static @Nullable String qualifier(@NotNull PsiElement reference) {
        PsiElement child = firstChild(reference, REFERENCE);
        return child != null ? child.getText() : null;
    }

    static @Nullable String lastIdentifier(@NotNull PsiElement element) {
        String result = null;
        for (PsiElement child : element.getChildren()) {
            if (isType(child, IDENTIFIER)) result = child.getText();
        }
        return result;
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
            if (!child.getClass().getSimpleName().equals(TOKEN)) return child;
        }
        return null;
    }

    static @Nullable PsiElement directChild(@NotNull PsiElement element, @NotNull String simpleName) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, simpleName)) return child;
        }
        return null;
    }

    static @NotNull List<PsiElement> directChildren(@NotNull PsiElement element, @NotNull String simpleName) {
        List<PsiElement> children = new ArrayList<>();
        for (PsiElement child : element.getChildren()) {
            if (isType(child, simpleName)) children.add(child);
        }
        return children;
    }

    static @Nullable PsiElement firstChild(@NotNull PsiElement element, @NotNull String simpleName) {
        return directChild(element, simpleName);
    }

    static @Nullable PsiElement findDescendant(@NotNull PsiElement root, @NotNull String simpleName) {
        for (PsiElement child : root.getChildren()) {
            if (isType(child, simpleName)) return child;
            PsiElement found = findDescendant(child, simpleName);
            if (found != null) return found;
        }
        return null;
    }

    static boolean isType(@Nullable PsiElement element, @NotNull String simpleName) {
        return element != null && element.getClass().getSimpleName().equals(simpleName);
    }

    static boolean isStar(@NotNull String text) {
        return text.equals("*") || text.endsWith(".*");
    }
}
