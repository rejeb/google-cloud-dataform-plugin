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
package io.github.rejeb.dataform.language.schema.sql.usages;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret;
import io.github.rejeb.dataform.language.schema.sql.StructColumnPathResolver;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
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
 * @param searchTargets the elements whose references make up the usage rows
 * @param name          the column name, as written
 * @param declarations  the columns this one is built from, each in the file declaring it
 * @param structPath    the field inside a struct column, {@code null} for a column of a table
 */
public record ColumnWindowTarget(@NotNull List<PsiElement> searchTargets,
                                 @NotNull String name,
                                 @NotNull List<PsiElement> declarations,
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

        ColumnWindowTarget alias = fromAlias(injected);
        if (alias != null) return alias;
        ColumnWindowTarget read = fromReference(injected);
        return read != null ? read : fromStructField(injected);
    }

    /**
     * A column named by an {@code AS} alias. What later queries read is the alias, and what the
     * column is built from is every column the renamed expression reads — one for a plain rename,
     * several for {@code CONCAT(first, last) AS full_name}.
     */
    private static @Nullable ColumnWindowTarget fromAlias(@NotNull PsiElement token) {
        PsiElement identifier = token.getParent();
        if (!isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)) return null;
        PsiElement expression = identifier.getParent();
        if (!isType(expression, SqlCompositeElementTypes.SQL_AS_EXPRESSION)
                || lastIdentifier(expression) != identifier) {
            return null;
        }
        if (SqlxColumnAtCaret.sqlxFileOf(identifier) == null) return null;

        ColumnOriginService origins = ColumnOriginService.getInstance(identifier.getProject());
        List<PsiElement> declarations = new ArrayList<>();
        for (PsiElement read : columnReferencesIn(renamedExpression(expression))) {
            PsiElement declaration = declarationOf(read, origins, null);
            if (declaration != null) declarations.add(declaration);
        }

        List<PsiElement> searched = new ArrayList<>(List.of(identifier, expression));
        DataformDasColumn output = SqlxColumnAtCaret.declaredColumnOf(expression);
        if (output != null) searched.add(output);
        return new ColumnWindowTarget(searched,
                identifier.getText().replace("`", ""), distinct(declarations),
                StructColumnPathResolver.getInstance(identifier.getProject())
                        .declaredPathAt(identifier));
    }

    /** A column read by name, which is declared by whatever it resolves to. */
    private static @Nullable ColumnWindowTarget fromReference(@NotNull PsiElement token) {
        PsiElement reference = SqlxColumnAtCaret.referenceOf(token);
        if (reference == null) return null;
        PsiFile topLevel = SqlxColumnAtCaret.sqlxFileOf(reference);
        if (topLevel == null) return null;

        ColumnOriginService origins = ColumnOriginService.getInstance(reference.getProject());
        ColumnRef declaredRef = origins.declaredColumn(topLevel, reference);
        DataformDasColumn declared = declaredRef == null ? null : origins.dasColumn(declaredRef);
        PsiElement declaration = declarationOf(reference, origins, declared);

        List<PsiElement> searched = new ArrayList<>();
        if (declared != null) {
            searched.add(declared);
        } else {
            DataformDasColumn read = readColumn(reference, null);
            if (read == null) return null;
            searched.add(read);
        }
        return new ColumnWindowTarget(searched,
                declared != null ? declared.getName() : columnName(reference),
                declaration == null ? List.of() : List.of(declaration), null);
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
        PsiElement declaration = ColumnOriginService.getInstance(token.getProject())
                .declaringElement(path);
        return new ColumnWindowTarget(read == null ? List.of() : List.of(read),
                path.leafName(),
                declaration == null ? List.of() : List.of(declaration),
                path);
    }

    /**
     * Where a column read by an expression is declared: the select-list item of the action that
     * builds it when the schema knows it, and otherwise whatever the reference resolves to inside
     * the file, such as the alias of an earlier common table expression.
     */
    private static @Nullable PsiElement declarationOf(@NotNull PsiElement reference,
                                                      @NotNull ColumnOriginService origins,
                                                      @Nullable DataformDasColumn exclude) {
        DataformDasColumn column = readColumn(reference, exclude);
        if (column != null) {
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

    /** The schema column an expression reads, which is never the one it declares. */
    private static @Nullable DataformDasColumn readColumn(@NotNull PsiElement reference,
                                                          @Nullable DataformDasColumn exclude) {
        PsiReference psiReference = reference.getReference();
        if (psiReference == null) return null;
        if (psiReference instanceof PsiPolyVariantReference poly) {
            for (ResolveResult result : poly.multiResolve(false)) {
                if (!(result.getElement() instanceof DataformDasColumn column)) continue;
                if (exclude == null || !column.isEquivalentTo(exclude)) return column;
            }
            return null;
        }
        PsiElement target = psiReference.resolve();
        if (!(target instanceof DataformDasColumn column)) return null;
        return exclude != null && column.isEquivalentTo(exclude) ? null : column;
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
        if (isType(expression, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)) {
            found.add(expression);
            return found;
        }
        collectColumnReferences(expression, found);
        return found;
    }

    private static void collectColumnReferences(@NotNull PsiElement element,
                                                @NotNull List<PsiElement> found) {
        for (PsiElement child : element.getChildren()) {
            if (isType(child, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)) {
                found.add(child);
            } else {
                collectColumnReferences(child, found);
            }
        }
    }

    private static @NotNull String columnName(@NotNull PsiElement reference) {
        PsiElement last = lastIdentifier(reference);
        return last == null ? reference.getText() : last.getText().replace("`", "");
    }

    private static @NotNull List<PsiElement> distinct(@NotNull List<PsiElement> elements) {
        return List.copyOf(new LinkedHashSet<>(elements));
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
}
