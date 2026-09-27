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
package io.github.rejeb.dataform.language.lineage.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.column.BigQuerySelectAnalyzer;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageExtractor;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.UnitAnalysisRunner;
import io.github.rejeb.dataform.language.lineage.extractor.LineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformSchemaEvent;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link LineageGraphService}. The graphs are rebuilt when the compiled graph or the
 * schemas change and cached otherwise.
 *
 * <p>Once the schemas change, the graphs are built again in the background, their analyses side by
 * side in non-blocking read actions, so that the rename or the usage search that follows finds
 * them ready. A caller that finds them stale still builds them itself, exactly for the graph and
 * schemas it sees: it never reads the previous graph. That build always runs inside a read action,
 * one taken before the lock that lets concurrent callers share it, so the holder of that lock never
 * waits for a read lock that a pending write action and another caller would keep from it.</p>
 */
public final class LineageGraphServiceImpl implements LineageGraphService, Disposable {

    private static final Logger LOG = Logger.getInstance(LineageGraphServiceImpl.class);
    private static final int PREBUILD_PARALLELISM =
            Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private record Cached(@Nullable CompiledGraph compiled, long schemaStamp, @NotNull Graphs graphs) {
        boolean matches(@NotNull CompiledGraph compiled, long schemaStamp) {
            return this.compiled == compiled && this.schemaStamp == schemaStamp
                    && graphs.columnGraph() != null;
        }
    }

    private final Project project;
    private final Object buildLock = new Object();
    private final AtomicLong prebuildRequests = new AtomicLong();
    private final ExecutorService prebuildScheduler =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform lineage prebuild", 1);
    private final ExecutorService analysisExecutor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform lineage analysis", PREBUILD_PARALLELISM);
    private volatile Cached cached = new Cached(null, Long.MIN_VALUE, new Graphs(null, null));

    public LineageGraphServiceImpl(@NotNull Project project) {
        this.project = project;
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            project.getMessageBus().connect(this)
                    .subscribe(DataformSchemaEvent.TOPIC, (DataformSchemaEvent) this::requestPrebuild);
        }
    }

    @Override
    public @NotNull Graphs graphs(@NotNull CompiledGraph compiled) {
        long schemaStamp = schemaStamp();
        Cached current = cached;
        if (current.matches(compiled, schemaStamp)) {
            return current.graphs();
        }
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
            return ReadAction.nonBlocking(() -> graphs(compiled))
                    .expireWith(project)
                    .executeSynchronously();
        }
        synchronized (buildLock) {
            current = cached;
            if (current.matches(compiled, schemaStamp)) {
                return current.graphs();
            }
            Graphs graphs = build(compiled, UnitAnalysisRunner.SEQUENTIAL);
            cached = new Cached(compiled, schemaStamp, graphs);
            return graphs;
        }
    }

    @Override
    public @Nullable ColumnLineageGraph columnGraph() {
        CompiledGraph compiled = DataformCompilationService.getInstance(project).getCompiledGraph();
        return compiled == null ? null : graphs(compiled).columnGraph();
    }

    @Override
    public @Nullable ColumnLineageGraph lastBuiltColumnGraph() {
        return cached.graphs().columnGraph();
    }

    @Override
    public void dispose() {
        prebuildRequests.incrementAndGet();
        prebuildScheduler.shutdownNow();
        analysisExecutor.shutdownNow();
    }

    /**
     * Builds the graphs again in the background, for the latest request only: a burst of schema
     * updates ends in one build.
     */
    private void requestPrebuild() {
        long request = prebuildRequests.incrementAndGet();
        prebuildScheduler.execute(() -> {
            if (request != prebuildRequests.get() || project.isDisposed()) return;
            try {
                prebuild();
            } catch (ProcessCanceledException e) {
                LOG.debug("Lineage prebuild cancelled");
            } catch (RuntimeException e) {
                LOG.warn("Lineage prebuild failed", e);
            }
        });
    }

    private void prebuild() {
        CompiledGraph compiled = DataformCompilationService.getInstance(project).getCompiledGraph();
        if (compiled == null) return;
        long schemaStamp = schemaStamp();
        if (cached.matches(compiled, schemaStamp)) return;
        Graphs graphs = build(compiled, new ParallelReadActionRunner(project, analysisExecutor));
        synchronized (buildLock) {
            boolean current = compiled == DataformCompilationService.getInstance(project).getCompiledGraph()
                    && schemaStamp == schemaStamp();
            if (current && !cached.matches(compiled, schemaStamp)) {
                cached = new Cached(compiled, schemaStamp, graphs);
            }
        }
    }

    private long schemaStamp() {
        return DataformTableSchemaService.getInstance(project).getModificationCount();
    }

    private @NotNull Graphs build(@NotNull CompiledGraph compiled, @NotNull UnitAnalysisRunner runner) {
        LineageGraph tableGraph = new LineageExtractorImpl().extract(compiled);
        ColumnLineageGraph columnGraph = computeColumnGraph(compiled, runner);
        return new Graphs(tableGraph, columnGraph);
    }

    /**
     * Builds the column graph from the schemas of the actions present in the compiled graph.
     * Cached schemas of actions that are no longer part of the graph are ignored, so a stale
     * entry cannot contribute columns to the lineage.
     */
    private @NotNull ColumnLineageGraph computeColumnGraph(@NotNull CompiledGraph compiled,
                                                           @NotNull UnitAnalysisRunner runner) {
        Set<String> actionNames = actionFullNames(compiled);
        Map<String, List<ColumnInfo>> schemas = new LinkedHashMap<>();
        DataformTableSchemaService.getInstance(project).getAllTables()
                .forEach((fqn, table) -> {
                    if (actionNames.contains(fqn)) schemas.put(fqn, table.getColumns());
                });
        ColumnLineageExtractor extractor =
                new ColumnLineageExtractorImpl(new BigQuerySelectAnalyzer(project), runner);
        return extractor.extract(compiled, schemas);
    }

    private @NotNull Set<String> actionFullNames(@NotNull CompiledGraph compiled) {
        Set<String> names = new LinkedHashSet<>();
        compiled.getTables().forEach(t -> addFullName(names, t.getTarget()));
        compiled.getOperations().forEach(o -> addFullName(names, o.getTarget()));
        compiled.getDeclarations().forEach(d -> addFullName(names, d.getTarget()));
        return names;
    }

    private void addFullName(@NotNull Set<String> names, @Nullable Target target) {
        if (target != null && target.getFullName() != null) names.add(target.getFullName());
    }
}
