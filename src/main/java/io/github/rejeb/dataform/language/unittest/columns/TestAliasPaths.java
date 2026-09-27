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
package io.github.rejeb.dataform.language.unittest.columns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public final class TestAliasPaths {

    public static final String AS_EXPRESSION = "BigQueryAsExpressionImpl";
    private static final String STRUCT_EXPRESSION = "BigQueryParenthesizedExpression";
    private static final String SELECT_CLAUSE = "SqlSelectClauseImpl";

    private TestAliasPaths() {
    }

    /**
     * Tells whether an element is an instance of the PSI class of the given simple name.
     */
    public static boolean isType(@Nullable PsiElement element, @NotNull String simpleName) {
        return element != null && element.getClass().getSimpleName().equals(simpleName);
    }

    /**
     * Returns the identifier an {@code AS} expression gives its value, or null when it has none yet.
     */
    @Nullable
    public static PsiElement aliasIdentifier(@NotNull PsiElement asExpression) {
        return SqlPsiParts.lastIdentifier(asExpression);
    }

    /**
     * Returns the name an {@code AS} expression gives its value, unquoted, or null.
     */
    @Nullable
    public static String aliasOf(@NotNull PsiElement asExpression) {
        PsiElement identifier = aliasIdentifier(asExpression);
        return identifier == null ? null : SqlPsiParts.unquoted(identifier.getText());
    }

    /**
     * Tells whether an {@code AS} expression names a column: an item of a select list or a field of
     * a {@code STRUCT}.
     */
    public static boolean isColumnAlias(@NotNull PsiElement asExpression) {
        PsiElement parent = asExpression.getParent();
        return isType(parent, SELECT_CLAUSE) || isStructConstructor(parent);
    }

    /**
     * Returns the aliases of the structs enclosing an alias, outermost first, empty when one of them
     * has no alias yet. Arrays add nothing to the path.
     */
    @NotNull
    public static Optional<List<String>> enclosingPath(@NotNull PsiElement asExpression) {
        Deque<String> path = new ArrayDeque<>();
        boolean pending = false;
        for (PsiElement current = asExpression.getParent();
             current != null && !(current instanceof PsiFile);
             current = current.getParent()) {
            if (isStructConstructor(current)) {
                pending = true;
            } else if (pending && isType(current, AS_EXPRESSION)) {
                String name = aliasOf(current);
                if (name == null) {
                    return Optional.empty();
                }
                path.addFirst(name);
                pending = false;
            }
        }
        return pending ? Optional.empty() : Optional.of(List.copyOf(path));
    }

    /**
     * Returns the dotted path of the column an alias names, its own name last, empty when the alias
     * names no column or when an enclosing struct has no alias yet.
     */
    @NotNull
    public static Optional<List<String>> pathOf(@NotNull PsiElement asExpression) {
        String own = aliasOf(asExpression);
        if (own == null || !isColumnAlias(asExpression)) {
            return Optional.empty();
        }
        return enclosingPath(asExpression).map(path -> {
            List<String> full = new ArrayList<>(path);
            full.add(own);
            return List.copyOf(full);
        });
    }

    /**
     * Tells whether an element is a {@code STRUCT} constructor: a parenthesized expression holding
     * named fields.
     */
    public static boolean isStructConstructor(@Nullable PsiElement element) {
        if (!isType(element, STRUCT_EXPRESSION)) {
            return false;
        }
        for (PsiElement child : element.getChildren()) {
            if (isType(child, AS_EXPRESSION)) {
                return true;
            }
        }
        return false;
    }
}
