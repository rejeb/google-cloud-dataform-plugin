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
package io.github.rejeb.dataform.language.columns.impact;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.columns.rename.downstream.DownstreamRename;
import io.github.rejeb.dataform.language.columns.rename.downstream.DownstreamRenameNotifier;
import io.github.rejeb.dataform.language.compilation.CompilationFailures;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblems;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.DryRunErrorRegistry;
import io.github.rejeb.dataform.language.schema.sql.SchemaRefreshOutcome;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default {@link ColumnImpactService}. Saves made while a check waits for the compilation are
 * folded into it, so the comparison is always made against the columns before the first of them.
 */
public final class ColumnImpactServiceImpl implements ColumnImpactService {

    private static final Logger LOG = Logger.getInstance(ColumnImpactServiceImpl.class);

    private record Baseline(@NotNull Map<String, List<ColumnInfo>> columns, @NotNull Map<String, String> errors) {
    }

    private record Explanation(@NotNull ColumnChange change, @NotNull String failureMessage) {
    }

    private final Project project;
    private final Object lock = new Object();
    private final AtomicLong modificationCount = new AtomicLong();
    private Baseline baseline;
    private boolean checking;
    private volatile Map<String, Explanation> explanations = Map.of();
    private volatile List<DownstreamRename> renames = List.of();

    public ColumnImpactServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void noteSourceSaved() {
        synchronized (lock) {
            if (baseline == null) baseline = snapshot();
            if (checking) return;
            checking = true;
        }
        AppExecutorUtil.getAppExecutorService().execute(this::check);
    }

    @Override
    public @Nullable ColumnChange changeBehind(@NotNull String actionFullName, @NotNull String failureMessage) {
        Explanation explanation = explanations.get(actionFullName);
        return explanation != null && explanation.failureMessage().equals(failureMessage) ? explanation.change() : null;
    }

    @Override
    public @NotNull List<DownstreamRename> pendingRenamesOf(@NotNull String tableFullName) {
        DryRunErrorRegistry registry = DryRunErrorRegistry.getInstance(project);
        return renames.stream()
                .filter(rename -> rename.tableFullName().equals(tableFullName))
                .filter(rename -> readersStillFailing(rename, registry) > 0)
                .toList();
    }

    private long readersStillFailing(@NotNull DownstreamRename rename, @NotNull DryRunErrorRegistry registry) {
        return explanations.entrySet().stream()
                .filter(e -> explains(rename, e.getValue())
                        && e.getValue().failureMessage().equals(registry.getError(e.getKey())))
                .count();
    }

    private static boolean explains(@NotNull DownstreamRename rename, @NotNull Explanation explanation) {
        ColumnChange change = explanation.change();
        return change.kind() == ColumnChange.Kind.RENAMED
                && rename.renamedTables().contains(change.tableFullName())
                && rename.oldName().equalsIgnoreCase(change.oldName())
                && rename.newName().equalsIgnoreCase(change.newName());
    }

    @Override
    public long getModificationCount() {
        return modificationCount.get();
    }

    /**
     * Makes the given impacts the ones the editors explain the failures with, and tells the editors.
     */
    public void publish(@NotNull List<ColumnImpact> impacts) {
        publish(impacts, List.of());
    }

    /**
     * Makes the given impacts the ones the editors explain the failures with and the given renames
     * the ones they suggest carrying downstream, and tells the editors.
     */
    public void publish(@NotNull List<ColumnImpact> impacts, @NotNull List<DownstreamRename> newRenames) {
        List<DownstreamRename> keptRenames = new ArrayList<>(renames);
        keptRenames.removeIf(old -> newRenames.stream().anyMatch(n -> n.tableFullName().equals(old.tableFullName())
                && n.oldName().equalsIgnoreCase(old.oldName())));
        keptRenames.addAll(newRenames);
        renames = List.copyOf(keptRenames);
        Map<String, Explanation> merged = new HashMap<>(explanations);
        for (ColumnImpact impact : impacts) {
            for (DryRunBuildProblem failure : impact.affected()) {
                merged.put(failure.actionFullName(), new Explanation(impact.change(), failure.details()));
            }
        }
        explanations = Map.copyOf(merged);
        modificationCount.incrementAndGet();
        DataformEditorRefresher.refresh(project);
    }

    private @NotNull Baseline snapshot() {
        Map<String, List<ColumnInfo>> columns = new LinkedHashMap<>();
        DataformTableSchemaService.getInstance(project).getAllTables()
                .forEach((name, table) -> columns.put(name, table.getColumns()));
        return new Baseline(columns, DryRunErrorRegistry.getInstance(project).getErrors());
    }

    private void check() {
        boolean failed = false;
        try {
            while (!project.isDisposed()) {
                Baseline taken;
                synchronized (lock) {
                    taken = baseline;
                }
                CompiledGraph graph = DataformCompilationService.getInstance(project).upToDateGraph().get();
                if (graph == null || CompilationFailures.isTotalFailure(graph)) break;
                Set<String> failedFiles = CompilationFailures.fileNamesOf(graph);
                SchemaRefreshOutcome outcome = DataformTableSchemaService.getInstance(project)
                        .refresh(graph, false, failedFiles).get();
                synchronized (lock) {
                    if (!DataformCompilationService.getInstance(project).isUpToDate()) continue;
                    baseline = null;
                }
                if (outcome.extracted()) report(taken, graph, failedFiles);
                return;
            }
            failed = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed = true;
        } catch (Exception e) {
            LOG.warn("Column impact check failed", e);
            failed = true;
        } finally {
            boolean again;
            synchronized (lock) {
                if (failed) baseline = null;
                again = baseline != null && !project.isDisposed();
                checking = again;
            }
            if (again) AppExecutorUtil.getAppExecutorService().execute(this::check);
        }
    }

    private void report(@NotNull Baseline before, @NotNull CompiledGraph graph, @NotNull Set<String> failedFiles) {
        Baseline after = snapshot();
        List<ColumnChange> changes = ColumnChanges.between(before.columns(), after.columns());
        if (changes.isEmpty()) return;
        Set<String> newFailures = after.errors().entrySet().stream()
                .filter(e -> !Objects.equals(e.getValue(), before.errors().get(e.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<DryRunBuildProblem> failures = ReadAction
                .nonBlocking(() -> DryRunBuildProblems.collect(project, graph, failedFiles))
                .executeSynchronously().stream()
                .filter(p -> newFailures.contains(p.actionFullName()))
                .toList();
        Map<String, Set<String>> reads = readsOf(graph);
        List<ColumnImpact> impacts = ColumnImpacts.of(changes, failures, reads);
        if (impacts.isEmpty()) return;
        List<DownstreamRename> newRenames = DownstreamRenames.of(changes, reads);
        publish(impacts, newRenames);
        DryRunErrorRegistry registry = DryRunErrorRegistry.getInstance(project);
        Map<DownstreamRename, Integer> pending = new LinkedHashMap<>();
        newRenames.forEach(rename -> {
            int readers = (int) readersStillFailing(rename, registry);
            if (readers > 0) pending.put(rename, readers);
        });
        ApplicationManager.getApplication().invokeLater(() -> {
            ColumnImpactBuildView.show(project, impacts);
            pending.forEach((rename, readers) -> DownstreamRenameNotifier.show(project, rename, readers));
        }, project.getDisposed());
    }

    private static @NotNull Map<String, Set<String>> readsOf(@NotNull CompiledGraph graph) {
        Map<String, Set<String>> reads = new HashMap<>();
        graph.getTables().forEach(t -> addReads(reads, t.getTarget(), t.getDependencyTargets()));
        graph.getOperations().forEach(o -> addReads(reads, o.getTarget(), o.getDependencyTargets()));
        return reads;
    }

    private static void addReads(@NotNull Map<String, Set<String>> reads,
                                 @Nullable Target target,
                                 @Nullable List<Target> dependencies) {
        if (target == null || target.getFullName() == null || dependencies == null) return;
        Set<String> names = reads.computeIfAbsent(target.getFullName(), k -> new HashSet<>());
        dependencies.stream()
                .filter(d -> d != null && d.getFullName() != null)
                .forEach(d -> names.add(d.getFullName()));
    }
}
