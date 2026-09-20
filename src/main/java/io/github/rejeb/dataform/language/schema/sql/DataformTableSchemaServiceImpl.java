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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.compilation.model.*;
import io.github.rejeb.dataform.language.diagnostics.DataformEditorRefresher;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.DataformAuthState;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import io.github.rejeb.dataform.language.util.PreOperationsFilter;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link DataformTableSchemaService}. Runs one extraction at a time, asking
 * {@link SchemaRefreshPlanner} what to dry-run and {@link SchemaCacheStore} to hold the results.
 */
@State(
        name = "DataformTableSchemaService",
        storages = @Storage(value = StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)
)
public final class DataformTableSchemaServiceImpl
        implements DataformTableSchemaService, Disposable {

    private static final Logger LOG = Logger.getInstance(DataformTableSchemaServiceImpl.class);

    private final Project project;
    private final SchemaCacheStore cache;
    private static final int DRY_RUN_PARALLELISM = 4;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong modificationCount = new AtomicLong(0);
    private final ExecutorService dryRunExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
            "Dataform schema extraction", DRY_RUN_PARALLELISM);
    private volatile boolean watchingDocuments = false;
    private final Object pendingLock = new Object();
    private PendingRefresh pending;

    private record PendingRefresh(@NotNull CompiledGraph graph,
                                  boolean forceRefresh,
                                  @NotNull Set<String> failedFileNames) {
        PendingRefresh mergedWith(@NotNull CompiledGraph graph,
                                  boolean forceRefresh,
                                  @NotNull Set<String> failedFileNames) {
            return new PendingRefresh(graph, this.forceRefresh || forceRefresh, failedFileNames);
        }
    }

    public DataformTableSchemaServiceImpl(@NotNull Project project) {
        this.project = project;
        this.cache = new SchemaCacheStore(project);
    }

    @Override
    public @Nullable DataformTableSchemaService.State getState() {
        return cache.state();
    }

    @Override
    public void loadState(@NotNull DataformTableSchemaService.State state) {
        cache.load(state);
        modificationCount.incrementAndGet();
    }

    @Override
    public long getModificationCount() {
        return modificationCount.get();
    }

    public void refreshAsync(@NotNull CompiledGraph graph) {
        refreshAsync(graph, false, Set.of());
    }

    @Override
    public void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh) {
        refreshAsync(graph, forceRefresh, Set.of());
    }

    @Override
    public void refreshAsync(@NotNull CompiledGraph graph,
                             boolean forceRefresh,
                             @NotNull Set<String> failedFileNames) {
        synchronized (pendingLock) {
            if (!running.compareAndSet(false, true)) {
                pending = pending == null
                        ? new PendingRefresh(graph, forceRefresh, failedFileNames)
                        : pending.mergedWith(graph, forceRefresh, failedFileNames);
                LOG.debug("Schema extraction already running, will re-run after completion");
                return;
            }
            pending = null;
        }
        evictStaleEntries(graph);
        startTask(graph, forceRefresh, failedFileNames);
    }

    @NotNull
    public Map<String, DataformDasTable> getAllTables() {
        return cache.published();
    }

    @Override
    @NotNull
    public List<DataformDasTable> getTablesNamed(@NotNull String name) {
        return cache.publishedNamed(name);
    }

    @Override
    public void renameColumn(@NotNull Set<ColumnRef> columns,
                             @NotNull String newName,
                             @NotNull Collection<VirtualFile> written) {
        boolean changed = false;
        for (ColumnRef column : columns) {
            changed |= cache.rename(column.tableFullName(), column.columnName(), newName);
        }
        if (!changed) return;
        cache.guessedFrom(written);
        watchWhatWasGuessed();
        announce();
    }

    /**
     * Watches the documents for as long as a schema holds what a rename wrote rather than what an
     * extraction read, and puts the read answer back as soon as one of those files says something
     * else. Undoing a rename or rolling it back would otherwise leave the editor resolving against
     * a name no file carries any more, until the next extraction — or the next IDE run.
     */
    private void watchWhatWasGuessed() {
        if (watchingDocuments || project.isDisposed()) return;
        watchingDocuments = true;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void documentChanged(@NotNull DocumentEvent event) {
                        if (cache.dropStaleGuesses()) announceAfterTheDocumentChange();
                    }
                }, this);
    }

    /** Publishes what the cache now holds and tells everything reading it that it changed. */
    private void announce() {
        cache.publish();
        modificationCount.incrementAndGet();
        notifyReaders();
    }

    /**
     * The same from inside a document change, where the telling has to wait.
     *
     * <p>A document listener runs while the document is being written and before the PSI of the file
     * is committed. The subscribers of the topic and the editors this repaints read the PSI of what
     * they show, which they may not do on a document the platform has not caught up with yet. What
     * the cache holds is put right on the spot, so resolution asking a moment later gets the
     * corrected answer either way.</p>
     */
    private void announceAfterTheDocumentChange() {
        cache.publish();
        modificationCount.incrementAndGet();
        ApplicationManager.getApplication()
                .invokeLater(this::notifyReaders, ModalityState.defaultModalityState());
    }

    private void notifyReaders() {
        if (project.isDisposed()) return;
        project.getMessageBus().syncPublisher(DataformSchemaEvent.TOPIC).onSchemasUpdated();
        DataformEditorRefresher.refresh(project);
    }

    /**
     * Drops cached schemas of actions the compiled graph no longer declares. A forced refresh
     * re-extracts every remaining action but must not empty the cache first: an action whose
     * dry-run fails during that run has to keep the schema it already had.
     */
    private void evictStaleEntries(@NotNull CompiledGraph graph) {
        Set<String> known = new HashSet<>();
        graph.getTables().forEach(t -> addFullName(known, t.getTarget()));
        graph.getOperations().forEach(o -> addFullName(known, o.getTarget()));
        graph.getDeclarations().forEach(d -> addFullName(known, d.getTarget()));
        if (known.isEmpty()) return;
        cache.retainOnly(known);
        DryRunErrorRegistry.getInstance(project).retainOnly(known);
        cache.publish();
    }

    @Override
    public void dispose() {
    }

    private void addFullName(@NotNull Set<String> names, @Nullable Target target) {
        if (target != null && target.getFullName() != null) names.add(target.getFullName());
    }

    private void startTask(@NotNull CompiledGraph graph,
                           boolean forceRefresh,
                           @NotNull Set<String> failedFileNames) {
        new Task.Backgroundable(project, "Extracting Dataform table schemas…", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    runExtraction(graph, indicator, forceRefresh, failedFileNames);
                } catch (Exception e) {
                    LOG.warn("Schema extraction failed: " + e);
                } finally {
                    onTaskFinished();
                }
            }
        }.queue();
    }

    private void onTaskFinished() {
        cache.publish();
        modificationCount.incrementAndGet();
        PendingRefresh next;
        synchronized (pendingLock) {
            running.set(false);
            next = pending;
            pending = null;
        }
        if (next != null) {
            refreshAsync(next.graph(), next.forceRefresh(), next.failedFileNames());
        } else if (!project.isDisposed()) {
            project.getMessageBus().syncPublisher(DataformSchemaEvent.TOPIC).onSchemasUpdated();
        }
    }

    private void runExtraction(@NotNull CompiledGraph graph,
                               @NotNull ProgressIndicator indicator,
                               boolean forceRefresh,
                               @NotNull Set<String> failedFileNames) {
        ExtractionContext ctx = buildContext(graph);
        if (ctx == null) return;

        List<List<SortableAction>> waves = new SchemaRefreshPlanner(project.getBasePath(), cache)
                .planWaves(DataformTopologicalSorter.sort(graph), forceRefresh, failedFileNames);
        if (!waves.isEmpty()) {
            processAllWaves(waves, ctx, indicator);
        }
    }

    @Nullable
    private ExtractionContext buildContext(@NotNull CompiledGraph graph) {
        ProjectConfig config = graph.getProjectConfig();
        if (config == null) {
            LOG.warn("No ProjectConfig in CompiledGraph, skipping schema extraction");
            return null;
        }
        String projectId = config.getDefaultDatabase();
        if (projectId == null || projectId.isBlank()) {
            LOG.warn("ProjectConfig.defaultDatabase is empty, skipping schema extraction");
            return null;
        }
        if (!hasValidCredentials()) {
            DataformAuthState.getInstance().markAuthRequired(AuthTrigger.BACKGROUND);
            return null;
        }
        return new ExtractionContext(projectId, config.getDefaultLocation(),
                project.getService(BigQueryDryRunSchemaExtractor.class));
    }

    private void processAllWaves(@NotNull List<List<SortableAction>> waves,
                                 @NotNull ExtractionContext ctx,
                                 @NotNull ProgressIndicator indicator) {
        Map<String, List<ColumnInfo>> resolvedInThisRun = new ConcurrentHashMap<>();
        int total = waves.stream().mapToInt(List::size).sum();
        AtomicInteger processed = new AtomicInteger(0);
        for (List<SortableAction> wave : waves) {
            if (indicator.isCanceled()) {
                LOG.debug("Schema extraction cancelled");
                cache.persist();
                return;
            }
            waveExtraction(ctx, indicator, resolvedInThisRun, wave, processed, total);
        }
        indicator.setFraction(1d);
        cache.persist();
        LOG.warn("Schema extraction complete: " + resolvedInThisRun.size() + "/" + processed.get()
                + " actions resolved");
    }

    /**
     * Dry-runs the actions of one wave concurrently on a bounded pool of the platform rather than
     * on the common fork-join pool, which network calls must not tie up, and under the indicator of
     * the extraction so cancellation reaches every dry-run.
     */
    private void waveExtraction(ExtractionContext ctx,
                                ProgressIndicator indicator,
                                Map<String, List<ColumnInfo>> resolvedInThisRun,
                                List<SortableAction> wave,
                                AtomicInteger processed,
                                int total) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(wave.size());
        for (SortableAction action : wave) {
            futures.add(CompletableFuture.runAsync(() -> {
                if (indicator.isCanceled()) return;
                ProgressManager.getInstance().executeProcessUnderProgress(() -> {
                    extractSchema(ctx, resolvedInThisRun, action);
                    indicator.setFraction((double) processed.incrementAndGet() / total);
                }, indicator);
            }, dryRunExecutor));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void extractSchema(ExtractionContext ctx,
                               @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun,
                               @NotNull SortableAction action) {
        String fqn = action.target().getFullName();
        LOG.info("Resolving schema for: " + fqn);
        DryRunResult result = computeSchema(action, ctx, resolvedInThisRun);
        recordDryRunOutcome(fqn, result);
        if (!result.columns().isEmpty()) publishResult(action, result.columns(), resolvedInThisRun);
    }

    /**
     * Keeps the dry-run failure of an action, or drops the previous one once it runs clean, so the
     * query view can report why a schema is missing.
     */
    private void recordDryRunOutcome(@NotNull String fqn, @NotNull DryRunResult result) {
        DryRunErrorRegistry registry = DryRunErrorRegistry.getInstance(project);
        if (result.hasError()) {
            registry.report(fqn, result.errorMessage());
        } else {
            registry.clear(fqn);
        }
    }

    @NotNull
    private DryRunResult computeSchema(@NotNull SortableAction action,
                                       @NotNull ExtractionContext ctx,
                                       @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun) {
        try {
            if (action.isTable()) return extractTableSchema(action.table(), ctx, resolvedInThisRun);
            if (action.isOperation()) return extractOperationSchema(action.operation(), ctx);
            if (action.isDeclaration()) return extractDeclarationSchema(action.target().getFullName(), ctx);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Schema extraction failed for " + action.target().getFullName() + ": " + e.getMessage());
            return DryRunResult.failure(e.getMessage() != null ? e.getMessage() : e.toString());
        }
        return DryRunResult.empty();
    }

    @NotNull
    private DryRunResult extractTableSchema(@NotNull CompiledTable table,
                                            @NotNull ExtractionContext ctx,
                                            @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun) {
        String mainQuery = ReadAction.computeBlocking(() ->
                DataformCteQueryBuilder.buildDryRunQuery(table.getQuery(), resolvedInThisRun, project)
        );
        String query = Utils.withPreOperations(
                PreOperationsFilter.keepReadOnly(table.getPreOps()), mainQuery);
        return runDryRun(ctx, query);
    }

    @NotNull
    private DryRunResult extractOperationSchema(@NotNull CompiledOperation operation,
                                                @NotNull ExtractionContext ctx) {
        return OperationSchemaExtraction.extract(operation, ctx);
    }

    @NotNull
    private DryRunResult extractDeclarationSchema(@NotNull String fqn,
                                                  @NotNull ExtractionContext ctx) {
        return runDryRun(ctx, OperationSchemaExtraction.tableQuery(fqn));
    }

    @NotNull
    private DryRunResult runDryRun(@NotNull ExtractionContext ctx, @NotNull String query) {
        return ctx.extractor().extractSchema(ctx.projectId(), ctx.location(), query);
    }

    private void publishResult(@NotNull SortableAction action,
                               @NotNull List<ColumnInfo> columns,
                               @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun) {
        String fqn = action.target().getFullName();
        cache.put(fqn, action.target().getName(), columns, ActionSourceFiles.fileNameOf(action));
        resolvedInThisRun.put(fqn, columns);
        LOG.info("Resolved schema for " + fqn + ": " + columns.size() + " columns");
    }

    private boolean hasValidCredentials() {
        try {
            return DataformCredentialsService.getInstance().isSignedIn();
        } catch (Exception e) {
            LOG.warn("Google credentials not available: " + e.getMessage());
            return false;
        }
    }
}
