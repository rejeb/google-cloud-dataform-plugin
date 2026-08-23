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
package io.github.rejeb.dataform.language.refactoring.column.usage;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.refactoring.column.DataformColumnNameValidator;
import io.github.rejeb.dataform.language.refactoring.column.plan.StarBoundary;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the SQL places of a rename: where each column is declared, and everywhere it is read.
 *
 * <p>A column is matched by resolving, never by name — {@code order_id} exists in every layer of a
 * Dataform project and the same name in two actions is two different columns.</p>
 */
public final class SqlRenameEditCollector {

    /**
     * What the SQL of the project has to say about a rename.
     *
     * @param edits      the places to write
     * @param boundaries the columns that have no declaration to write, because a star produces them
     * @param warnings   what could not be looked at, and keeps the old name because of it
     */
    public record Result(@NotNull List<ColumnRenameEdit> edits,
                         @NotNull List<StarBoundary> boundaries,
                         @NotNull List<String> warnings) {
    }

    private SqlRenameEditCollector() {
    }

    /**
     * The SQL places of every column of the rename. Runs a project-wide reference search, so it
     * belongs to a background read action.
     */
    public static @NotNull Result collect(@NotNull Project project,
                                          @NotNull Set<ColumnRef> columns,
                                          @NotNull String newName) {
        Map<String, ColumnRenameEdit> edits = new LinkedHashMap<>();
        List<StarBoundary> boundaries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ColumnOriginService origins = ColumnOriginService.getInstance(project);

        for (ColumnRef column : columns) {
            collectDeclaration(project, origins, column, newName, edits, boundaries, warnings);
            collectReferences(project, origins, column, newName, edits);
        }
        return new Result(List.copyOf(edits.values()), List.copyOf(boundaries),
                List.copyOf(warnings));
    }

    /**
     * The declaration of one column: the select-list item naming it, the aliases feeding it from
     * outside the select list, and the reads of those aliases inside the same file.
     */
    private static void collectDeclaration(@NotNull Project project,
                                           @NotNull ColumnOriginService origins,
                                           @NotNull ColumnRef column,
                                           @NotNull String newName,
                                           @NotNull Map<String, ColumnRenameEdit> edits,
                                           @NotNull List<StarBoundary> boundaries,
                                           @NotNull List<String> warnings) {
        PsiElement declaration = origins.declaringElement(column);
        if (declaration == null) {
            warnings.add("The file declaring " + column.tableFullName() + "."
                    + column.columnName() + " could not be opened, so it keeps the old name");
            return;
        }
        PsiFile hostFile = HostRanges.hostPsiFileOf(declaration);
        if (hostFile == null) return;

        List<PsiElement> aliases =
                SqlxStarDeclarationLocator.findStructAliases(hostFile, column.columnName());

        if (SqlxStarDeclarationLocator.isStar(declaration)) {
            if (aliases.isEmpty()) {
                boundaries.add(new StarBoundary(column, hostFile.getVirtualFile(),
                        SmartPointerManager.getInstance(project)
                                .createSmartPsiElementPointer(declaration),
                        List.of()));
                return;
            }
        } else {
            add(edits, EditFactory.ofWhole(declaration, DataformColumnNameValidator.inSql(newName),
                    ColumnRenameEdit.Kind.SQL_DECLARATION, ColumnRenameEdit.Risk.CERTAIN,
                    "declaration of " + column.columnName()));
        }

        for (PsiElement alias : aliases) {
            add(edits, EditFactory.ofWhole(alias, DataformColumnNameValidator.inSql(newName),
                    ColumnRenameEdit.Kind.SQL_STRUCT_ALIAS, ColumnRenameEdit.Risk.CERTAIN,
                    "alias feeding " + column.columnName()));
        }
        if (!aliases.isEmpty()) {
            collectLocalReads(hostFile, column, newName, edits);
        }
    }

    /**
     * The reads of a name inside the file declaring it, for the case where the name is written by an
     * alias of the same file rather than by another action.
     *
     * <p>A reference resolving to a Dataform column of another table is left alone: that column is
     * not part of this rename and shares the name by accident.</p>
     */
    private static void collectLocalReads(@NotNull PsiFile hostFile,
                                          @NotNull ColumnRef column,
                                          @NotNull String newName,
                                          @NotNull Map<String, ColumnRenameEdit> edits) {
        for (PsiFile injected : InjectedSqlFiles.all(hostFile)) {
            for (PsiElement reference : PsiTreeUtil.collectElements(injected,
                    element -> isType(element, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE))) {
                PsiElement identifier = lastIdentifier(reference);
                if (identifier == null
                        || !unquoted(identifier.getText()).equalsIgnoreCase(column.columnName())) {
                    continue;
                }
                if (resolvesToForeignColumn(reference, column)) continue;
                add(edits, EditFactory.ofWhole(identifier,
                        DataformColumnNameValidator.inSql(newName),
                        ColumnRenameEdit.Kind.SQL_REFERENCE, ColumnRenameEdit.Risk.CERTAIN,
                        "read of " + column.columnName()));
            }
        }
    }

    private static boolean resolvesToForeignColumn(@NotNull PsiElement reference,
                                                   @NotNull ColumnRef column) {
        PsiReference psiReference = reference.getReference();
        PsiElement resolved = psiReference == null ? null : psiReference.resolve();
        return resolved instanceof DataformDasColumn resolvedColumn
                && !resolvedColumn.getName().equalsIgnoreCase(column.columnName());
    }

    /** Every reference of the project resolving to one column of the rename. */
    private static void collectReferences(@NotNull Project project,
                                          @NotNull ColumnOriginService origins,
                                          @NotNull ColumnRef column,
                                          @NotNull String newName,
                                          @NotNull Map<String, ColumnRenameEdit> edits) {
        DataformDasColumn dasColumn = origins.dasColumn(column);
        if (dasColumn == null) return;
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        ReferencesSearch.search(dasColumn, scope).forEach(reference -> {
            PsiElement element = reference.getElement();
            PsiElement identifier = lastIdentifier(element);
            PsiElement target = identifier != null ? identifier : element;
            add(edits, EditFactory.ofWhole(target, DataformColumnNameValidator.inSql(newName),
                    ColumnRenameEdit.Kind.SQL_REFERENCE, ColumnRenameEdit.Risk.CERTAIN,
                    "read of " + column.columnName()));
            return true;
        });
    }

    private static void add(@NotNull Map<String, ColumnRenameEdit> edits,
                            @Nullable ColumnRenameEdit edit) {
        if (edit == null) return;
        edits.putIfAbsent(key(edit), edit);
    }

    private static @NotNull String key(@NotNull ColumnRenameEdit edit) {
        return edit.file().getPath() + "@" + edit.hostRange().getStartOffset()
                + "-" + edit.hostRange().getEndOffset();
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

    private static @NotNull String unquoted(@NotNull String text) {
        return text.replace("`", "");
    }
}
