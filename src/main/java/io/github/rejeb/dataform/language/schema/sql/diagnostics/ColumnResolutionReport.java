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
package io.github.rejeb.dataform.language.schema.sql.diagnostics;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sql.psi.SqlCompositeElementTypes;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Declaration;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import io.github.rejeb.dataform.language.schema.sql.ColumnOriginService;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.SqlxColumnAtCaret;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A plain-text account of every step the plugin takes to answer Go to Declaration and rename for
 * the column under the caret: the compiled action of the file, its cached schema, how the SQL
 * references of the query resolve, and what the column lineage graph holds. Each step names what it
 * found, so the first one that comes back empty is where resolution breaks.
 */
public final class ColumnResolutionReport {

    private static final int SAMPLE_SIZE = 20;
    private static final int MESSAGE_LENGTH = 300;

    private final Project project;
    private final StringBuilder out = new StringBuilder();

    private ColumnResolutionReport(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Builds the report. Parses and resolves PSI and may build the lineage graph, so it must run in
     * a read action on a background thread.
     *
     * @param hostFile the SQLX file the caret is in
     * @param offset   the caret offset in that file
     * @return the report text
     */
    public static @NotNull String build(@NotNull PsiFile hostFile, int offset) {
        ColumnResolutionReport report = new ColumnResolutionReport(hostFile.getProject());
        report.write(hostFile, offset);
        return report.out.toString();
    }

    private void write(@NotNull PsiFile hostFile, int offset) {
        line("Dataform column resolution report");
        line("");
        CompiledGraph graph = DataformCompilationService.getInstance(project).getCompiledGraph();
        Map<String, DataformDasTable> tables = DataformTableSchemaService.getInstance(project).getAllTables();
        guarded("Project", () -> project(graph, tables));
        guarded("File", () -> file(hostFile, graph, tables));
        guarded("Caret", () -> caret(hostFile, offset));
        guarded("Lineage", () -> lineage(hostFile, offset));
    }

    private void project(@Nullable CompiledGraph graph, @NotNull Map<String, DataformDasTable> tables) {
        section("Project");
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        line("project dir: " + (projectDir == null ? "<none>" : projectDir.getPath()));
        if (graph == null) {
            line("compiled graph: <none> -- compile the project first");
            return;
        }
        line("compiled graph: " + graph.getTables().size() + " tables, "
                + graph.getDeclarations().size() + " declarations, "
                + graph.getOperations().size() + " operations, "
                + graph.getAssertions().size() + " assertions");
        int compilationErrors = graph.getGraphErrors() == null ? 0 : graph.getGraphErrors().getCompilationErrors().size();
        line("compilation errors: " + compilationErrors);
        line("cached schemas: " + tables.size());

        List<String> missing = new ArrayList<>();
        actionTargets(graph).forEach(target -> {
            if (!tables.containsKey(target.getFullName())) missing.add(target.getFullName());
        });
        line("actions without a cached schema: " + missing.size());
        Map<String, String> errors = DryRunErrorRegistry.getInstance(project).getErrors();
        line("dry-run errors: " + errors.size());
        missing.stream().limit(SAMPLE_SIZE).forEach(fqn -> {
            String error = errors.get(fqn);
            line("  - " + fqn + (error == null ? "" : "  [dry-run: " + shorten(error) + "]"));
        });
        if (missing.size() > SAMPLE_SIZE) line("  ... " + (missing.size() - SAMPLE_SIZE) + " more");
    }

    private void file(@NotNull PsiFile hostFile,
                      @Nullable CompiledGraph graph,
                      @NotNull Map<String, DataformDasTable> tables) {
        section("File");
        VirtualFile virtualFile = hostFile.getVirtualFile();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        String relative = virtualFile == null || projectDir == null
                ? null : VfsUtilCore.getRelativePath(virtualFile, projectDir);
        line("relative path: " + (relative == null ? "<not under the project dir>" : relative));
        if (graph == null || relative == null) return;

        List<CompiledTable> actions = graph.findTableByFileName(virtualFile.getPath());
        if (actions.isEmpty()) {
            line("compiled action for this path: <none>");
            String name = virtualFile.getName();
            graph.getTables().stream()
                    .map(CompiledTable::getFileName)
                    .filter(f -> f != null && f.endsWith(name))
                    .limit(SAMPLE_SIZE)
                    .forEach(f -> line("  compiled file name ending the same: " + f));
            return;
        }
        for (CompiledTable action : actions) {
            Target target = action.getTarget();
            String fqn = target == null ? null : target.getFullName();
            line("compiled action: " + fqn
                    + (action.getCanonicalTarget() == null ? "" : "  (canonical " + action.getCanonicalTarget().getFullName() + ")"));
            line("  compiled file name: " + action.getFileName());
            DataformDasTable table = fqn == null ? null : tables.get(fqn);
            line("  cached schema: " + (table == null ? "<none>" : table.getColumns().size() + " columns"));
            String error = fqn == null ? null : DryRunErrorRegistry.getInstance(project).getError(fqn);
            if (error != null) line("  dry-run error: " + shorten(error));
            line("  dependencies: " + action.getDependencyTargets().size());
            action.getDependencyTargets().stream().limit(SAMPLE_SIZE).forEach(dependency ->
                    line("    - " + dependency.getFullName()
                            + (tables.containsKey(dependency.getFullName()) ? "" : "  <no cached schema>")));
        }
    }

    private void caret(@NotNull PsiFile hostFile, int offset) {
        section("Caret");
        PsiElement injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, offset);
        if (injected == null) {
            line("injected SQL at caret: <none> -- the caret is not inside an injected SQL block");
            return;
        }
        PsiFile sql = injected.getContainingFile();
        line("injected SQL: " + sql.getTextLength() + " characters, language " + sql.getLanguage().getID());
        tableReferences(sql);

        PsiElement reference = SqlxColumnAtCaret.referenceOf(injected);
        if (reference == null) {
            line("column reference at caret: <none> -- token '" + injected.getText() + "'");
            return;
        }
        line("column reference at caret: " + reference.getText());
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        ColumnRef declared = origins.declaredColumn(hostFile, reference);
        line("declares output column: " + (declared == null ? "<no>" : declared.id()));
        if (declared != null) {
            DataformDasColumn column = origins.dasColumn(declared);
            line("  schema column: " + (column == null ? "<none in cached schema>" : describe(column)));
        }
        line("resolves to:");
        resolveResults(reference.getReference()).forEach(result -> line("  - " + result));
    }

    private void tableReferences(@NotNull PsiFile sql) {
        List<PsiElement> references = new ArrayList<>();
        PsiTreeUtil.processElements(sql, element -> {
            if (element.getNode() != null
                    && element.getNode().getElementType() == SqlCompositeElementTypes.SQL_TABLE_REFERENCE) {
                references.add(element);
            }
            return true;
        });
        line("table references in this query: " + references.size());
        references.stream().limit(SAMPLE_SIZE).forEach(reference -> {
            line("  - " + reference.getText());
            resolveResults(reference.getReference()).forEach(result -> line("      -> " + result));
        });
        if (sql.getText().contains("NULL") && references.isEmpty()) {
            line("  the query holds NULL placeholders: a ${...} may not have been resolved to a table");
        }
    }

    private void lineage(@NotNull PsiFile hostFile, int offset) {
        section("Lineage");
        long start = System.nanoTime();
        ColumnLineageGraph graph = LineageGraphService.getInstance(project).columnGraph();
        long millis = (System.nanoTime() - start) / 1_000_000;
        if (graph == null) {
            line("column graph: <none> (" + millis + " ms)");
            return;
        }
        line("column graph: " + graph.columns().size() + " columns, " + graph.edges().size()
                + " edges, obtained in " + millis + " ms");
        line("unresolved tables: " + graph.unresolvedTables().size());
        graph.unresolvedTables().stream().limit(SAMPLE_SIZE).forEach(t -> line("  - " + t));

        DataformDasColumn column = SqlxColumnAtCaret.columnAt(hostFile, offset);
        if (column == null) {
            line("column at caret: <not resolved to a Dataform column>");
            return;
        }
        ColumnRef ref = ColumnOriginService.getInstance(project).reference(column);
        if (ref == null) {
            line("column at caret: " + describe(column) + " -- <no column reference for its table>");
            return;
        }
        ColumnRef node = graph.column(ref.id());
        line("column at caret: " + ref.id() + " -- " + (node == null ? "<not in the column graph>" : "in the column graph"));
        if (node != null) {
            line("  upstream columns: " + graph.upstream(node.id()).size()
                    + ", downstream columns: " + graph.downstream(node.id()).size());
        }
    }

    private @NotNull List<String> resolveResults(@Nullable PsiReference reference) {
        if (reference == null) return List.of("<no reference>");
        List<String> described = new ArrayList<>();
        if (reference instanceof PsiPolyVariantReference poly) {
            for (ResolveResult result : poly.multiResolve(false)) {
                described.add(describe(result.getElement()));
            }
        } else {
            described.add(describe(reference.resolve()));
        }
        return described.isEmpty() ? List.of("<unresolved>") : described;
    }

    private static @NotNull String describe(@Nullable PsiElement element) {
        if (element == null) return "<null>";
        if (element instanceof DataformDasColumn column) {
            String table = column.getTable() instanceof DataformDasTable t && t.getFullName() != null
                    ? t.getFullName()
                    : column.getTable() == null ? "?" : column.getTable().getName();
            return "Dataform column " + table + "." + column.getName();
        }
        if (element instanceof DataformDasTable table) {
            return "Dataform table " + (table.getFullName() == null ? table.getName() : table.getFullName());
        }
        return element.getClass().getSimpleName() + " '" + shorten(element.getText()) + "'";
    }

    private static @NotNull Stream<Target> actionTargets(@NotNull CompiledGraph graph) {
        return Stream.of(
                graph.getTables().stream().map(CompiledTable::getTarget),
                graph.getOperations().stream().map(CompiledOperation::getTarget),
                graph.getDeclarations().stream().map(Declaration::getTarget)
        ).flatMap(s -> s).filter(t -> t != null && t.getFullName() != null);
    }

    private void guarded(@NotNull String step, @NotNull Runnable body) {
        try {
            body.run();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            line("!! " + step + " failed: " + e);
            for (StackTraceElement frame : e.getStackTrace()) {
                if (frame.getClassName().startsWith("io.github.rejeb")) line("     at " + frame);
            }
        }
        line("");
    }

    private void section(@NotNull String title) {
        line("== " + title);
    }

    private void line(@NotNull String text) {
        out.append(text).append('\n');
    }

    private static @NotNull String shorten(@Nullable String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= MESSAGE_LENGTH ? flat : flat.substring(0, MESSAGE_LENGTH) + "...";
    }
}
