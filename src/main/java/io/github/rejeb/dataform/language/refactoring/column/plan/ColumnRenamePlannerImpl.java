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
package io.github.rejeb.dataform.language.refactoring.column.plan;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import io.github.rejeb.dataform.language.refactoring.column.DataformColumnNameValidator;
import io.github.rejeb.dataform.language.refactoring.column.target.ColumnRenameSubject;
import io.github.rejeb.dataform.language.refactoring.column.usage.ColumnRenameEdit;
import io.github.rejeb.dataform.language.refactoring.column.usage.ConfigRenameEditCollector;
import io.github.rejeb.dataform.language.refactoring.column.usage.EditFactory;
import io.github.rejeb.dataform.language.refactoring.column.usage.HostRanges;
import io.github.rejeb.dataform.language.refactoring.column.usage.JsRenameEditCollector;
import io.github.rejeb.dataform.language.refactoring.column.usage.SqlRenameEditCollector;
import io.github.rejeb.dataform.language.refactoring.column.usage.SqlxStarExpander;
import io.github.rejeb.dataform.language.refactoring.column.usage.TestRenameEditCollector;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColumnRenamePlannerImpl implements ColumnRenamePlanner {

    private final Project project;

    public ColumnRenamePlannerImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull ColumnRenamePlan plan(@NotNull ColumnRenameSubject subject,
                                          @NotNull String newName) {
        if (!DataformColumnNameValidator.isValid(newName)) {
            return ColumnRenamePlan.refused(subject, newName,
                    "\"" + newName + "\" is not a valid BigQuery column name");
        }
        ColumnLineageGraph graph = LineageGraphService.getInstance(project).columnGraph();
        if (graph == null) {
            return ColumnRenamePlan.refused(subject, newName,
                    "The project has not been compiled yet, so the columns reading this one are unknown");
        }
        return build(subject, newName,
                ColumnClosure.of(graph, subject.column(), sourceTables()), List.of(), graph);
    }

    /**
     * The tables declared to the project with {@code declare()}: tables of BigQuery no action
     * builds, whose columns a rename never writes.
     */
    private @NotNull Set<String> sourceTables() {
        CompiledGraph compiled = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (compiled == null) return Set.of();
        Set<String> tables = new LinkedHashSet<>();
        for (Declaration declaration : compiled.getDeclarations()) {
            if (declaration.getTarget() != null) tables.add(declaration.getTarget().getFullName());
        }
        return tables;
    }

    @Override
    public @NotNull ColumnRenamePlan resolve(@NotNull ColumnRenamePlan plan,
                                             @NotNull StarResolution resolution) {
        if (plan.refusal() != null || plan.starBoundaries().isEmpty()) return plan;
        return switch (resolution) {
            case CANCEL -> ColumnRenamePlan.refused(plan.subject(), plan.newName(),
                    "The rename was cancelled");
            case EXPAND -> expand(plan);
            case ALIAS_AT_READERS -> aliasAtReaders(plan);
        };
    }

    /**
     * The plan with each star replaced by the explicit column list, which gives every column of the
     * rename a declaration to write.
     */
    private @NotNull ColumnRenamePlan expand(@NotNull ColumnRenamePlan plan) {
        List<ColumnRenameEdit> expansions = new ArrayList<>();
        List<String> warnings = new ArrayList<>(plan.warnings());
        List<StarBoundary> unresolved = new ArrayList<>();
        Set<VirtualFile> expanded = new LinkedHashSet<>();

        for (StarBoundary boundary : plan.starBoundaries()) {
            PsiElement star = boundary.star() == null ? null : boundary.star().getElement();
            PsiFile hostFile = star == null ? null : HostRanges.hostPsiFileOf(star);
            if (star == null || hostFile == null) {
                unresolved.add(boundary);
                continue;
            }
            SqlxStarExpander.Expansion expansion = SqlxStarExpander.of(hostFile, star,
                    boundary.column().columnName(), plan.newName());
            if (!expansion.isPossible()) {
                unresolved.add(new StarBoundary(boundary.column(), boundary.file(), boundary.star(),
                        expansion.blockers()).withReaders(boundary.readers(), boundary.readerFiles()));
                warnings.add("The star of " + hostFile.getName() + " could not be expanded: "
                        + String.join(", ", expansion.blockers()));
                continue;
            }
            ColumnRenameEdit edit = expansionEdit(star, expansion, hostFile);
            if (edit == null) continue;
            expansions.add(edit);
            expanded.add(edit.file());
        }

        List<ColumnRenameEdit> edits = new ArrayList<>();
        for (ColumnRenameEdit edit : plan.edits()) {
            if (!expanded.contains(edit.file()) || survivesExpansion(edit)) edits.add(edit);
        }
        edits.addAll(expansions);
        return new ColumnRenamePlan(plan.subject(), plan.newName(), plan.columns(),
                List.copyOf(edits), List.copyOf(unresolved), List.copyOf(warnings), null);
    }

    /**
     * Whether a place of a file whose star was expanded still holds.
     *
     * <p>An expansion writes {@code old AS new}: the query goes on reading what its source calls the
     * column and only what it publishes takes the new name. Everything else that file writes the old
     * name in belongs to the source — the alias of a struct the query reads, the SQL a {@code js}
     * block builds — and renaming it would leave the expansion reading a name that no longer exists.
     * The config follows the expansion, since it describes what the action publishes.</p>
     */
    private static boolean survivesExpansion(@NotNull ColumnRenameEdit edit) {
        return switch (edit.kind()) {
            case CONFIG_COLUMN_KEY, CONFIG_COLUMN_NAME, CONFIG_PARTITION_EXPRESSION,
                 CONFIG_ROW_CONDITION, SQL_STAR_EXPANSION -> true;
            default -> false;
        };
    }

    /**
     * The edit writing an expansion, which covers the star <em>and</em> the {@code EXCEPT} list
     * written after it: those names are already left out of the expanded list, and leaving the list
     * behind would turn it into a set operator. The range is taken on the select list holding the
     * star, since it is the only element spanning both.
     */
    private static @Nullable ColumnRenameEdit expansionEdit(@NotNull PsiElement star,
                                                            @NotNull SqlxStarExpander.Expansion expansion,
                                                            @NotNull PsiFile hostFile) {
        PsiElement list = star.getParent();
        TextRange starRange = star.getTextRange();
        TextRange listRange = list == null ? null : list.getTextRange();
        String presentation = "expansion of the star of " + hostFile.getName();
        if (list == null || listRange == null || starRange == null) {
            return EditFactory.ofWhole(star, expansion.text(),
                    ColumnRenameEdit.Kind.SQL_STAR_EXPANSION, ColumnRenameEdit.Risk.CERTAIN,
                    presentation);
        }
        TextRange rangeInList = TextRange.from(
                starRange.getStartOffset() - listRange.getStartOffset(),
                expansion.replacedLength());
        return EditFactory.of(list, rangeInList, expansion.text(),
                ColumnRenameEdit.Kind.SQL_STAR_EXPANSION, ColumnRenameEdit.Risk.CERTAIN,
                presentation);
    }

    /**
     * The plan reduced to the actions reading a column straight from a star and everything
     * downstream of them: the column keeps its old name where the star produces it, and each of
     * those actions declares it under the new one.
     */
    private @NotNull ColumnRenamePlan aliasAtReaders(@NotNull ColumnRenamePlan plan) {
        ColumnRenameSubject subject = plan.subject();
        Set<ColumnRef> readers = new LinkedHashSet<>();
        plan.starBoundaries().forEach(boundary -> readers.addAll(boundary.readers()));
        if (readers.isEmpty()) {
            return ColumnRenamePlan.refused(subject, plan.newName(),
                    "No action of the rename reads the column from the star, so there is nowhere to declare the new name");
        }
        ColumnLineageGraph graph = LineageGraphService.getInstance(project).columnGraph();
        if (graph == null) {
            return ColumnRenamePlan.refused(subject, plan.newName(),
                    "The project has not been compiled yet");
        }
        Map<String, ColumnRenameEdit> edits = new LinkedHashMap<>();
        Set<ColumnRef> columns = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        for (ColumnRef reader : readers) {
            ColumnRenamePlan reduced = build(subject, plan.newName(),
                    ColumnClosure.downstreamOf(graph, reader, sourceTables()), List.of(), graph);
            columns.addAll(reduced.columns());
            reduced.warnings().stream().filter(warning -> !warnings.contains(warning)).forEach(warnings::add);
            withDeclarationAliased(reduced.edits(), reader, plan.newName())
                    .forEach(edit -> edits.put(edit.key(), edit));
        }
        return new ColumnRenamePlan(subject, plan.newName(), Set.copyOf(columns),
                List.copyOf(edits.values()), List.of(), List.copyOf(warnings), null);
    }

    /**
     * The declaration of {@code column} rewritten as {@code old AS new}, so its action starts
     * producing the new name while still reading the old one from its source.
     *
     * <p>A declaration that is already the alias of an {@code AS} expression is only renamed: the
     * expression on its left is what the file reads, and writing {@code old AS new} over the alias
     * would leave the query with two {@code AS} in a row.</p>
     */
    private @NotNull List<ColumnRenameEdit> withDeclarationAliased(
            @NotNull List<ColumnRenameEdit> edits,
            @NotNull ColumnRef column,
            @NotNull String newName) {
        PsiElement declaration = ColumnOriginService.getInstance(project).declaringElement(column);
        if (declaration == null) return edits;
        ColumnRenameEdit aliased = SqlRenameEditCollector.aliasedDeclaration(declaration,
                column.columnName(), newName);
        if (aliased == null) return edits;

        List<ColumnRenameEdit> result = new ArrayList<>();
        for (ColumnRenameEdit edit : edits) {
            if (!edit.hostRange().equals(aliased.hostRange())
                    || !edit.file().equals(aliased.file())) {
                result.add(edit);
            }
        }
        result.add(aliased);
        return List.copyOf(result);
    }

    /**
     * The plan for a set of columns: their SQL places, their config places, and the JavaScript.
     *
     * <p>The config places are looked for in the files declaring a column of the rename, the caret's
     * own file included when it is one of them. A config describes what its action publishes, so the
     * config of a file that only reads the column names a column of its own — one this rename has
     * nothing to say about. The JavaScript of the caret's file is searched either way: the rename
     * writes in that file, and what its {@code js} block builds is about the column being renamed.</p>
     */
    private @NotNull ColumnRenamePlan build(@NotNull ColumnRenameSubject subject,
                                            @NotNull String newName,
                                            @NotNull ColumnClosure closure,
                                            @NotNull List<String> knownWarnings,
                                            @NotNull ColumnLineageGraph graph) {
        Set<ColumnRef> columns = closure.columns();
        SqlRenameEditCollector.Result sql =
                SqlRenameEditCollector.collect(project, columns, closure.carried(),
                        closure.aliased(), newName);
        Map<String, ColumnRenameEdit> edits = new LinkedHashMap<>();
        sql.edits().forEach(edit -> edits.putIfAbsent(edit.key(), edit));
        TestRenameEditCollector.collect(project, columns, newName)
                .forEach(edit -> edits.putIfAbsent(edit.key(), edit));

        Set<PsiFile> declaringFiles = declaringFilesOf(columns);
        if (subject.declaresColumn()) declaringFiles.add(subject.hostFile());
        for (PsiFile file : declaringFiles) {
            ConfigRenameEditCollector.collect(file, subject.oldName(), newName)
                    .forEach(edit -> edits.putIfAbsent(edit.key(), edit));
        }
        Set<PsiFile> touchedFiles = new LinkedHashSet<>(declaringFiles);
        touchedFiles.add(subject.hostFile());
        JsRenameEditCollector.collect(project, subject.oldName(), newName, touchedFiles)
                .forEach(edit -> edits.putIfAbsent(edit.key(), edit));

        List<String> warnings = new ArrayList<>(knownWarnings);
        warnings.addAll(sql.warnings());
        for (ColumnRef unknown : closure.unknownOrigins()) {
            warnings.add("Where " + unknown.tableFullName() + "." + unknown.columnName()
                    + " comes from could not be determined; its sources keep the old name");
        }
        return new ColumnRenamePlan(subject, newName, Set.copyOf(columns), List.copyOf(edits.values()),
                withReaders(sql.boundaries(), graph, columns), List.copyOf(warnings), null);
    }

    /**
     * The boundaries with, for each, the columns of the rename reading it straight from the star
     * and the files declaring them, which is where the new name can be declared instead.
     */
    private @NotNull List<StarBoundary> withReaders(@NotNull List<StarBoundary> boundaries,
                                                    @NotNull ColumnLineageGraph graph,
                                                    @NotNull Set<ColumnRef> columns) {
        List<StarBoundary> result = new ArrayList<>(boundaries.size());
        for (StarBoundary boundary : boundaries) {
            List<ColumnRef> readers = new ArrayList<>();
            Set<VirtualFile> files = new LinkedHashSet<>();
            for (String id : graph.successors(boundary.column().id())) {
                ColumnRef reader = graph.column(id);
                if (reader == null || !columns.contains(reader)) continue;
                PsiElement declaration = ColumnOriginService.getInstance(project).declaringElement(reader);
                PsiFile hostFile = declaration == null ? null : HostRanges.hostPsiFileOf(declaration);
                if (hostFile == null || hostFile.getVirtualFile() == null) continue;
                readers.add(reader);
                files.add(hostFile.getVirtualFile());
            }
            result.add(boundary.withReaders(readers, List.copyOf(files)));
        }
        return List.copyOf(result);
    }

    private @NotNull Set<PsiFile> declaringFilesOf(@NotNull Set<ColumnRef> columns) {
        Set<PsiFile> files = new LinkedHashSet<>();
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        for (ColumnRef column : columns) {
            PsiElement declaration = origins.declaringElement(column);
            PsiFile hostFile = declaration == null ? null : HostRanges.hostPsiFileOf(declaration);
            if (hostFile != null) files.add(hostFile);
        }
        return files;
    }
}
