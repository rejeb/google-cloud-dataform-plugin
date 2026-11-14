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
package io.github.rejeb.dataform.language.lineage.dataplex;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares the lineage the repository declares with the one Knowledge Catalog recorded, on the
 * loaded graph only: between the tables of the repository that are on it and their direct
 * neighbours. Operations, assertions and declarations are never the table a difference is about,
 * and a table is never said to miss a recorded source unless its sources were read.
 */
public final class LineageDifferences {

    private static final Set<String> NEVER_WRITTEN = Set.of("operations", "operation", "assertion", "declaration", "test");
    private static final Set<String> NEVER_READ = Set.of("operations", "operation", "assertion", "test");

    private LineageDifferences() {
    }

    /**
     * @param view            the recorded lineage
     * @param dataform        the lineage the repository declares
     * @param writtenDatasets the datasets the repository writes to, as {@code project.dataset}
     * @return the recorded links the repository does not declare, then the declared dependencies
     * without a recorded link
     */
    public static @NotNull List<Difference> compute(@NotNull DataplexView view, @NotNull LineageGraph dataform,
                                                    @NotNull Set<String> writtenDatasets) {
        LineageGraph recorded = view.graph();
        List<Difference> differences = new ArrayList<>();
        for (LineageNode target : recorded.nodes()) {
            for (String sourceId : recorded.predecessors(target.id())) {
                if (written(dataform, target.id()) != null) {
                    if (!dataform.predecessors(target.id()).contains(sourceId)) {
                        differences.add(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, sourceId, target.id()));
                    }
                } else if (written(dataform, sourceId) != null && dataform.node(target.id()) == null
                        && writtenDatasets.contains(datasetOf(view, target.id()))) {
                    differences.add(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, sourceId, target.id()));
                }
            }
        }
        Set<String> known = new HashSet<>();
        for (RecordedLink link : view.snapshot().links()) known.add(keyOf(link));
        for (RecordedLink link : view.snapshot().pendingLinks()) known.add(keyOf(link));
        for (LineageNode target : recorded.nodes()) {
            String fqn = view.fqnOf(target.id());
            if (written(dataform, target.id()) == null || fqn == null || !view.snapshot().upstreamRead().contains(fqn)) continue;
            for (String sourceId : dataform.predecessors(target.id())) {
                LineageNode source = dataform.node(sourceId);
                if (source == null || NEVER_READ.contains(source.dataformType())) continue;
                if (!known.contains(sourceId + '>' + target.id())) {
                    differences.add(new Difference(Difference.Kind.DECLARED_NOT_RECORDED, sourceId, target.id()));
                }
            }
        }
        return differences;
    }

    /**
     * @return the datasets the tables and views of the repository are written to, as {@code project.dataset}
     */
    public static @NotNull Set<String> writtenDatasets(@NotNull LineageGraph dataform) {
        Set<String> datasets = new LinkedHashSet<>();
        for (LineageNode node : dataform.nodes()) {
            if (NEVER_WRITTEN.contains(node.dataformType())) continue;
            int dot = node.fullName().lastIndexOf('.');
            if (dot > 0) datasets.add(node.fullName().substring(0, dot));
        }
        return datasets;
    }

    /**
     * @return the recorded graph with the dependencies only the repository declares and the tables
     * they come from; the recorded graph itself when there is none
     */
    public static @NotNull LineageGraph withDeclaredOnly(@NotNull LineageGraph recorded, @NotNull LineageGraph dataform,
                                                         @NotNull List<Difference> differences) {
        List<Difference> declaredOnly = differences.stream()
                .filter(difference -> difference.kind() == Difference.Kind.DECLARED_NOT_RECORDED).toList();
        if (declaredOnly.isEmpty()) return recorded;
        LineageGraph.Builder builder = LineageGraph.builder();
        Set<String> present = new HashSet<>();
        for (LineageNode node : recorded.nodes()) {
            builder.addNode(node);
            present.add(node.id());
        }
        for (Difference difference : declaredOnly) {
            for (String id : List.of(difference.fromId(), difference.toId())) {
                LineageNode node = dataform.node(id);
                if (node != null && present.add(id)) builder.addNode(node);
            }
        }
        for (LineageNode node : recorded.nodes()) {
            for (String sourceId : recorded.predecessors(node.id())) builder.addEdge(sourceId, node.id());
        }
        for (Difference difference : declaredOnly) {
            if (present.contains(difference.fromId()) && present.contains(difference.toId())) {
                builder.addEdge(difference.fromId(), difference.toId());
            }
        }
        return builder.build();
    }

    private static @Nullable LineageNode written(@NotNull LineageGraph dataform, @NotNull String nodeId) {
        LineageNode node = dataform.node(nodeId);
        return node == null || NEVER_WRITTEN.contains(node.dataformType()) ? null : node;
    }

    private static @NotNull String datasetOf(@NotNull DataplexView view, @NotNull String nodeId) {
        String fqn = view.fqnOf(nodeId);
        String fullName = fqn == null ? null : EntityNames.fullNameOf(fqn);
        if (fullName == null) return "";
        int dot = fullName.lastIndexOf('.');
        return dot > 0 ? fullName.substring(0, dot) : "";
    }

    private static @NotNull String keyOf(@NotNull RecordedLink link) {
        return EntityNames.nodeIdOf(link.sourceFqn()) + '>' + EntityNames.nodeIdOf(link.targetFqn());
    }
}
