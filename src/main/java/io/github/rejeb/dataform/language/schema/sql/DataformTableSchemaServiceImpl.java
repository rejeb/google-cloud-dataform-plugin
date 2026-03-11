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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.github.rejeb.dataform.language.compilation.model.*;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.util.DataformAuthNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public final class DataformTableSchemaServiceImpl implements DataformTableSchemaService {

    private static final Logger LOG = Logger.getInstance(DataformTableSchemaServiceImpl.class);

    private final Project project;
    private final ConcurrentHashMap<String, List<ColumnInfo>> schemaCache = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean pendingRefresh = false;
    private volatile CompiledGraph pendingGraph = null;

    public DataformTableSchemaServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    public void refreshAsync(@NotNull CompiledGraph graph) {
        if (running.compareAndSet(false, true)) {
            pendingRefresh = false;
            pendingGraph = null;
            startTask(graph);
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

    private void startTask(@NotNull CompiledGraph graph) {
        new Task.Backgroundable(project, "Extracting Dataform table schemas…", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    runExtraction(graph, indicator);
                    ApplicationManager.getApplication().invokeLater(() ->
                            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                    );
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
                               @NotNull ProgressIndicator indicator) {
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
        int total = sorted.size();
        Map<String, List<ColumnInfo>> resolvedInThisRun = new HashMap<>();

        for (int i = 0; i < total; i++) {
            if (indicator.isCanceled()) {
                LOG.debug("Schema extraction cancelled after " + i + "/" + total);
                return;
            }

            SortableAction action = sorted.get(i);
            String fqn = action.target().getFullName();
            indicator.setText("Dataform schema: " + fqn);
            indicator.setFraction((double) i / total);

            if (action.isTable()) {
                processTable(action.table(), fqn, projectId, location, resolvedInThisRun, extractor);
            } else if (action.isOperation()) {
                processOperation(action.operation(), fqn, projectId, location, resolvedInThisRun, extractor);
            } else if (action.isDeclaration()) {
                processDeclaration(fqn, projectId, location, extractor);
            }
        }

        indicator.setFraction(1.0);
        LOG.info("Schema extraction complete: " + resolvedInThisRun.size() + "/" + total + " actions resolved");
    }

    private void processTable(@NotNull CompiledTable table,
                              @NotNull String fqn,
                              @NotNull String projectId,
                              @Nullable String location,
                              @NotNull Map<String, List<ColumnInfo>> resolvedInThisRun,
                              @NotNull BigQueryDryRunSchemaExtractor extractor) {
        try {
            String dryRunQuery = ReadAction.compute(() ->
                    DataformCteQueryBuilder.buildDryRunQuery(
                            table.getQuery(),
                            resolvedInThisRun,
                            project
                    )
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

    private void processOperation(@NotNull CompiledOperation operation,
                                  @NotNull String fqn,
                                  @NotNull String projectId,
                                  @Nullable String location,
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

    private void processDeclaration(@NotNull String fqn,
                                    @NotNull String projectId,
                                    @Nullable String location,
                                    @NotNull BigQueryDryRunSchemaExtractor extractor) {
        try {
            String dryRunQuery = "SELECT * FROM `" + fqn + "` LIMIT 0";

            List<ColumnInfo> columns = extractor.extractSchema(projectId, location, dryRunQuery);
            if (!columns.isEmpty()) {
                schemaCache.put(fqn, columns);
                LOG.info("Resolved schema for declaration " + fqn + ": " + columns.size() + " columns");
            }
        } catch (Exception e) {
            LOG.warn("Schema extraction failed for declaration " + fqn + ": " + e.getMessage());
        }
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
