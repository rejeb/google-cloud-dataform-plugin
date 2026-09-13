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
package io.github.rejeb.dataform.language.highlight;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tells whether the columns a query reads are ones the IDE can name.
 *
 * <p>A SQLX query reads through template holes, and a hole is injected as filler text — a
 * {@code ${ref("customers")}} becomes the table it names, everything else becomes a literal. Where
 * the filler is the table, the columns of the source are the columns of that action and a name that
 * is not among them is worth reporting. Where it is a literal — rows a {@code js} block builds,
 * handed to {@code UNNEST} — the source has columns the IDE cannot see: what they are is decided
 * when Dataform compiles the file. Reporting a name read from such a source says the query is wrong
 * on the strength of text the plugin itself put there.</p>
 */
public final class SqlxQuerySources {

    private SqlxQuerySources() {
    }

    /**
     * Whether every source of the query holding {@code element} is one whose columns can be named.
     * True for a query with no sources at all, which has nothing to hide.
     *
     * <p>The answer depends on the {@code FROM} clause alone, so it is kept on it. Every unresolved
     * name of a file asks this while the file is inspected, and the walk behind it asks the
     * injection manager about each leaf of the clause.</p>
     */
    public static boolean areKnown(@NotNull PsiElement element) {
        PsiElement from = fromClauseOf(element);
        if (from == null) return true;
        return CachedValuesManager.getCachedValue(from, () -> CachedValueProvider.Result.create(
                allSourcesAreKnown(from), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static boolean allSourcesAreKnown(@NotNull PsiElement from) {
        for (PsiElement leaf : PsiTreeUtil.collectElements(from,
                candidate -> candidate.getFirstChild() == null)) {
            if (leaf instanceof PsiWhiteSpace || isPartOfTableReference(leaf, from)) continue;
            if (isGenerated(leaf)) return false;
        }
        return true;
    }

    /**
     * Whether the text of an element was written by the injection rather than by the user, which is
     * what a template hole becomes in the SQL the IDE analyses.
     */
    public static boolean isGenerated(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return false;
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(element.getProject());
        if (manager.getInjectionHost(file) == null) return false;
        return manager.intersectWithAllEditableFragments(file, element.getTextRange())
                .stream()
                .allMatch(TextRange::isEmpty);
    }

    /** The {@code FROM} clause of the innermost query holding the element, if it has one. */
    private static @Nullable PsiElement fromClauseOf(@NotNull PsiElement element) {
        PsiElement query = PsiTreeUtil.findFirstParent(element, false,
                parent -> SqlPsiParts.isType(parent, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION));
        if (query == null) return null;
        PsiElement table = SqlPsiParts.childOfType(query,
                SqlCompositeElementTypes.SQL_TABLE_EXPRESSION);
        return table == null
                ? null
                : SqlPsiParts.childOfType(table, SqlCompositeElementTypes.SQL_FROM_CLAUSE);
    }

    /**
     * Whether the element spells out a table the query reads. Filler text standing for a table is
     * the one kind the IDE can look behind: it resolves to the action, whose columns are known.
     */
    private static boolean isPartOfTableReference(@NotNull PsiElement leaf,
                                                  @NotNull PsiElement from) {
        for (PsiElement parent = leaf; parent != null && parent != from;
             parent = parent.getParent()) {
            if (SqlPsiParts.isType(parent, SqlCompositeElementTypes.SQL_TABLE_REFERENCE)) return true;
        }
        return false;
    }
}
