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
package io.github.rejeb.dataform.language.columns.rename.downstream;

import com.intellij.sql.psi.SqlCompositeElementTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.columns.analysis.BigQuerySelectAnalyzer;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.ColumnOriginService;
import io.github.rejeb.dataform.language.columns.origin.SqlxOutputColumnLocator;
import io.github.rejeb.dataform.language.columns.rename.plan.ColumnRenamePlan;
import io.github.rejeb.dataform.language.columns.rename.plan.ColumnRenamePlanner;
import io.github.rejeb.dataform.language.columns.rename.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.columns.rename.usage.ColumnRenameEdit;
import io.github.rejeb.dataform.language.columns.rename.usage.ConfigRenameEditCollector;
import io.github.rejeb.dataform.language.columns.rename.usage.EditFactory;
import io.github.rejeb.dataform.language.columns.rename.usage.HostRanges;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.injection.InjectedFiles;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.rejeb.dataform.language.util.Utils.DATAFORM_SCHEMA_PREFIX;

/**
 * Plans the rename of the downstream usages of a column renamed in its action.
 *
 * <p>The column rename refactoring matches columns by resolving, and once the schemas have caught
 * up with the change nothing resolves to the old name any more. So the reads of the old name in the
 * actions reading the renamed one directly are found here instead: unresolved references named like
 * it whose qualifier stands for a renamed action, or unqualified ones when only renamed actions are
 * read. A read in the main select list publishes the column under the new name once rewritten, and
 * the rename of that column goes on downstream through the refactoring, which still resolves it. A
 * read in a common table expression or a subquery keeps the inner name with an alias, so the rest of
 * the query is left alone.</p>
 *
 * <p>Reads the PSI and searches the project, so it runs in a read action away from the event
 * thread.</p>
 */
public final class DownstreamRenamePlanner {

    private DownstreamRenamePlanner() {
    }

    /**
     * The plan renaming every downstream usage of {@code rename.oldName()} to its new name, or
     * {@code null} when the action the column was renamed in has no file.
     */
    public static @Nullable ColumnRenamePlan plan(@NotNull Project project, @NotNull DownstreamRename rename) {
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        PsiFile renamedFile = graph == null ? null : fileOf(project, graph, rename.tableFullName());
        if (renamedFile == null) return null;
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        ColumnRef column = new ColumnRef(rename.tableFullName(), rename.oldName());
        PsiElement declaration = origins.declaringElement(new ColumnRef(rename.tableFullName(), rename.newName()));
        if (declaration == null) {
            return ColumnRenamePlan.refused(new ColumnRenameSubject(column, renamedFile, renamedFile,
                    ColumnRenameSubject.Kind.SELECT_ALIAS), rename.newName(),
                    rename.tableFullName() + " no longer declares " + rename.newName());
        }
        ColumnRenameSubject subject = new ColumnRenameSubject(column, declaration, renamedFile,
                ColumnRenameSubject.Kind.SELECT_ALIAS);

        List<ColumnRenameEdit> edits = new ArrayList<>(
                ConfigRenameEditCollector.collect(renamedFile, rename.oldName(), rename.newName()));
        List<String> warnings = new ArrayList<>();
        Set<ColumnRef> columns = new LinkedHashSet<>(Set.of(column));
        Map<String, PsiFile> readers = readersOf(project, graph, rename.renamedTables());
        List<ColumnRef> published = new ArrayList<>();
        readers.forEach((reader, file) -> collectReads(project, reader, file, rename, edits, warnings, published));
        for (ColumnRef carried : published) {
            renameDownstream(project, origins, carried, rename.newName(), edits, warnings, columns);
        }
        return new ColumnRenamePlan(subject, rename.newName(), Set.copyOf(columns), edits, List.of(),
                List.copyOf(warnings), null);
    }

    private static @Nullable PsiFile fileOf(@NotNull Project project, @NotNull CompiledGraph graph,
                                            @NotNull String fullName) {
        for (var table : graph.getTables()) {
            if (isNamed(table.getTarget(), fullName)) return DataformPaths.findPsiFileInProject(project, table.getFileName());
        }
        for (var operation : graph.getOperations()) {
            if (isNamed(operation.getTarget(), fullName)) {
                return DataformPaths.findPsiFileInProject(project, operation.getFileName());
            }
        }
        return null;
    }

    private static boolean isNamed(@Nullable Target target, @NotNull String fullName) {
        return target != null && fullName.equals(target.getFullName());
    }

    private static @NotNull Map<String, PsiFile> readersOf(@NotNull Project project,
                                                           @NotNull CompiledGraph graph,
                                                           @NotNull Set<String> renamedTables) {
        Map<String, PsiFile> readers = new LinkedHashMap<>();
        graph.getTables().forEach(t -> addReader(project, readers, t.getTarget(), t.getDependencyTargets(),
                t.getFileName(), renamedTables));
        graph.getOperations().forEach(o -> addReader(project, readers, o.getTarget(), o.getDependencyTargets(),
                o.getFileName(), renamedTables));
        return readers;
    }

    private static void addReader(@NotNull Project project,
                                  @NotNull Map<String, PsiFile> readers,
                                  @Nullable Target target,
                                  @NotNull List<Target> dependencies,
                                  @Nullable String fileName,
                                  @NotNull Set<String> renamedTables) {
        if (target == null || target.getFullName() == null) return;
        boolean reads = dependencies.stream().anyMatch(d -> d != null && renamedTables.contains(d.getFullName()));
        if (!reads) return;
        PsiFile file = DataformPaths.findPsiFileInProject(project, fileName);
        if (file != null) readers.put(target.getFullName(), file);
    }

    private static void collectReads(@NotNull Project project,
                                     @NotNull String reader,
                                     @NotNull PsiFile file,
                                     @NotNull DownstreamRename rename,
                                     @NotNull List<ColumnRenameEdit> edits,
                                     @NotNull List<String> warnings,
                                     @NotNull List<ColumnRef> published) {
        if (!(file instanceof SqlxFile)) {
            warnings.add(file.getName() + " is not a SQLX file and was not searched for `" + rename.oldName() + "`");
            return;
        }
        BigQuerySelectAnalyzer analyzer = new BigQuerySelectAnalyzer(project);
        for (PsiFile injected : InjectedFiles.allSql(file)) {
            Map<String, String> aliases = analyzer.fromAliases(injected.getText());
            PsiElement mainSelect = SqlxOutputColumnLocator.mainSelectClauseOf(injected);
            for (PsiElement reference : staleReadsOf(injected, rename)) {
                String qualifier = qualifierOf(reference);
                if (!readsRenamed(qualifier, aliases, rename.renamedTables())) {
                    if (qualifier == null) {
                        warnings.add(file.getName() + " reads `" + rename.oldName()
                                + "` without naming its table among several; left unchanged");
                    }
                    continue;
                }
                boolean bareItem = isType(reference.getParent(), SqlCompositeElementTypes.SQL_SELECT_CLAUSE);
                boolean publishes = bareItem && reference.getParent() == mainSelect;
                ColumnRenameEdit edit = bareItem && !publishes
                        ? EditFactory.ofWhole(reference,
                        (qualifier == null ? "" : qualifier + ".") + rename.newName() + " AS " + rename.oldName(),
                        ColumnRenameEdit.Kind.SQL_REFERENCE, ColumnRenameEdit.Risk.CERTAIN,
                        "read of " + rename.oldName() + " in " + file.getName())
                        : renameIdentifier(reference, rename, file);
                if (edit != null) edits.add(edit);
                if (publishes) published.add(new ColumnRef(reader, rename.oldName()));
            }
        }
    }

    private static @Nullable ColumnRenameEdit renameIdentifier(@NotNull PsiElement reference,
                                                               @NotNull DownstreamRename rename,
                                                               @NotNull PsiFile file) {
        PsiElement identifier = lastIdentifierOf(reference);
        return identifier == null ? null : EditFactory.ofWhole(identifier, rename.newName(),
                ColumnRenameEdit.Kind.SQL_REFERENCE, ColumnRenameEdit.Risk.CERTAIN,
                "read of " + rename.oldName() + " in " + file.getName());
    }

    private static @NotNull List<PsiElement> staleReadsOf(@NotNull PsiFile injected, @NotNull DownstreamRename rename) {
        List<PsiElement> reads = new ArrayList<>();
        injected.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (isType(element, SqlCompositeElementTypes.SQL_COLUMN_REFERENCE) && isStaleRead(element, rename)) {
                    reads.add(element);
                    return;
                }
                super.visitElement(element);
            }
        });
        return reads;
    }

    private static boolean isStaleRead(@NotNull PsiElement reference, @NotNull DownstreamRename rename) {
        PsiElement identifier = lastIdentifierOf(reference);
        if (identifier == null || !unquote(identifier.getText()).equalsIgnoreCase(rename.oldName())) return false;
        PsiReference psiReference = reference.getReference();
        if (psiReference == null) return false;
        PsiElement target = psiReference.resolve();
        if (target == null) return true;
        if (!(target instanceof DataformDasColumn column)) return false;
        ColumnRef read = column.ref();
        return read == null || !rename.renamedTables().contains(read.tableFullName());
    }

    private static @Nullable PsiElement lastIdentifierOf(@NotNull PsiElement reference) {
        PsiElement last = null;
        for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (isType(child, SqlCompositeElementTypes.SQL_IDENTIFIER)) last = child;
        }
        return last;
    }

    private static @Nullable String qualifierOf(@NotNull PsiElement reference) {
        String text = reference.getText();
        int dot = text.lastIndexOf('.');
        return dot < 0 ? null : text.substring(0, dot).trim();
    }

    private static boolean readsRenamed(@Nullable String qualifier,
                                        @NotNull Map<String, String> aliases,
                                        @NotNull Set<String> renamedTables) {
        if (qualifier != null) return pointsToAny(aliases.getOrDefault(qualifier, qualifier), renamedTables);
        Set<String> tables = new LinkedHashSet<>(aliases.values());
        return !tables.isEmpty() && tables.stream().allMatch(token -> pointsToAny(token, renamedTables));
    }

    private static boolean pointsToAny(@NotNull String token, @NotNull Set<String> fullNames) {
        String table = withoutSchemaPrefix(unquote(token));
        return fullNames.stream().anyMatch(full -> full.equalsIgnoreCase(table)
                || full.toLowerCase().endsWith("." + table.toLowerCase()));
    }

    private static @NotNull String unquote(@NotNull String text) {
        return text.replace("`", "").trim();
    }

    private static @NotNull String withoutSchemaPrefix(@NotNull String table) {
        String[] parts = table.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            parts[i] = StringUtil.trimStart(parts[i], DATAFORM_SCHEMA_PREFIX);
        }
        return String.join(".", parts);
    }

    private static boolean isType(@Nullable PsiElement element, @NotNull IElementType type) {
        return element != null && element.getNode() != null && element.getNode().getElementType() == type;
    }

    private static void renameDownstream(@NotNull Project project,
                                         @NotNull ColumnOriginService origins,
                                         @NotNull ColumnRef carried,
                                         @NotNull String newName,
                                         @NotNull List<ColumnRenameEdit> edits,
                                         @NotNull List<String> warnings,
                                         @NotNull Set<ColumnRef> columns) {
        PsiElement declaration = origins.declaringElement(carried);
        PsiFile file = declaration == null ? null : HostRanges.hostPsiFileOf(declaration);
        if (file == null) return;
        ColumnRenamePlan plan = ColumnRenamePlanner.getInstance(project).plan(
                new ColumnRenameSubject(carried, declaration, file, ColumnRenameSubject.Kind.SELECT_ITEM), newName);
        if (plan.refusal() != null) {
            warnings.add("The usages of " + carried.tableFullName() + "." + carried.columnName()
                    + " keep the old name: " + plan.refusal());
            return;
        }
        edits.addAll(plan.edits());
        warnings.addAll(plan.warnings());
        columns.addAll(plan.columns());
        plan.starBoundaries().forEach(boundary -> warnings.add("The star of "
                + (boundary.file() == null ? boundary.column().tableFullName() : boundary.file().getName())
                + " publishes " + boundary.column().columnName() + " under the old name"));
    }
}
