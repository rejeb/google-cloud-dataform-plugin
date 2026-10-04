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
package io.github.rejeb.dataform.language.columns.usages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import io.github.rejeb.dataform.language.columns.analysis.ColumnEdge;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.analysis.Confidence;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.columns.origin.ColumnOriginService;
import io.github.rejeb.dataform.language.lineage.service.LineageGraphService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reads of a column by the actions selecting a star from its table.
 *
 * <p>{@code SELECT * FROM ${ref("orders")}} reads every column of the table without naming one, so
 * no reference points at the column and the reference search never finds that action. The column
 * lineage knows better: the star carries the column on, under the same name, into the table the
 * action builds. The star is listed as the read, and the column it carries on is searched in turn,
 * since reading it is reading the same values under the same name.</p>
 */
final class StarColumnUsageSearch implements ColumnUsageSearch {

    private final Project project;
    private final ColumnWindowTarget target;

    StarColumnUsageSearch(@NotNull Project project, @NotNull ColumnWindowTarget target) {
        this.project = project;
        this.target = target;
    }

    @Override
    public void forEachRead(@NotNull Processor<PsiElement> reads, int maxReads) {
        ColumnLineageGraph graph = LineageGraphService.getInstance(project).columnGraph();
        if (graph == null) return;
        ColumnOriginService origins = ColumnOriginService.getInstance(project);
        Map<ColumnRef, Set<ColumnRef>> carriedByStar = carriedByStar(graph);
        if (carriedByStar.isEmpty()) return;

        Set<ColumnRef> visited = new LinkedHashSet<>();
        Deque<ColumnRef> queue = new ArrayDeque<>();
        for (ColumnRef column : searchedColumns(origins, graph)) {
            if (visited.add(column)) queue.add(column);
        }
        while (!queue.isEmpty()) {
            for (ColumnRef carried : carriedByStar.getOrDefault(queue.poll(), Set.of())) {
                if (!visited.add(carried)) continue;
                queue.add(carried);
                PsiElement star = origins.declaringElement(carried);
                if (star != null && !reads.process(star)) return;
                DataformDasColumn column = origins.dasColumn(carried);
                if (column != null && !ColumnOccurrences.forEachReference(project, column, reads)) return;
            }
        }
    }

    /** The schema columns the window searches, as the lineage graph names them. */
    private @NotNull List<ColumnRef> searchedColumns(@NotNull ColumnOriginService origins,
                                                     @NotNull ColumnLineageGraph graph) {
        return target.searchTargets().stream()
                .filter(DataformDasColumn.class::isInstance)
                .map(searched -> origins.reference((DataformDasColumn) searched))
                .filter(reference -> reference != null && graph.column(reference.id()) != null)
                .map(reference -> graph.column(reference.id()))
                .toList();
    }

    /** Each column of the graph with the columns a star carries it on to. */
    private static @NotNull Map<ColumnRef, Set<ColumnRef>> carriedByStar(@NotNull ColumnLineageGraph graph) {
        Map<ColumnRef, Set<ColumnRef>> carried = new LinkedHashMap<>();
        for (ColumnEdge edge : graph.edges()) {
            if (edge.kind() != Confidence.STAR) continue;
            carried.computeIfAbsent(edge.from(), k -> new LinkedHashSet<>()).add(edge.to());
        }
        return carried;
    }
}
