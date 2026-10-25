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
package io.github.rejeb.dataform.language.columns.origin;

import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.psi.SqlxSqlBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Locates the element of a SQLX file that declares an output column of the table the file builds.
 *
 * <p>A column of a Dataform table is produced by the select list of the main query, so only that
 * list declares it. Names appearing anywhere else &mdash; a struct field of a {@code FROM UNNEST},
 * a column of a CTE, an operations block &mdash; are inputs of the query, not the declaration.</p>
 *
 * <p>A query that selects a star names no column at all, and every column it builds is declared by
 * that star.</p>
 */
public final class SqlxOutputColumnLocator {

    private SqlxOutputColumnLocator() {
    }

    /**
     * The element naming {@code columnName} in the select list of the file's main query, or
     * {@code null} when the file declares no such column.
     */
    public static @Nullable PsiElement findOutputColumn(@NotNull PsiFile file,
                                                        @NotNull String columnName) {
        if (!(file instanceof SqlxFile)) return null;
        PsiElement selectClause = mainSelectClause(file);
        if (selectClause == null) return null;
        for (PsiElement item : selectClause.getChildren()) {
            PsiElement name = outputNameOf(item);
            if (name != null && identifierMatches(name, columnName)) return name;
        }
        return starOf(selectClause);
    }

    /**
     * The star of a select list, which declares every column of what the query reads.
     *
     * <p>A query written {@code SELECT *} names none of its columns, so a column of the table it
     * builds has no line of its own to point at. The star is that line: it is where the column
     * enters the query, and it is what a reader has to look at to understand where it came
     * from.</p>
     */
    private static @Nullable PsiElement starOf(@NotNull PsiElement selectClause) {
        for (PsiElement item : selectClause.getChildren()) {
            if (item.getNode() == null) continue;
            IElementType type = item.getNode().getElementType();
            if (type != SqlCompositeElementTypes.SQL_COLUMN_REFERENCE
                    && type != SqlCompositeElementTypes.SQL_REFERENCE) {
                continue;
            }
            if (SqlPsiParts.isStar(item)) return item;
        }
        return null;
    }

    /**
     * The column named by {@code element} when it is an item of the select list of the file's main
     * query, or {@code null} when the element declares no output column.
     */
    public static @Nullable String declaredColumnName(@NotNull PsiFile file,
                                                      @NotNull PsiElement element) {
        if (!(file instanceof SqlxFile)) return null;
        PsiElement selectClause = selectClauseAround(element);
        if (selectClause == null) return null;
        for (PsiElement item : selectClause.getChildren()) {
            if (item != element) continue;
            PsiElement name = outputNameOf(item);
            return name == null ? null : SqlPsiParts.unquoted(name.getText());
        }
        return null;
    }

    /**
     * The select clause of the main query, reached from an element already inside it.
     *
     * <p>Resolution asks this question for every column reference, and asking the file would build
     * the injected documents of each of its blocks to find them. Building injection inside a
     * resolve re-enters resolution, which is enough to make an unrelated one return a different
     * number of results on a second run. The element carries its own injected file, so the walk
     * starts there and the host block is only checked for what kind it is.</p>
     */
    private static @Nullable PsiElement selectClauseAround(@NotNull PsiElement element) {
        PsiFile injected = element.getContainingFile();
        if (injected == null) return null;
        PsiElement host = InjectedLanguageManager.getInstance(element.getProject())
                .getInjectionHost(element);
        if (!(host instanceof SqlxSqlBlock)
                || host.getNode() == null
                || host.getNode().getElementType() != SharedTokenTypes.SQL_CONTENT) {
            return null;
        }
        return mainSelectClauseOf(injected);
    }

    /**
     * The select clause producing the rows of the main query of an injected SQL file, walking
     * through WITH, set operations and parentheses; {@code null} when there is none.
     */
    public static @Nullable PsiElement mainSelectClauseOf(@NotNull PsiFile injected) {
        PsiElement statement = statementOf(injected);
        return statement == null ? null : selectClauseOf(statement);
    }

    /**
     * The query producing the rows of the table a SQLX file builds, or {@code null} when the file
     * holds none.
     */
    public static @Nullable PsiElement mainQuery(@NotNull PsiFile file) {
        for (PsiFile injected : InjectedFiles.mainSql(file)) {
            PsiElement statement = statementOf(injected);
            if (statement == null) continue;
            PsiElement query = rowProducingQuery(statement);
            if (query != null && SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE) != null) {
                return query;
            }
        }
        return null;
    }

    private static @Nullable PsiElement mainSelectClause(@NotNull PsiFile file) {
        PsiElement query = mainQuery(file);
        return query == null ? null : SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
    }

    /**
     * The select clause naming the rows a statement produces.
     *
     * <p>The query producing them is not always held by the statement itself. A {@code WITH}
     * statement holds it after its CTEs, a set operation holds one per branch, and a query may be
     * written inside parentheses — and these nest, as a {@code WITH} whose main query is a
     * {@code UNION} does. Each of those only wraps the query, so the walk goes through them.</p>
     *
     * <p>The first query found is the one that names the columns: the branches of a set operation
     * all carry the same number of columns and BigQuery takes their names from the first. The CTE
     * queries are inside the with-clause, which is not walked, so they are never mistaken for the
     * query the table is built from.</p>
     */
    private static @Nullable PsiElement selectClauseOf(@NotNull PsiElement statement) {
        PsiElement query = rowProducingQuery(statement);
        return query == null
                ? null
                : SqlPsiParts.childOfType(query, SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
    }

    /**
     * The statement producing the rows of the table: the select of a table action, or the
     * {@code CREATE TABLE ... AS} of an operation with output.
     */
    private static @Nullable PsiElement statementOf(@NotNull PsiFile injected) {
        PsiElement select = SqlPsiParts.childOfType(injected, SqlCompositeElementTypes.SQL_SELECT_STATEMENT);
        return select != null ? select : SqlPsiParts.childOfType(injected, SqlCompositeElementTypes.SQL_CREATE_TABLE_STATEMENT);
    }

    /** Kinds that only wrap the query producing the rows, in the order they nest. */
    private static final IElementType[] QUERY_WRAPPERS = {
            SqlCompositeElementTypes.SQL_WITH_QUERY_EXPRESSION,
            SqlCompositeElementTypes.SQL_UNION_EXPRESSION,
            SqlCompositeElementTypes.SQL_PARENTHESIZED_QUERY_EXPRESSION,
            SqlCompositeElementTypes.SQL_AS_QUERY_CLAUSE
    };

    /**
     * The query producing the rows of a statement or of a query wrapper, walking through WITH,
     * set operations and parentheses; {@code null} when there is none.
     */
    public static @Nullable PsiElement rowProducingQuery(@NotNull PsiElement element) {
        PsiElement query = SqlPsiParts.childOfType(element,
                SqlCompositeElementTypes.SQL_QUERY_EXPRESSION);
        if (query != null) return query;
        for (IElementType wrapper : QUERY_WRAPPERS) {
            PsiElement wrapped = SqlPsiParts.childOfType(element, wrapper);
            if (wrapped == null) continue;
            PsiElement found = rowProducingQuery(wrapped);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * The element naming the column a select-list item produces: the alias of an {@code AS}
     * expression, or the last identifier of a column reference. Any other item, a star for
     * instance, names no single column.
     */
    public static @Nullable PsiElement outputNameOf(@NotNull PsiElement item) {
        IElementType type = item.getNode().getElementType();
        if (type != SqlCompositeElementTypes.SQL_AS_EXPRESSION
                && type != SqlCompositeElementTypes.SQL_COLUMN_REFERENCE) {
            return null;
        }
        return SqlPsiParts.lastIdentifier(item);
    }

    private static boolean identifierMatches(@NotNull PsiElement identifier, @NotNull String name) {
        return SqlPsiParts.unquoted(identifier.getText()).equalsIgnoreCase(name);
    }
}
