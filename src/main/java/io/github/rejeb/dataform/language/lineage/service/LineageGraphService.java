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
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds and caches the table and column lineage graphs of the project, so that consumers do not
 * pay for a rebuild another one has already done.
 */
public interface LineageGraphService {

    static LineageGraphService getInstance(@NotNull Project project) {
        return project.getService(LineageGraphService.class);
    }

    /** The two lineage graphs of a compiled graph. Either may be {@code null} when unavailable. */
    record Graphs(@Nullable LineageGraph tableGraph, @Nullable ColumnLineageGraph columnGraph) {
    }

    /**
     * Returns the lineage graphs of the given compiled graph, rebuilding them only when the
     * compiled graph or the extracted schemas changed since the last call. Extraction parses SQL
     * and must not run on the EDT.
     */
    @NotNull
    Graphs graphs(@NotNull CompiledGraph compiled);

    /**
     * Returns the column lineage graph of the last compilation, or {@code null} when the project
     * has not been compiled yet. Never triggers a compilation: callers that need a graph out of a
     * user action must tell the user to compile rather than pay the CLI cost silently.
     */
    @Nullable
    ColumnLineageGraph columnGraph();
}
