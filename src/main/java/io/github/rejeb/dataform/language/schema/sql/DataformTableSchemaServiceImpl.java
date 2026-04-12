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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.compilation.model.*;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.util.DataformAuthNotifier;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@State(
        name = "DataformTableSchemaService",
        storages = @Storage(value = "dataform-table-schema.xml")
)
public final class DataformTableSchemaServiceImpl implements DataformTableSchemaService {

    private static final Logger LOG = Logger.getInstance(DataformTableSchemaServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, SchemaCacheEntry>>() {
    }.getType();

    private final Project project;
    private final ConcurrentHashMap<String, List<ColumnInfo>> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, Long> fileModificationTimes = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean pendingRefresh = false;
    private volatile CompiledGraph pendingGraph = null;
    private State currentState = new State();

    public DataformTableSchemaServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @Nullable State getState() {
        return currentState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.currentState = state;
        restoreCacheFromState(state);
    }

    public void refreshAsync(@NotNull CompiledGraph graph) {
        refreshAsync(graph, false);
    }

    public void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh) {
        if (running.compareAndSet(false, true)) {
            pendingRefresh = false;
            pendingGraph = null;
            startTask(graph, forceRefresh);
        } else {
            pendingGraph = graph;
            pendingRefresh = true;
            LOG.debug("Schema extraction already running, will re-run after completion");
        }
    }

    @NotNull
    public Map<String, List<ColumnInfo>> getAllSchemas() {
        return Collections.unmodifiableMap(schemaCache);
    }

    private void startTask(@NotNull CompiledGraph graph, boolean forceRefresh) {
        new Task.Backgroundable(project, "Extracting Dataform table schemas…", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    runExtraction(graph, indicator, forceRefresh);
                } catch (Exception e) {
                    LOG.warn("Schema extraction failed: " + e);
                } finally {
                    onTaskFinished();
                }
            }
        }.queue();
    }

    private void onTaskFinished() {
        running.set(false);
        if (pendingRefresh && pendingGraph != null) {
            CompiledGraph next = pendingGraph;
            pendingGraph = null;
            pendingRefresh = false;
            refreshAsync(next);
        }
    }

    private void runExtraction(@NotNull CompiledGraph graph,
                               @NotNull ProgressIndicator indicator,
                               boolean forceRefresh) {
        ExtractionContext ctx = buildContext(graph);
        if (ctx == null) return;

        List<SortableAction> sorted = DataformTopologicalSorter.sort(graph);
        List<SortableAction> toRefresh = identifyActionsToRefresh(sorted, forceRefresh);
        logSkipped(sorted.size(), toRefresh.size());

        List<List<SortableAction>> waves = computeWaves(toRefresh);
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                processAllWaves(waves, ctx, indicator));
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
            DataformAuthNotifier.notifyAuthRequired(project);
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
                persistStateFromCache();
                return;
            }
            waveExtraction(ctx, indicator, resolvedInThisRun, wave, processed, total);
        }
        indicator.setFraction(1);
        persistStateFromCache();
        LOG.warn("Schema extraction complete: " + resolvedInThisRun.size() + "/" + processed.get() + " actions resolved");
    }

    private void waveExtraction(ExtractionContext ctx,
                                ProgressIndicator indicator,
                                Map<String, List<ColumnInfo>> resolvedInThisRun,
                                List<SortableAction> wave,
                                AtomicInteger processed,
                                int total) {
        wave.parallelStream().forEach(action -> {
            extractSchema(ctx, resolvedInThisRun, action);
            indicator.setFraction((double) processed.incrementAndGet() / total);
        });

    }

    private void extractSchema(ExtractionContext ctx,
                               @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun,
                               @NotNull SortableAction action) {
        String fqn = action.target().getFullName();
        LOG.info("Resolving schema for: " + fqn);
        Optional<List<ColumnInfo>> result = computeSchema(action, ctx, resolvedInThisRun);
        result.ifPresent(columns -> publishResult(fqn, columns, resolvedInThisRun));
    }


    @NotNull
    private Optional<List<ColumnInfo>> computeSchema(@NotNull SortableAction action,
                                                     @NotNull ExtractionContext ctx,
                                                     @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun) {
        try {
            if (action.isTable()) return extractTableSchema(action.table(), ctx, resolvedInThisRun);
            if (action.isOperation()) return extractOperationSchema(action.operation(), ctx);
            if (action.isDeclaration()) return extractDeclarationSchema(action.target().getFullName(), ctx);
        } catch (Exception e) {
            LOG.warn("Schema extraction failed for " + action.target().getFullName() + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    @NotNull
    private Optional<List<ColumnInfo>> extractTableSchema(@NotNull CompiledTable table,
                                                          @NotNull ExtractionContext ctx,
                                                          @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun) {
        String query = ReadAction.computeBlocking(() ->
                DataformCteQueryBuilder.buildDryRunQuery(table.getQuery(), resolvedInThisRun, project)
        );
        return runDryRun(ctx, query);
    }

    @NotNull
    private Optional<List<ColumnInfo>> extractOperationSchema(@NotNull CompiledOperation operation,
                                                              @NotNull ExtractionContext ctx) {
        List<String> queries = operation.getQueries();
        if (queries.isEmpty()) return Optional.empty();
        String lastQuery = queries.getLast();
        if (lastQuery == null || lastQuery.isBlank()) return Optional.empty();
        return runDryRun(ctx, lastQuery);
    }

    @NotNull
    private Optional<List<ColumnInfo>> extractDeclarationSchema(@NotNull String fqn,
                                                                @NotNull ExtractionContext ctx) {
        return runDryRun(ctx, "SELECT * FROM `" + fqn + "` LIMIT 0");
    }

    @NotNull
    private Optional<List<ColumnInfo>> runDryRun(@NotNull ExtractionContext ctx, @NotNull String query) {
        List<ColumnInfo> columns = ctx.extractor().extractSchema(ctx.projectId(), ctx.location(), query);
        return columns.isEmpty() ? Optional.empty() : Optional.of(columns);
    }

    private void publishResult(@NotNull String fqn,
                               @NotNull List<ColumnInfo> columns,
                               @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun) {
        schemaCache.put(fqn, columns);
        resolvedInThisRun.put(fqn, columns);
        LOG.info("Resolved schema for " + fqn + ": " + columns.size() + " columns");
    }

    @NotNull
    private List<List<SortableAction>> computeWaves(@NotNull List<SortableAction> actions) {
        Map<String, Integer> levelByFqn = computeLevelByFqn(actions);
        int maxLevel = levelByFqn.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<List<SortableAction>> waves = new ArrayList<>(maxLevel + 1);
        for (int i = 0; i <= maxLevel; i++) waves.add(new ArrayList<>());

        actions.forEach(a -> waves.get(levelByFqn.getOrDefault(a.target().getFullName(), 0)).add(a));

        LOG.info("Computed " + (maxLevel + 1) + " wave(s) for " + actions.size() + " actions");
        return waves;
    }

    @NotNull
    private Map<String, Integer> computeLevelByFqn(@NotNull List<SortableAction> actions) {
        Map<String, SortableAction> byFqn = actions.stream()
                .collect(Collectors.toMap(a -> a.target().getFullName(), a -> a, (a, b) -> a));
        Map<String, Integer> levels = new HashMap<>();
        Set<String> fqns = byFqn.keySet();
        actions.forEach(a -> resolveLevel(a, byFqn, fqns, levels, new HashSet<>()));
        return levels;
    }

    private int resolveLevel(@NotNull SortableAction action,
                             @NotNull Map<String, SortableAction> byFqn,
                             @NotNull Set<String> knownFqns,
                             @NotNull Map<String, Integer> levels,
                             @NotNull Set<String> visiting) {
        String fqn = action.target().getFullName();
        if (levels.containsKey(fqn)) return levels.get(fqn);
        if (!visiting.add(fqn)) return 0;

        int maxDepLevel = action.dependencyTargets().stream()
                .filter(dep -> knownFqns.contains(dep.getFullName()))
                .mapToInt(dep -> resolveLevel(byFqn.get(dep.getFullName()), byFqn, knownFqns, levels, visiting))
                .max()
                .orElse(-1);

        int level = maxDepLevel + 1;
        levels.put(fqn, level);
        visiting.remove(fqn);
        return level;
    }

    // -------------------------------------------------------------------------
    // Actions to refresh (change detection)
    // -------------------------------------------------------------------------

    @NotNull
    private List<SortableAction> identifyActionsToRefresh(@NotNull List<SortableAction> allActions,
                                                          boolean forceRefresh) {
        if (forceRefresh) {
            LOG.info("Force refresh enabled, refreshing all actions");
            return allActions;
        }
        String basePath = project.getBasePath();
        if (basePath == null) {
            LOG.warn("Project base path is null, refreshing all actions");
            return allActions;
        }
        Set<String> modifiedFqns = collectModifiedFqns(allActions, basePath);
        Set<String> affectedFqns = propagateToDependents(modifiedFqns, allActions);
        return allActions.stream()
                .filter(a -> affectedFqns.contains(a.target().getFullName()))
                .collect(Collectors.toList());
    }

    @NotNull
    private Set<String> collectModifiedFqns(@NotNull List<SortableAction> actions,
                                            @NotNull String basePath) {
        Set<String> modified = new HashSet<>();
        for (SortableAction action : actions) {
            String fqn = action.target().getFullName();
            String fileName = getFileNameFromAction(action);
            if (isModified(fqn, fileName, basePath)) modified.add(fqn);
        }
        return modified;
    }

    private boolean isModified(@NotNull String fqn, @Nullable String fileName, @NotNull String basePath) {
        if (fileName == null) return !schemaCache.containsKey(fqn);
        long currentModTime = getFileModificationTime(basePath, fileName);
        fileModificationTimes.put(fqn, currentModTime);
        Long cached = getCachedModTime(fqn);
        return cached == null || cached < currentModTime;
    }

    @NotNull
    private Set<String> propagateToDependents(@NotNull Set<String> modifiedFqns,
                                              @NotNull List<SortableAction> allActions) {
        Map<String, Set<String>> reverseDeps = buildReverseDependencies(allActions);
        Set<String> affected = new HashSet<>(modifiedFqns);
        Queue<String> queue = new LinkedList<>(modifiedFqns);

        while (!queue.isEmpty()) {
            reverseDeps.getOrDefault(queue.poll(), Set.of()).stream()
                    .filter(affected::add)
                    .forEach(queue::add);
        }

        logPropagation(modifiedFqns.size(), affected.size());
        return affected;
    }

    @NotNull
    private Map<String, Set<String>> buildReverseDependencies(@NotNull List<SortableAction> actions) {
        Map<String, Set<String>> reverse = new HashMap<>();
        for (SortableAction action : actions) {
            String fqn = action.target().getFullName();
            action.dependencyTargets().forEach(dep ->
                    reverse.computeIfAbsent(dep.getFullName(), k -> new HashSet<>()).add(fqn)
            );
        }
        return reverse;
    }

    // -------------------------------------------------------------------------
    // State persistence
    // -------------------------------------------------------------------------

    private void restoreCacheFromState(@NotNull State state) {
        if (state.schemaCacheJson == null || state.schemaCacheJson.isBlank()) return;
        try {
            Map<String, SchemaCacheEntry> loaded = GSON.fromJson(state.schemaCacheJson, CACHE_TYPE);
            if (loaded == null) return;
            schemaCache.clear();
            loaded.forEach((fqn, entry) -> schemaCache.put(fqn, entry.columns()));
            LOG.info("Restored " + schemaCache.size() + " schemas from persistent state");
        } catch (Exception e) {
            LOG.warn("Failed to deserialize schema cache from state: " + e.getMessage());
            schemaCache.clear();
            this.currentState = new State();
        }
    }

    private void persistStateFromCache() {
        Map<String, SchemaCacheEntry> toSerialize = schemaCache.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new SchemaCacheEntry(e.getValue(),
                                fileModificationTimes.getOrDefault(e.getKey(), Long.MAX_VALUE))
                ));
        currentState.schemaCacheJson = GSON.toJson(toSerialize);
        LOG.info("Persisted " + toSerialize.size() + " schemas to state");
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    @Nullable
    private Long getCachedModTime(@NotNull String fqn) {
        if (currentState.schemaCacheJson == null) return null;
        try {
            Map<String, SchemaCacheEntry> parsed = GSON.fromJson(currentState.schemaCacheJson, CACHE_TYPE);
            if (parsed == null) return null;
            SchemaCacheEntry entry = parsed.get(fqn);
            return entry != null ? entry.lastModified() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private String getFileNameFromAction(@NotNull SortableAction action) {
        if (action.isTable() && action.table() != null) return action.table().getFileName();
        if (action.isOperation() && action.operation() != null) return action.operation().getFileName();
        return null;
    }

    private long getFileModificationTime(@NotNull String basePath, @NotNull String fileName) {
        try {
            Path filePath = Paths.get(basePath, fileName);
            if (Files.exists(filePath)) return Files.getLastModifiedTime(filePath).toMillis();
        } catch (Exception e) {
            LOG.debug("Failed to get modification time for " + fileName + ": " + e.getMessage());
        }
        return System.currentTimeMillis();
    }

    private boolean hasValidCredentials() {
        try {
            GcpClientsUtils.getCredentials().refresh();
            return true;
        } catch (Exception e) {
            LOG.warn("Google credentials not available: " + e.getMessage());
            return false;
        }
    }

    private void logSkipped(int total, int toRefresh) {
        int skipped = total - toRefresh;
        if (skipped > 0) LOG.info("Skipping " + skipped + " unchanged actions, refreshing " + toRefresh);
    }

    private void logPropagation(int modifiedCount, int affectedCount) {
        if (affectedCount > modifiedCount) {
            LOG.info("Propagated refresh to " + (affectedCount - modifiedCount)
                    + " dependent actions (" + modifiedCount + " directly modified)");
        }
    }
}