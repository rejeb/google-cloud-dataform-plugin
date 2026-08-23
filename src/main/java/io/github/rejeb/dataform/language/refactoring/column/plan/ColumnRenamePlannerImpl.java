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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import org.jetbrains.annotations.NotNull;

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
        ColumnClosure closure = ColumnClosure.of(graph, subject.column());
        return build(subject, newName, closure.columns(), closure.unknownOrigins(), List.of());
    }

    @Override
    public @NotNull ColumnRenamePlan resolve(@NotNull ColumnRenamePlan plan,
                                             @NotNull StarResolution resolution) {
        if (plan.refusal() != null || plan.starBoundaries().isEmpty()) return plan;
        return switch (resolution) {
            case CANCEL -> ColumnRenamePlan.refused(plan.subject(), plan.newName(),
                    "The rename was cancelled");
            case EXPAND -> expand(plan);
            case ALIAS_IN_CURRENT_FILE -> aliasInCurrentFile(plan);
        };
    }

    /**
     * The plan with each star replaced by the explicit column list, which gives every column of the
     * rename a declaration to write.
     */
    private @NotNull ColumnRenamePlan expand(@NotNull ColumnRenamePlan plan) {
        List<ColumnRenameEdit> edits = new ArrayList<>(plan.edits());
        List<String> warnings = new ArrayList<>(plan.warnings());
        List<StarBoundary> unresolved = new ArrayList<>();

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
                        expansion.blockers()));
                warnings.add("The star of " + hostFile.getName() + " could not be expanded: "
                        + String.join(", ", expansion.blockers()));
                continue;
            }
            ColumnRenameEdit edit = EditFactory.ofWhole(star, expansion.text(),
                    ColumnRenameEdit.Kind.SQL_STAR_EXPANSION, ColumnRenameEdit.Risk.CERTAIN,
                    "expansion of the star of " + hostFile.getName());
            if (edit != null) edits.add(edit);
        }
        return new ColumnRenamePlan(plan.subject(), plan.newName(), plan.columns(),
                List.copyOf(edits), List.copyOf(unresolved), List.copyOf(warnings), null);
    }

    /**
     * The plan reduced to the file of the caret and everything downstream of it: the column keeps
     * its old name where it is produced, and the file of the caret declares it under the new one.
     */
    private @NotNull ColumnRenamePlan aliasInCurrentFile(@NotNull ColumnRenamePlan plan) {
        ColumnRenameSubject subject = plan.subject();
        if (!subject.declaresColumn()) {
            return ColumnRenamePlan.refused(subject, plan.newName(),
                    "The caret is on a read, so there is no declaration here to give the new name to");
        }
        ColumnLineageGraph graph = LineageGraphService.getInstance(project).columnGraph();
        if (graph == null) {
            return ColumnRenamePlan.refused(subject, plan.newName(),
                    "The project has not been compiled yet");
        }
        ColumnClosure downstream = ColumnClosure.downstreamOf(graph, subject.column());
        ColumnRenamePlan reduced = build(subject, plan.newName(), downstream.columns(),
                List.of(), List.of());
        return new ColumnRenamePlan(subject, plan.newName(), reduced.columns(),
                withDeclarationAliased(reduced.edits(), subject, plan.newName()),
                List.of(), reduced.warnings(), reduced.refusal());
    }

    /**
     * The declaration of the caret's file rewritten as {@code old AS new}, so the file starts
     * producing the new name while still reading the old one from its source.
     */
    private @NotNull List<ColumnRenameEdit> withDeclarationAliased(
            @NotNull List<ColumnRenameEdit> edits,
            @NotNull ColumnRenameSubject subject,
            @NotNull String newName) {
        PsiElement declaration = ColumnOriginService.getInstance(project)
                .declaringElement(subject.column());
        if (declaration == null) return edits;
        ColumnRenameEdit aliased = EditFactory.ofWhole(declaration,
                DataformColumnNameValidator.inSql(subject.oldName())
                        + " AS " + DataformColumnNameValidator.inSql(newName),
                ColumnRenameEdit.Kind.SQL_DECLARATION_ALIAS, ColumnRenameEdit.Risk.CERTAIN,
                "declaration of " + subject.oldName() + " under its new name");
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

    /** The plan for a set of columns: their SQL places, their config places, and the JavaScript. */
    private @NotNull ColumnRenamePlan build(@NotNull ColumnRenameSubject subject,
                                            @NotNull String newName,
                                            @NotNull Set<ColumnRef> columns,
                                            @NotNull List<ColumnRef> unknownOrigins,
                                            @NotNull List<String> knownWarnings) {
        SqlRenameEditCollector.Result sql = SqlRenameEditCollector.collect(project, columns, newName);
        Map<String, ColumnRenameEdit> edits = new LinkedHashMap<>();
        sql.edits().forEach(edit -> edits.putIfAbsent(key(edit), edit));

        Set<PsiFile> touchedFiles = declaringFilesOf(columns);
        touchedFiles.add(subject.hostFile());
        for (PsiFile file : touchedFiles) {
            ConfigRenameEditCollector.collect(file, subject.oldName(), newName)
                    .forEach(edit -> edits.putIfAbsent(key(edit), edit));
        }
        JsRenameEditCollector.collect(project, subject.oldName(), newName, touchedFiles)
                .forEach(edit -> edits.putIfAbsent(key(edit), edit));

        List<String> warnings = new ArrayList<>(knownWarnings);
        warnings.addAll(sql.warnings());
        for (ColumnRef unknown : unknownOrigins) {
            warnings.add("Where " + unknown.tableFullName() + "." + unknown.columnName()
                    + " comes from could not be determined; its sources keep the old name");
        }
        return new ColumnRenamePlan(subject, newName, Set.copyOf(columns), List.copyOf(edits.values()),
                sql.boundaries(), List.copyOf(warnings), null);
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

    private static @NotNull String key(@NotNull ColumnRenameEdit edit) {
        return edit.file().getPath() + "@" + edit.hostRange().getStartOffset()
                + "-" + edit.hostRange().getEndOffset();
    }
}
