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
package io.github.rejeb.dataform.language.columns.usages;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.sql.psi.SqlAsExpression;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.SqlPsiParts;
import io.github.rejeb.dataform.language.columns.origin.SqlxColumnAtCaret;
import io.github.rejeb.dataform.language.columns.origin.StructColumnPathResolver;
import io.github.rejeb.dataform.language.columns.origin.StructColumnPaths;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import io.github.rejeb.dataform.language.unittest.SqlxUnitTests;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAlias;
import io.github.rejeb.dataform.language.unittest.columns.TestColumnAliases;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What the column window was opened on.
 *
 * <p>Two shapes reach it. A column of a Dataform table, whose readers live across the project and
 * are found through the schema; and a name a query gives a value of its own — {@code nationality as
 * country} inside a CTE — whose readers live in the query that named it. Both are columns to the
 * person reading the file, so both open the same window.</p>
 *
 * <p>A third shape reaches it: a field inside a struct column, at any depth. The schema holds the
 * column rather than the field, so the field is named by the path walked into it, and that path is
 * what its rows are searched for. The path is carried whether the caret sits on a read of the field
 * or on the alias declaring it — a reader asks the same question from either end, and nothing
 * references the alias of a field, so a search over references answers only one of them.</p>
 *
 * @param searchTargets   the elements whose references make up the usage rows
 * @param name            the column name, as written
 * @param declarations    the columns this one is built from, each in the file declaring it
 * @param bigQuerySources the columns this one is built from that belong to a source, a BigQuery
 *                        table the project reads but does not build, and that no line declares
 * @param structPath      the field inside a struct column, {@code null} for a column of a table
 */
public record ColumnWindowTarget(@NotNull List<PsiElement> searchTargets,
                                 @NotNull String name,
                                 @NotNull List<PsiElement> declarations,
                                 @NotNull List<ColumnRef> bigQuerySources,
                                 @Nullable StructColumnPath structPath) {

    /**
     * The column window's subject at an offset of a SQLX file, or {@code null} when the offset
     * holds no column at all and navigation should keep its usual behaviour.
     */
    public static @Nullable ColumnWindowTarget at(@Nullable PsiFile hostFile, int offset) {
        if (hostFile == null || !hostFile.getName().endsWith(".sqlx")) return null;
        PsiElement injected = InjectedLanguageManager.getInstance(hostFile.getProject())
                .findInjectedElementAt(hostFile, offset);
        if (injected == null) return null;

        if (SqlxUnitTests.isUnitTestFile(hostFile)) {
            ColumnWindowTarget test = fromTestAlias(injected);
            if (test != null) return test;
        }
        ColumnWindowTarget alias = fromAlias(injected);
        if (alias != null) return alias;
        ColumnWindowTarget read = fromReference(injected);
        return read != null ? read : fromStructField(injected);
    }

    /**
     * A column named by an {@code AS} alias. What later queries read is the alias, and what the
     * column is built from is every column the renamed expression reads — one for a plain rename,
     * several for {@code CONCAT(first, last) AS full_name}, and the columns handed as strings to a
     * helper for {@code ${helper("goals")} AS top_scorers}.
     */
    private static @Nullable ColumnWindowTarget fromAlias(@NotNull PsiElement token) {
        PsiElement identifier = token.getParent();
        if (!SqlPsiParts.isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)) return null;
        PsiElement expression = identifier.getParent();
        if (!SqlPsiParts.isType(expression, SqlCompositeElementTypes.SQL_AS_EXPRESSION)
                || SqlPsiParts.lastIdentifier(expression) != identifier) {
            return null;
        }
        if (SqlxColumnAtCaret.sqlxFileOf(identifier) == null) return null;

        ColumnOriginService origins = ColumnOriginService.getInstance(identifier.getProject());
        List<PsiElement> declarations = new ArrayList<>();
        List<ColumnRef> sources = new ArrayList<>();
        PsiElement renamed = renamedExpression(expression);
        for (PsiElement read : columnReferencesIn(renamed)) {
            PsiElement declaration = declarationOf(read, origins, null, sources);
            if (declaration != null) declarations.add(declaration);
        }
        if (renamed != null) {
            for (DataformDasColumn handed : HelperColumnStrings.columnsHandedTo(renamed, identifier)) {
                PsiElement declaration = declarationOf(handed, origins, sources);
                if (declaration != null) declarations.add(declaration);
            }
        }

        List<PsiElement> searched = new ArrayList<>(List.of(identifier, expression));
        DataformDasColumn output = SqlxColumnAtCaret.declaredColumnOf(expression);
        if (output != null) searched.add(output);
        return new ColumnWindowTarget(searched,
                SqlPsiParts.unquoted(identifier.getText()), distinct(declarations), distinct(sources),
                StructColumnPathResolver.getInstance(identifier.getProject())
                        .declaredPathAt(identifier));
    }

    /** A column read by name, which is declared by whatever it resolves to. */
    /**
     * The column a unit test alias stands for, searched the way a read of it would be. The alias
     * itself is among the searched elements, so the window does not list it as a usage.
     */
    private static @Nullable ColumnWindowTarget fromTestAlias(@NotNull PsiElement token) {
        Project project = token.getProject();
        TestColumnAlias alias = TestColumnAliases.getInstance(project).at(token).orElse(null);
        if (alias == null) return null;
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        ColumnRef column = alias.column();
        if (column.columnName().contains(".")) {
            StructColumnPath path = StructColumnPaths.of(project, column).orElse(null);
            if (path == null) return null;
            PsiElement declaration = origins.declaringElement(path);
            ColumnRef root = origins.reference(path.root());
            List<ColumnRef> sources = declaration == null && root != null && origins.isSource(root)
                    ? List.of(new ColumnRef(root.tableFullName(), path.dottedName()))
                    : List.of();
            return new ColumnWindowTarget(List.of(alias.identifier()), path.leafName(),
                    declaration == null ? List.of() : List.of(declaration), sources, path);
        }
        DataformDasColumn das = origins.dasColumn(column);
        if (das == null) return null;
        List<ColumnRef> sources = new ArrayList<>();
        PsiElement declaration = declarationOf(das, origins, sources);
        return new ColumnWindowTarget(List.of(das, alias.identifier()), das.getName(),
                declaration == null ? List.of() : List.of(declaration), distinct(sources), null);
    }

    private static @Nullable ColumnWindowTarget fromReference(@NotNull PsiElement token) {
        PsiElement reference = SqlxColumnAtCaret.referenceOf(token);
        if (reference == null) return null;
        PsiFile topLevel = SqlxColumnAtCaret.sqlxFileOf(reference);
        if (topLevel == null) return null;

        ColumnOriginService origins = ColumnOriginService.getInstance(reference.getProject());
        ColumnRef declaredRef = origins.declaredColumn(topLevel, reference);
        DataformDasColumn declared = declaredRef == null ? null : origins.dasColumn(declaredRef);
        List<ColumnRef> sources = new ArrayList<>();
        PsiElement declaration = declarationOf(reference, origins, declared, sources);

        List<PsiElement> searched = new ArrayList<>();
        if (declared != null) {
            searched.add(declared);
        } else {
            DataformDasColumn read = SqlxColumnAtCaret.resolvedColumn(reference, null);
            if (read == null) return null;
            searched.add(read);
            DataformDasColumn built = outputColumnBuiltFrom(reference);
            if (built != null) searched.add(built);
        }
        return new ColumnWindowTarget(searched,
                declared != null ? declared.getName() : columnName(reference),
                declaration == null ? List.of() : List.of(declaration), distinct(sources), null);
    }

    /**
     * The output column of the table the file builds that an expression reading a column declares,
     * when the read sits inside an aliased item of the main select list:
     * {@code CAST(TRIM(name) AS STRING) AS name}. The reader of that line asks where the column goes
     * next, which is wherever the output column is read, not only where the column read is.
     */
    private static @Nullable DataformDasColumn outputColumnBuiltFrom(@NotNull PsiElement reference) {
        for (PsiElement parent = reference.getParent();
             parent != null && !(parent instanceof PsiFile);
             parent = parent.getParent()) {
            if (!(parent instanceof SqlAsExpression)) continue;
            DataformDasColumn output = SqlxColumnAtCaret.declaredColumnOf(parent);
            if (output != null) return output;
        }
        return null;
    }

    /**
     * A field inside a struct column, named by the path walked into it.
     *
     * <p>Tried last, so a column of a table keeps answering the way it always has: a plain reference
     * is a path of no fields as much as a struct column is, and only the shapes nothing else claims
     * reach here. What does reach here is every segment of a qualified path — the struct column
     * itself included, which no other shape recognises.</p>
     */
    private static @Nullable ColumnWindowTarget fromStructField(@NotNull PsiElement token) {
        StructColumnPathResolver resolver = StructColumnPathResolver.getInstance(token.getProject());
        StructColumnPath path = resolver.pathAt(token);
        if (path == null) return null;
        if (SqlxColumnAtCaret.sqlxFileOf(token) == null) return null;

        PsiElement read = resolver.segmentAt(token);
        ColumnOriginService origins = ColumnOriginService.getInstance(token.getProject());
        PsiElement declaration = origins.declaringElement(path);
        ColumnRef root = declaration == null ? origins.reference(path.root()) : null;
        List<ColumnRef> sources = root != null && origins.isSource(root)
                ? List.of(new ColumnRef(root.tableFullName(), path.dottedName()))
                : List.of();
        return new ColumnWindowTarget(read == null ? List.of() : List.of(read),
                path.leafName(),
                declaration == null ? List.of() : List.of(declaration),
                sources,
                path);
    }

    /**
     * Where a column read by an expression is declared: the select-list item of the action that
     * builds it when the schema knows it, and otherwise whatever the reference resolves to inside
     * the file, such as the alias of an earlier common table expression. A column of a source is
     * declared by no line of the project: it is added to {@code sources} and has no declaration.
     */
    private static @Nullable PsiElement declarationOf(@NotNull PsiElement reference,
                                                      @NotNull ColumnOriginService origins,
                                                      @Nullable DataformDasColumn exclude,
                                                      @NotNull List<ColumnRef> sources) {
        DataformDasColumn column = SqlxColumnAtCaret.resolvedColumn(reference, exclude);
        if (column != null) {
            ColumnRef source = sourceOf(column, origins);
            if (source != null) {
                sources.add(source);
                return null;
            }
            PsiElement declaring = origins.declaringElement(column);
            if (declaring != null) return declaring;
        }
        PsiElement field = structFieldDeclarationOf(reference, origins);
        if (field != null) return field;
        PsiReference psiReference = reference.getReference();
        if (psiReference == null) return null;
        PsiElement resolved = psiReference.resolve();
        if (resolved instanceof DataformDasColumn) return null;
        return isInAFileOfTheProject(resolved) ? resolved : null;
    }

    /**
     * Where a schema column is declared: the select-list item of the action building it. A column
     * of a source is added to {@code sources} instead and has no declaration.
     */
    private static @Nullable PsiElement declarationOf(@NotNull DataformDasColumn column,
                                                      @NotNull ColumnOriginService origins,
                                                      @NotNull List<ColumnRef> sources) {
        ColumnRef source = sourceOf(column, origins);
        if (source != null) {
            sources.add(source);
            return null;
        }
        return origins.declaringElement(column);
    }

    /** The reference of a schema column when it belongs to a source, {@code null} otherwise. */
    private static @Nullable ColumnRef sourceOf(@NotNull DataformDasColumn column,
                                                @NotNull ColumnOriginService origins) {
        ColumnRef reference = origins.reference(column);
        return reference != null && origins.isSource(reference) ? reference : null;
    }

    /**
     * Where a read of a field inside a struct column is declared.
     *
     * <p>The platform resolves such a read to an element of the column's own type, which belongs to
     * no file of the project, so the field is looked for in the action building the column instead.
     * Without this an alias over a nested field — {@code n.customer.name AS customer_name} — has a
     * declaration that cannot be pointed at, and a window that cannot show one comes up empty.</p>
     */
    private static @Nullable PsiElement structFieldDeclarationOf(@NotNull PsiElement reference,
                                                                 @NotNull ColumnOriginService origins) {
        StructColumnPath path = StructColumnPathResolver.getInstance(reference.getProject())
                .pathAt(reference);
        return path == null || !path.isField() ? null : origins.declaringElement(path);
    }

    /**
     * Whether an element sits in a file the project holds.
     *
     * <p>A type imported to answer a resolve carries PSI of its own that no document backs. A row of
     * the window cannot be built for it, so holding it as a declaration loses the row silently and
     * the column reads as one that resolves to nothing. Better to report no declaration than one
     * nobody can open.</p>
     */
    private static boolean isInAFileOfTheProject(@Nullable PsiElement element) {
        if (element == null) return false;
        PsiFile file = element.getContainingFile();
        return file != null && file.getVirtualFile() != null;
    }

    /** The expression an alias renames, which is everything before its name. */
    private static @Nullable PsiElement renamedExpression(@NotNull PsiElement asExpression) {
        for (PsiElement child : asExpression.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == SqlCompositeElementTypes.SQL_IDENTIFIER) continue;
            return child;
        }
        return null;
    }

    /**
     * Every column an expression reads. A call over several columns declares a column built from
     * all of them, and the window names each.
     */
    private static @NotNull List<PsiElement> columnReferencesIn(@Nullable PsiElement expression) {
        List<PsiElement> found = new ArrayList<>();
        if (expression == null) return found;
        if (SqlPsiParts.isType(expression, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)) {
            found.add(expression);
            return found;
        }
        collectColumnReferences(expression, found);
        return found;
    }

    private static void collectColumnReferences(@NotNull PsiElement element,
                                                @NotNull List<PsiElement> found) {
        for (PsiElement child : element.getChildren()) {
            if (SqlPsiParts.isType(child, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)) {
                found.add(child);
            } else {
                collectColumnReferences(child, found);
            }
        }
    }

    private static @NotNull String columnName(@NotNull PsiElement reference) {
        PsiElement last = SqlPsiParts.lastIdentifier(reference);
        return last == null ? reference.getText() : SqlPsiParts.unquoted(last.getText());
    }

    private static <T> @NotNull List<T> distinct(@NotNull List<T> elements) {
        return List.copyOf(new LinkedHashSet<>(elements));
    }
}
