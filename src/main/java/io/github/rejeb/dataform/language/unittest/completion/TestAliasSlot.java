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
package io.github.rejeb.dataform.language.unittest.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.unittest.columns.TestAliasPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record TestAliasSlot(@NotNull List<String> recordPath,
                            @NotNull Set<String> siblingAliases,
                            @NotNull ValueShape shape) {

    public enum ValueShape {
        SCALAR,
        STRUCT,
        ARRAY
    }

    private static final IElementType AS_EXPRESSION = TestAliasPaths.AS_EXPRESSION;
    private static final Pattern ARRAY_VALUE = Pattern.compile("(?is)^(\\[|ARRAY\\b).*");
    private static final Pattern STRUCT_VALUE = Pattern.compile("(?is)^(STRUCT\\b|\\().*");

    /**
     * Returns the slot of the alias typed at the given position of an injected BigQuery fragment,
     * empty when the position is not the alias following an {@code AS}, or when a struct enclosing
     * it has no alias yet, since the record it fills is then unknown.
     */
    @NotNull
    public static Optional<TestAliasSlot> at(@Nullable PsiElement position) {
        if (position == null) {
            return Optional.empty();
        }
        PsiElement previous = PsiTreeUtil.prevVisibleLeaf(position);
        if (previous == null || !"AS".equalsIgnoreCase(previous.getText())) {
            return Optional.empty();
        }
        PsiElement alias = ancestor(position.getParent(), AS_EXPRESSION);
        if (alias == null || !isAliasOf(alias, position)) {
            return Optional.empty();
        }
        return recordPath(alias).map(path -> new TestAliasSlot(path, siblingAliases(alias), shapeOf(alias)));
    }

    /**
     * Tells whether a sibling item already uses the given alias, ignoring case.
     */
    public boolean isUsed(@NotNull String name) {
        return siblingAliases.contains(name.toLowerCase(Locale.ROOT));
    }

    private static boolean isAliasOf(@NotNull PsiElement asExpression, @NotNull PsiElement position) {
        PsiElement identifier = SqlPsiParts.lastIdentifier(asExpression);
        return identifier != null && PsiTreeUtil.isAncestor(identifier, position, false);
    }

    private static Optional<List<String>> recordPath(@NotNull PsiElement alias) {
        return TestAliasPaths.enclosingPath(alias);
    }

    private static Set<String> siblingAliases(@NotNull PsiElement alias) {
        Set<String> names = new HashSet<>();
        PsiElement parent = alias.getParent();
        if (parent == null) {
            return names;
        }
        for (PsiElement child : parent.getChildren()) {
            if (child != alias && isType(child, AS_EXPRESSION)) {
                String name = aliasOf(child);
                if (name != null) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }

    private static ValueShape shapeOf(@NotNull PsiElement alias) {
        PsiElement value = alias.getFirstChild();
        String text = value == null ? "" : value.getText().trim();
        if (ARRAY_VALUE.matcher(text).matches()) {
            return ValueShape.ARRAY;
        }
        if (STRUCT_VALUE.matcher(text).matches()) {
            return ValueShape.STRUCT;
        }
        return ValueShape.SCALAR;
    }

    @Nullable
    private static String aliasOf(@NotNull PsiElement asExpression) {
        return TestAliasPaths.aliasOf(asExpression);
    }

    @Nullable
    private static PsiElement ancestor(@Nullable PsiElement element, @NotNull IElementType type) {
        for (PsiElement current = element; current != null && !(current instanceof PsiFile);
             current = current.getParent()) {
            if (isType(current, type)) {
                return current;
            }
        }
        return null;
    }

    private static boolean isType(@Nullable PsiElement element, @NotNull IElementType type) {
        return TestAliasPaths.isType(element, type);
    }
}
