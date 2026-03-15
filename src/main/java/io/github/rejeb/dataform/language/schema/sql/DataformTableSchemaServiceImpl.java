/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.github.rejeb.dataform.language.compilation.model.*;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.util.DataformAuthNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@State(
        name = "DataformTableSchemaService",
        storages = @Storage(
                value = "dataform-table-schema.xml")
)
public final class DataformTableSchemaServiceImpl
        implements DataformTableSchemaService {

    private static final Logger LOG = Logger.getInstance(DataformTableSchemaServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type CACHE_TYPE =
            new TypeToken<Map<String, SchemaCacheEntry>>() {
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
        if (state.schemaCacheJson != null && !state.schemaCacheJson.isBlank()) {
            try {
                Map<String, SchemaCacheEntry> loaded = GSON.fromJson(state.schemaCacheJson, CACHE_TYPE);
                if (loaded != null) {
                    schemaCache.clear();
                    loaded.forEach((fqn, entry) -> schemaCache.put(fqn, entry.columns()));
                    LOG.info("Restored " + schemaCache.size() + " schemas from persistent state");
                }
            } catch (Exception e) {
                LOG.warn("Failed to deserialize schema cache from state: " + e.getMessage());
                schemaCache.clear();
                this.currentState = new State();
            }
        }
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
                    running.set(false);
                    if (pendingRefresh && pendingGraph != null) {
                        CompiledGraph next = pendingGraph;
                        pendingGraph = null;
                        pendingRefresh = false;
                        refreshAsync(next);
                    }
                }
            }
        }.queue();
    }

    private void runExtraction(@NotNull CompiledGraph graph,
                               @NotNull ProgressIndicator indicator,
                               boolean forceRefresh) {
        ProjectConfig config = graph.getProjectConfig();
        if (config == null) {
            LOG.warn("No ProjectConfig in CompiledGraph, skipping schema extraction");
            return;
        }

        String projectId = config.getDefaultDatabase();
        String location = config.getDefaultLocation();

        if (projectId == null || projectId.isBlank()) {
            LOG.warn("ProjectConfig.defaultDatabase is empty, skipping schema extraction");
            return;
        }

        if (!hasValidCredentials()) {
            DataformAuthNotifier.notifyAuthRequired(project);
            return;
        }

        BigQueryDryRunSchemaExtractor extractor =
                project.getService(BigQueryDryRunSchemaExtractor.class);

        List<SortableAction> sorted = DataformTopologicalSorter.sort(graph);
        List<SortableAction> actionsToRefresh = identifyActionsToRefresh(sorted, forceRefresh);

        int total = actionsToRefresh.size();
        int skipped = sorted.size() - total;
        if (skipped > 0) {
            LOG.info("Skipping " + skipped + " unchanged actions, refreshing " + total);
        }

        Map<String, List<ColumnInfo>> resolvedInThisRun = new HashMap<>();

        for (int i = 0; i < total; i++) {
            if (indicator.isCanceled()) {
                LOG.debug("Schema extraction cancelled after " + i + "/" + total);
                persistStateFromCache();  // save progress so far
                return;
            }

            SortableAction action = actionsToRefresh.get(i);
            String fqn = action.target().getFullName();
            indicator.setText("Dataform schema: " + fqn);
            indicator.setFraction((double) i / total);

            if (action.isTable()) {
                LOG.info("Resolving schema for table: " + fqn);
                processTable(action.table(), fqn, projectId, location, resolvedInThisRun, extractor);
            } else if (action.isOperation()) {
                LOG.info("Resolving schema for operation: " + fqn);
                processOperation(action.operation(), fqn, projectId, location, resolvedInThisRun, extractor);
            } else if (action.isDeclaration()) {
                LOG.info("Resolving schema for declared table: " + fqn);
                processDeclaration(fqn, projectId, location, extractor);
            }
        }

        indicator.setFraction(1.0);
        persistStateFromCache();  // replaces persistentCache.saveToDisk()

        LOG.info("Schema extraction complete: " + resolvedInThisRun.size() + "/" + total + " actions resolved");
    }

    private void persistStateFromCache() {
        Map<String, SchemaCacheEntry> toSerialize = new HashMap<>();
        schemaCache.forEach((fqn, columns) -> {
            long modTime = fileModificationTimes.getOrDefault(fqn, Long.MAX_VALUE);
            toSerialize.put(fqn, new SchemaCacheEntry(columns, modTime));
        });
        currentState.schemaCacheJson = GSON.toJson(toSerialize);
        LOG.info("Persisted " + toSerialize.size() + " schemas to state");
    }

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

        Set<String> modifiedFqns = new HashSet<>();

        for (SortableAction action : allActions) {
            String fqn = action.target().getFullName();
            String fileName = getFileNameFromAction(action);

            if (fileName == null) {
                if (!schemaCache.containsKey(fqn)) {
                    modifiedFqns.add(fqn);
                }
                continue;
            }

            long currentModTime = getFileModificationTime(basePath, fileName);
            fileModificationTimes.put(fqn, currentModTime);

            Long cachedModTime = getCachedModTime(fqn);
            if (cachedModTime == null || cachedModTime < currentModTime) {
                modifiedFqns.add(fqn);
            }
        }

        Set<String> allAffectedFqns = propagateToDependents(modifiedFqns, allActions);

        return allActions.stream()
                .filter(a -> allAffectedFqns.contains(a.target().getFullName()))
                .collect(Collectors.toList());
    }

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

    private Set<String> propagateToDependents(@NotNull Set<String> modifiedFqns,
                                              @NotNull List<SortableAction> allActions) {
        Set<String> affected = new HashSet<>(modifiedFqns);
        Map<String, Set<String>> reverseDependencies = new HashMap<>();

        for (SortableAction action : allActions) {
            String fqn = action.target().getFullName();
            for (Target dep : action.dependencyTargets()) {
                reverseDependencies
                        .computeIfAbsent(dep.getFullName(), k -> new HashSet<>())
                        .add(fqn);
            }
        }

        Queue<String> toProcess = new LinkedList<>(modifiedFqns);
        while (!toProcess.isEmpty()) {
            String currentFqn = toProcess.poll();
            Set<String> dependents = reverseDependencies.get(currentFqn);
            if (dependents != null) {
                for (String dependent : dependents) {
                    if (affected.add(dependent)) {
                        toProcess.add(dependent);
                    }
                }
            }
        }

        if (affected.size() > modifiedFqns.size()) {
            LOG.info("Propagated refresh to " + (affected.size() - modifiedFqns.size()) +
                    " dependent actions (" + modifiedFqns.size() + " directly modified)");
        }
        return affected;
    }

    private void processTable(@NotNull CompiledTable table, @NotNull String fqn,
                              @NotNull String projectId, @Nullable String location,
                              @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun,
                              @NotNull BigQueryDryRunSchemaExtractor extractor) {
        try {
            String dryRunQuery = ReadAction.compute(() ->
                    DataformCteQueryBuilder.buildDryRunQuery(table.getQuery(), resolvedInThisRun, project)
            );
            List<ColumnInfo> columns = extractor.extractSchema(projectId, location, dryRunQuery);
            if (!columns.isEmpty()) {
                schemaCache.put(fqn, columns);
                resolvedInThisRun.put(fqn, columns);
                LOG.info("Resolved schema for " + fqn + ": " + columns.size() + " columns");
            } else {
                LOG.warn("No schema returned for " + fqn);
            }
        } catch (Exception e) {
            LOG.warn("Schema extraction failed for " + fqn + ": " + e.getMessage());
        }
    }

    private void processOperation(@NotNull CompiledOperation operation, @NotNull String fqn,
                                  @NotNull String projectId, @Nullable String location,
                                  @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun,
                                  @NotNull BigQueryDryRunSchemaExtractor extractor) {
        try {
            List<String> queries = operation.getQueries();
            if (queries.isEmpty()) return;
            String dryRunQuery = queries.getLast();
            if (dryRunQuery == null || dryRunQuery.isBlank()) return;
            List<ColumnInfo> columns = extractor.extractSchema(projectId, location, dryRunQuery);
            if (!columns.isEmpty()) {
                schemaCache.put(fqn, columns);
                resolvedInThisRun.put(fqn, columns);
                LOG.info("Resolved schema for operation " + fqn + ": " + columns.size() + " columns");
            }
        } catch (Exception e) {
            LOG.warn("Schema extraction failed for operation " + fqn + ": " + e.getMessage());
        }
    }

    private void processDeclaration(@NotNull String fqn, @NotNull String projectId,
                                    @Nullable String location,
                                    @NotNull BigQueryDryRunSchemaExtractor extractor) {
        try {
            String dryRunQuery = "SELECT * FROM `" + fqn + "` LIMIT 0";
            List<ColumnInfo> columns = extractor.extractSchema(projectId, location, dryRunQuery);
            if (!columns.isEmpty()) {
                schemaCache.put(fqn, columns);
                fileModificationTimes.put(fqn, Long.MAX_VALUE);
                LOG.info("Resolved schema for declaration " + fqn + ": " + columns.size() + " columns");
            }
        } catch (Exception e) {
            LOG.warn("Schema extraction failed for declaration " + fqn + ": " + e.getMessage());
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
            if (Files.exists(filePath)) {
                return Files.getLastModifiedTime(filePath).toMillis();
            }
        } catch (Exception e) {
            LOG.debug("Failed to get modification time for " + fileName + ": " + e.getMessage());
        }
        return System.currentTimeMillis();
    }

    private boolean hasValidCredentials() {
        try {
            com.google.auth.oauth2.GoogleCredentials.getApplicationDefault();
            return true;
        } catch (Exception e) {
            LOG.warn("Google credentials not available: " + e.getMessage());
            return false;
        }
    }
}
