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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StructColumnPathResolverImpl implements StructColumnPathResolver {

    private static final String UNNEST = "UNNEST";

    /**
     * How many times a path may be followed through an {@code UNNEST}. An array of structs holding
     * an array of its own is unnested twice, and a reader who writes more levels than this wanted
     * something other than a column.
     */
    private static final int MAX_UNNEST_DEPTH = 8;

    @Override
    public @Nullable StructColumnPath pathAt(@NotNull PsiElement token) {
        return pathAt(token, 0);
    }

    private @Nullable StructColumnPath pathAt(@NotNull PsiElement token, int depth) {
        PsiElement segment = segmentAt(token);
        if (segment == null) return null;

        List<PsiElement> chain = chainFrom(segment);
        for (int root = 0; root < chain.size(); root++) {
            DataformDasColumn column = columnOf(chain.get(root));
            if (column == null) continue;
            List<String> names = namesAbove(chain, root);
            if (names == null) return null;
            List<ColumnInfo> trail = fieldsOf(column.getColumnInfo(), names);
            return trail == null ? null : new StructColumnPath(column, trail);
        }
        return throughUnnest(chain, depth);
    }

    /**
     * The path a read off an {@code UNNEST} alias names.
     *
     * <p>{@code UNNEST(n.items) AS item} gives the rows of an array a name, and {@code item.code}
     * reads a field of the array's element. The alias resolves to nothing at all — the platform does
     * not carry the element type of an array through it — so it is found where it is written, in the
     * from-clause of the query doing the reading, and the array it unnests is read as a path of its
     * own. What the caret sits on is then that path with the remaining segments walked into it.</p>
     */
    private @Nullable StructColumnPath throughUnnest(@NotNull List<PsiElement> chain, int depth) {
        if (depth >= MAX_UNNEST_DEPTH || chain.isEmpty()) return null;
        PsiElement alias = chain.getLast();
        PsiElement aliasName = SqlPsiParts.lastIdentifier(alias);
        if (aliasName == null) return null;

        PsiElement array = unnestedArrayOf(alias, SqlPsiParts.unquoted(aliasName.getText()));
        if (array == null) return null;
        PsiElement arrayName = SqlPsiParts.lastIdentifier(array);
        if (arrayName == null) return null;

        StructColumnPath unnested = pathAt(arrayName, depth + 1);
        if (unnested == null) return null;
        List<String> names = namesAbove(chain, chain.size() - 1);
        if (names == null) return null;
        List<ColumnInfo> fields = fieldsOf(unnested.leaf(), names);
        if (fields == null) return null;

        List<ColumnInfo> trail = new ArrayList<>(unnested.trail());
        trail.addAll(fields);
        return new StructColumnPath(unnested.root(), trail);
    }

    /**
     * The array an alias of the enclosing queries unnests, or {@code null} when no query in scope
     * names one. The queries are walked outwards, because a read may sit in a subquery of the one
     * whose from-clause holds the alias.
     */
    private static @Nullable PsiElement unnestedArrayOf(@NotNull PsiElement place,
                                                        @NotNull String alias) {
        PsiElement ancestor = place.getParent();
        while (ancestor != null && !(ancestor instanceof PsiFile)) {
            if (SqlPsiParts.isType(ancestor, SqlCompositeElementTypes.SQL_QUERY_EXPRESSION)) {
                PsiElement table = SqlPsiParts.childOfType(ancestor,
                        SqlCompositeElementTypes.SQL_TABLE_EXPRESSION);
                PsiElement from = table == null ? null
                        : SqlPsiParts.childOfType(table, SqlCompositeElementTypes.SQL_FROM_CLAUSE);
                PsiElement array = from == null ? null : unnestArgumentIn(from, alias);
                if (array != null) return array;
            }
            ancestor = ancestor.getParent();
        }
        return null;
    }

    private static @Nullable PsiElement unnestArgumentIn(@NotNull PsiElement element,
                                                        @NotNull String alias) {
        for (PsiElement child : element.getChildren()) {
            if (SqlPsiParts.isType(child, SqlCompositeElementTypes.SQL_AS_EXPRESSION)
                    && namesAlias(child, alias)) {
                PsiElement array = unnestArgumentOf(child);
                if (array != null) return array;
            }
            PsiElement nested = unnestArgumentIn(child, alias);
            if (nested != null) return nested;
        }
        return null;
    }

    private static boolean namesAlias(@NotNull PsiElement asExpression, @NotNull String alias) {
        PsiElement identifier = SqlPsiParts.lastIdentifier(asExpression);
        return identifier != null
                && SqlPsiParts.unquoted(identifier.getText()).equalsIgnoreCase(alias);
    }

    /**
     * What an aliased table expression unnests, or {@code null} when it unnests nothing. The call is
     * recognised by the name it is written with: unnesting is a function of the dialect rather than a
     * keyword of it, so the parser leaves its name where any other callable's would be.
     */
    private static @Nullable PsiElement unnestArgumentOf(@NotNull PsiElement asExpression) {
        PsiElement call = SqlPsiParts.childOfType(asExpression,
                SqlCompositeElementTypes.SQL_TABLE_PROCEDURE_CALL_EXPRESSION);
        PsiElement function = call == null
                ? SqlPsiParts.childOfType(asExpression, SqlCompositeElementTypes.SQL_FUNCTION_CALL)
                : SqlPsiParts.childOfType(call, SqlCompositeElementTypes.SQL_FUNCTION_CALL);
        if (function == null) return null;
        PsiElement callable = SqlPsiParts.childOfType(function,
                SqlCompositeElementTypes.SQL_ANY_CALLABLE_REFERENCE);
        if (callable == null || !UNNEST.equalsIgnoreCase(callable.getText())) return null;
        PsiElement arguments = SqlPsiParts.childOfType(function,
                SqlCompositeElementTypes.SQL_ARGUMENT_LIST);
        if (arguments == null) return null;
        for (PsiElement child : arguments.getChildren()) {
            if (isReference(child)) return child;
        }
        return null;
    }

    @Override
    public @Nullable StructColumnPath declaredPathAt(@NotNull PsiElement token) {
        PsiElement identifier = aliasIdentifierOf(token);
        if (identifier == null) return null;

        List<String> names = new ArrayList<>();
        names.add(SqlPsiParts.unquoted(identifier.getText()));

        PsiElement item = identifier.getParent();
        PsiElement enclosing = enclosingFieldOf(item);
        if (enclosing == null) return null;
        while (enclosing != null) {
            PsiElement name = SqlPsiParts.lastIdentifier(enclosing);
            if (name == null) return null;
            names.add(SqlPsiParts.unquoted(name.getText()));
            item = enclosing;
            enclosing = enclosingFieldOf(item);
        }
        Collections.reverse(names);

        PsiFile topLevel = SqlxColumnAtCaret.sqlxFileOf(identifier);
        if (topLevel == null) return null;
        ColumnOriginService origins = ColumnOriginService.getInstance(identifier.getProject());
        ColumnRef column = origins.declaredColumn(topLevel, item);
        if (column == null) return null;
        DataformDasColumn root = origins.dasColumn(column);
        if (root == null) return null;

        List<ColumnInfo> trail = fieldsOf(root.getColumnInfo(), names.subList(1, names.size()));
        return trail == null ? null : new StructColumnPath(root, trail);
    }

    /** The alias an element names, when it names one. */
    private static @Nullable PsiElement aliasIdentifierOf(@NotNull PsiElement token) {
        PsiElement identifier = SqlPsiParts.isType(token, SqlCompositeElementTypes.SQL_IDENTIFIER)
                ? token
                : token.getParent();
        if (!SqlPsiParts.isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)) return null;
        PsiElement expression = identifier.getParent();
        if (!SqlPsiParts.isType(expression, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) return null;
        return SqlPsiParts.lastIdentifier(expression) == identifier ? identifier : null;
    }

    /**
     * The item holding the one given, when a {@code STRUCT(...)} stands between them. Reaching the
     * select list without crossing a constructor means the item is a column of the table rather than
     * a field of one, and the schema answers for it already.
     */
    private static @Nullable PsiElement enclosingFieldOf(@NotNull PsiElement item) {
        boolean insideStruct = false;
        PsiElement node = item.getParent();
        while (node != null && !(node instanceof PsiFile)) {
            if (StructFieldDeclarationLocator.isStructConstructor(node)) insideStruct = true;
            if (SqlPsiParts.isType(node, SqlCompositeElementTypes.SQL_AS_EXPRESSION)) {
                return insideStruct ? node : null;
            }
            if (SqlPsiParts.isType(node, SqlCompositeElementTypes.SQL_SELECT_CLAUSE)) return null;
            node = node.getParent();
        }
        return null;
    }

    @Override
    public @Nullable PsiElement segmentAt(@NotNull PsiElement token) {
        if (isReference(token)) return token;
        PsiElement identifier = SqlPsiParts.isType(token, SqlCompositeElementTypes.SQL_IDENTIFIER)
                ? token
                : token.getParent();
        if (!SqlPsiParts.isType(identifier, SqlCompositeElementTypes.SQL_IDENTIFIER)) return null;
        PsiElement owner = identifier.getParent();
        if (!isReference(owner)) return null;
        return SqlPsiParts.lastIdentifier(owner) == identifier ? owner : null;
    }

    /**
     * The reference and every reference nested in it, outermost first. {@code n.customer.origin}
     * holds {@code n.customer}, which holds {@code n}: one element per segment, each naming the
     * segments before it as well as its own.
     */
    private static @NotNull List<PsiElement> chainFrom(@NotNull PsiElement outermost) {
        List<PsiElement> chain = new ArrayList<>();
        PsiElement node = outermost;
        while (node != null) {
            chain.add(node);
            node = nestedReferenceOf(node);
        }
        return chain;
    }

    private static @Nullable PsiElement nestedReferenceOf(@NotNull PsiElement node) {
        for (PsiElement child : node.getChildren()) {
            if (isReference(child)) return child;
        }
        return null;
    }

    /**
     * The segments the chain holds above its root, from the one just inside the caret's own segment
     * down to the caret. {@code null} when a segment names nothing.
     */
    private static @Nullable List<String> namesAbove(@NotNull List<PsiElement> chain, int root) {
        List<String> names = new ArrayList<>(root);
        for (int i = root - 1; i >= 0; i--) {
            PsiElement identifier = SqlPsiParts.lastIdentifier(chain.get(i));
            if (identifier == null) return null;
            names.add(SqlPsiParts.unquoted(identifier.getText()));
        }
        return names;
    }

    /** The fields a list of names walks into, or {@code null} when the schema holds none of them. */
    private static @Nullable List<ColumnInfo> fieldsOf(@NotNull ColumnInfo root,
                                                      @NotNull List<String> names) {
        List<ColumnInfo> trail = new ArrayList<>(names.size());
        ColumnInfo owner = root;
        for (String name : names) {
            ColumnInfo field = fieldOf(owner, name);
            if (field == null) return null;
            trail.add(field);
            owner = field;
        }
        return trail;
    }

    private static @Nullable ColumnInfo fieldOf(@NotNull ColumnInfo owner, @NotNull String name) {
        for (ColumnInfo field : owner.subFields()) {
            if (field.name().equalsIgnoreCase(name)) return field;
        }
        return null;
    }

    /** The Dataform column a reference stands for, {@code null} when it stands for anything else. */
    private static @Nullable DataformDasColumn columnOf(@NotNull PsiElement reference) {
        PsiReference psiReference = reference.getReference();
        if (psiReference == null) return null;
        if (psiReference instanceof PsiPolyVariantReference poly) {
            for (ResolveResult result : poly.multiResolve(false)) {
                if (result.getElement() instanceof DataformDasColumn column) return column;
            }
            return null;
        }
        return psiReference.resolve() instanceof DataformDasColumn column ? column : null;
    }

    private static boolean isReference(@Nullable PsiElement element) {
        return SqlPsiParts.isType(element, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE)
                || SqlPsiParts.isType(element, SqlCompositeElementTypes.SQL_REFERENCE);
    }
}
