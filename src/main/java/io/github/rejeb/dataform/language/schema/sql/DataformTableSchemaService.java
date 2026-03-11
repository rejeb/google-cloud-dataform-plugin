package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.ProjectConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


@Service(Service.Level.PROJECT)
public final class DataformTableSchemaService {

    private static final Logger LOG = Logger.getInstance(DataformTableSchemaService.class);

    private final Project project;
    private final ConcurrentHashMap<String, List<ColumnInfo>> schemaCache = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean pendingRefresh = false;
    private volatile CompiledGraph pendingGraph = null;

    public DataformTableSchemaService(@NotNull Project project) {
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

    @NotNull
    public Optional<List<ColumnInfo>> getSchema(@NotNull String fqn) {
        return Optional.ofNullable(schemaCache.get(fqn));
    }

    public boolean isDataformTable(@NotNull String fqn) {
        return schemaCache.containsKey(fqn);
    }

    @NotNull
    public Map<String, List<ColumnInfo>> getAllSchemas() {
        return Collections.unmodifiableMap(schemaCache);
    }

    public void clearCache() {
        schemaCache.clear();
    }


    private void runExtraction(@NotNull CompiledGraph graph,
                               @NotNull ProgressIndicator indicator) throws Exception {
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

        BigQueryDryRunSchemaExtractor extractor =
                project.getService(BigQueryDryRunSchemaExtractor.class);

        List<CompiledTable> sortedTables = DataformTopologicalSorter.sort(graph);
        int total = sortedTables.size();

        Map<String, List<ColumnInfo>> resolvedInThisRun = new HashMap<>();

        for (int i = 0; i < total; i++) {
            if (indicator.isCanceled()) {
                LOG.debug("Schema extraction cancelled after " + i + "/" + total + " tables");
                return;
            }

            CompiledTable table = sortedTables.get(i);
            String fqn = table.getTarget().getFullName();

            indicator.setText("Dataform schema: " + fqn);
            indicator.setFraction((double) i / total);

            processTable(table, fqn, projectId, location, resolvedInThisRun, extractor);
        }

        indicator.setFraction(1.0);
        LOG.info("Schema extraction complete: "
                + resolvedInThisRun.size() + "/" + total + " tables resolved");
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
                System.out.println("Resolved schema for " + fqn + ": " + columns.size() + " columns");
            } else {
                System.out.println("No schema returned for " + fqn);
            }

        } catch (Exception e) {
            LOG.warn("Schema extraction failed for " + fqn + ": " + e.getMessage());
        }
    }

}
