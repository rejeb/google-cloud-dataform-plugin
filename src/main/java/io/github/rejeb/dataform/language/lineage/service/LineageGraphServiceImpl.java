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

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.Target;
import io.github.rejeb.dataform.language.lineage.column.BigQuerySelectAnalyzer;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageExtractor;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.extractor.LineageExtractorImpl;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link LineageGraphService}. The graphs are rebuilt when the compiled graph or the
 * schemas change and cached otherwise; concurrent callers share a single build. The build parses
 * the SQL of every action, so it must be asked from a background thread.
 */
public final class LineageGraphServiceImpl implements LineageGraphService {

    private record Cached(@Nullable CompiledGraph compiled, long schemaStamp, @NotNull Graphs graphs) {
        boolean matches(@NotNull CompiledGraph compiled, long schemaStamp) {
            return this.compiled == compiled && this.schemaStamp == schemaStamp
                    && graphs.columnGraph() != null;
        }
    }

    private final Project project;
    private final Object buildLock = new Object();
    private volatile Cached cached = new Cached(null, Long.MIN_VALUE, new Graphs(null, null));

    public LineageGraphServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull Graphs graphs(@NotNull CompiledGraph compiled) {
        long schemaStamp = DataformTableSchemaService.getInstance(project).getModificationCount();
        Cached current = cached;
        if (current.matches(compiled, schemaStamp)) {
            return current.graphs();
        }
        synchronized (buildLock) {
            current = cached;
            if (current.matches(compiled, schemaStamp)) {
                return current.graphs();
            }
            LineageGraph tableGraph = new LineageExtractorImpl().extract(compiled);
            ColumnLineageGraph columnGraph = computeColumnGraph(compiled);
            Graphs graphs = new Graphs(tableGraph, columnGraph);
            cached = new Cached(compiled, schemaStamp, graphs);
            return graphs;
        }
    }

    @Override
    public @Nullable ColumnLineageGraph columnGraph() {
        CompiledGraph compiled = DataformCompilationService.getInstance(project).getCompiledGraph();
        return compiled == null ? null : graphs(compiled).columnGraph();
    }

    /**
     * Builds the column graph from the schemas of the actions present in the compiled graph.
     * Cached schemas of actions that are no longer part of the graph are ignored, so a stale
     * entry cannot contribute columns to the lineage.
     */
    private @NotNull ColumnLineageGraph computeColumnGraph(@NotNull CompiledGraph compiled) {
        Set<String> actionNames = actionFullNames(compiled);
        Map<String, List<ColumnInfo>> schemas = new LinkedHashMap<>();
        DataformTableSchemaService.getInstance(project).getAllTables()
                .forEach((fqn, table) -> {
                    if (actionNames.contains(fqn)) schemas.put(fqn, table.getColumns());
                });
        ColumnLineageExtractor extractor =
                new ColumnLineageExtractorImpl(new BigQuerySelectAnalyzer(project));
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
