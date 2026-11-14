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

import io.github.rejeb.dataform.language.columns.analysis.ColumnEdge;
import io.github.rejeb.dataform.language.columns.analysis.ColumnLineageGraph;
import io.github.rejeb.dataform.language.columns.analysis.Confidence;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageClient.Direction;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The column lineage Knowledge Catalog recorded: the column links around the roots, read in one
 * search each way and walked level by level within the tables of the loaded graph, and how it is
 * laid over the known columns of the cards.
 */
public final class DataplexColumnLineage {

    /**
     * The permission needed to read column links.
     */
    public static final String FIELDS_PERMISSION = "datalineage.events.getFields";

    static final int MAX_COLUMNS = 500;
    static final int MAX_DEPTH = 100;

    private DataplexColumnLineage() {
    }

    /**
     * @param graph  the recorded columns and links, empty when they could not be read
     * @param reason why the column lineage could not be read, {@code null} when it was
     */
    public record Result(@NotNull ColumnLineageGraph graph, @Nullable String reason) {
    }

    /**
     * Reads the recorded column links around the roots, both ways in one search, then walks them
     * upstream and downstream from the roots. A link to a table that is not loaded is left out and
     * not followed.
     *
     * @param roots           the columns whose lineage is asked for
     * @param loadedFullNames the full names of the loaded BigQuery tables
     * @return the walked lineage, or an empty graph and the reason when Knowledge Catalog refused
     */
    public static @NotNull Result load(@NotNull DataplexLineageClient client, @NotNull LineageScope scope,
                                       @NotNull List<ColumnRef> roots, @NotNull Set<String> loadedFullNames) {
        List<ColumnRef> kept = roots.stream().filter(root -> loadedFullNames.contains(root.tableFullName())).toList();
        if (kept.isEmpty()) return new Result(ColumnLineageGraph.builder().build(), null);
        Map<String, List<String>> rootColumns = new LinkedHashMap<>();
        Set<String> asked = new HashSet<>();
        for (ColumnRef root : kept) {
            if (!asked.add(key(root))) continue;
            rootColumns.computeIfAbsent(EntityNames.fqnOf(root.tableFullName()), t -> new ArrayList<>()).add(root.columnName());
        }
        List<RecordedColumnLink> links;
        try {
            links = client.columnStream(scope, rootColumns, EnumSet.allOf(Direction.class), MAX_DEPTH);
        } catch (DataplexLineageException e) {
            return new Result(ColumnLineageGraph.builder().build(), reasonOf(e, scope));
        }
        Walk walk = new Walk(loadedFullNames, kept, links);
        walk.run(Direction.UPSTREAM);
        walk.run(Direction.DOWNSTREAM);
        return new Result(walk.graph(), null);
    }

    /**
     * Lays recorded column links over the columns the cards know.
     *
     * @param known    the columns of the repository tables, whose own edges are left out
     * @param outside  the columns read for the tables outside the repository, by full name
     * @param recorded the recorded columns and links, {@code null} when none is shown
     * @return the known columns, those of the outside tables, the recorded columns no card knows,
     * and the recorded links only
     */
    public static @NotNull ColumnLineageGraph over(@Nullable ColumnLineageGraph known,
                                                   @NotNull Map<String, List<ColumnRef>> outside,
                                                   @Nullable ColumnLineageGraph recorded) {
        return over(known, outside, recorded, List.of());
    }

    /**
     * Same as {@link #over(ColumnLineageGraph, Map, ColumnLineageGraph)}, keeping also the columns
     * met by earlier walks, so a card does not lose a row when the shown lineage changes.
     */
    public static @NotNull ColumnLineageGraph over(@Nullable ColumnLineageGraph known,
                                                   @NotNull Map<String, List<ColumnRef>> outside,
                                                   @Nullable ColumnLineageGraph recorded,
                                                   @NotNull Collection<ColumnRef> discovered) {
        ColumnLineageGraph.Builder builder = ColumnLineageGraph.builder();
        Set<String> present = new HashSet<>();
        Set<String> knownTables = new HashSet<>();
        if (known != null) {
            for (ColumnRef column : known.columns()) {
                add(builder, present, column);
                knownTables.add(column.tableNodeId());
            }
            known.unresolvedTables().forEach(builder::addUnresolvedTable);
        }
        for (Map.Entry<String, List<ColumnRef>> table : outside.entrySet()) {
            if (knownTables.contains(LineageNode.idOf(table.getKey()))) continue;
            table.getValue().forEach(column -> add(builder, present, column));
        }
        discovered.forEach(column -> add(builder, present, column));
        if (recorded != null) {
            recorded.columns().forEach(column -> add(builder, present, column));
            for (ColumnEdge edge : recorded.edges()) builder.addEdge(edge.from().id(), edge.to().id(), edge.kind());
        }
        return builder.build();
    }

    private static void add(@NotNull ColumnLineageGraph.Builder builder, @NotNull Set<String> present,
                            @NotNull ColumnRef column) {
        if (present.add(key(column))) builder.addColumn(column);
    }

    private static @NotNull String key(@NotNull ColumnRef column) {
        return column.id().toLowerCase(Locale.ROOT);
    }

    private static @NotNull String reasonOf(@NotNull DataplexLineageException e, @NotNull LineageScope scope) {
        return switch (e.kind()) {
            case PERMISSION_DENIED -> "Your account cannot read column lineage in " + scope.projectId()
                    + ": it needs the permission " + FIELDS_PERMISSION;
            case NOT_SIGNED_IN -> "Sign in to Google Cloud to read the column lineage";
            case API_DISABLED -> "The Data Lineage API is not enabled in " + scope.projectId();
            case OTHER -> "Knowledge Catalog cannot give the column lineage: "
                    + (e.getMessage() == null || e.getMessage().isBlank() ? "the request failed" : e.getMessage());
        };
    }

    private static final class Walk {
        private final Set<String> loaded;
        private final List<ColumnRef> roots;
        private final Map<String, List<RecordedColumnLink>> byTarget = new HashMap<>();
        private final Map<String, List<RecordedColumnLink>> bySource = new HashMap<>();
        private final Map<String, ColumnRef> columns = new LinkedHashMap<>();
        private final Map<String, ColumnEdge> edges = new LinkedHashMap<>();

        Walk(@NotNull Set<String> loaded, @NotNull List<ColumnRef> roots, @NotNull List<RecordedColumnLink> links) {
            this.loaded = loaded;
            this.roots = roots;
            for (RecordedColumnLink link : links) {
                byTarget.computeIfAbsent(end(link.targetFqn(), link.targetColumn()), k -> new ArrayList<>()).add(link);
                bySource.computeIfAbsent(end(link.sourceFqn(), link.sourceColumn()), k -> new ArrayList<>()).add(link);
            }
            roots.forEach(root -> columns.putIfAbsent(key(root), root));
        }

        void run(@NotNull Direction direction) {
            Map<String, List<RecordedColumnLink>> near = direction == Direction.UPSTREAM ? byTarget : bySource;
            Set<String> seen = new HashSet<>();
            Map<String, List<String>> frontier = new LinkedHashMap<>();
            for (ColumnRef root : roots) {
                if (seen.add(key(root))) frontier.computeIfAbsent(root.tableFullName(), t -> new ArrayList<>()).add(root.columnName());
            }
            while (!frontier.isEmpty()) {
                Map<String, List<String>> next = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> table : frontier.entrySet()) {
                    String fqn = EntityNames.fqnOf(table.getKey());
                    for (String column : table.getValue()) {
                        for (RecordedColumnLink link : near.getOrDefault(end(fqn, column), List.of())) {
                            ColumnRef far = follow(link, direction);
                            if (far != null && seen.size() < MAX_COLUMNS && seen.add(key(far))) {
                                next.computeIfAbsent(far.tableFullName(), t -> new ArrayList<>()).add(far.columnName());
                            }
                        }
                    }
                }
                frontier = next;
            }
        }

        private static @NotNull String end(@NotNull String fqn, @NotNull String column) {
            return (fqn + '#' + column).toLowerCase(Locale.ROOT);
        }

        private @Nullable ColumnRef follow(@NotNull RecordedColumnLink link, @NotNull Direction direction) {
            String sourceTable = EntityNames.fullNameOf(link.sourceFqn());
            String targetTable = EntityNames.fullNameOf(link.targetFqn());
            if (sourceTable == null || targetTable == null || !loaded.contains(sourceTable) || !loaded.contains(targetTable)) {
                return null;
            }
            ColumnRef from = column(new ColumnRef(sourceTable, link.sourceColumn()));
            ColumnRef to = column(new ColumnRef(targetTable, link.targetColumn()));
            edges.putIfAbsent(key(from) + '>' + key(to),
                    new ColumnEdge(from, to, link.exactCopy() ? Confidence.DIRECT : Confidence.DERIVED));
            return direction == Direction.UPSTREAM ? from : to;
        }

        private @NotNull ColumnRef column(@NotNull ColumnRef column) {
            return columns.computeIfAbsent(key(column), k -> column);
        }

        @NotNull ColumnLineageGraph graph() {
            ColumnLineageGraph.Builder builder = ColumnLineageGraph.builder();
            columns.values().forEach(builder::addColumn);
            for (ColumnEdge edge : edges.values()) builder.addEdge(edge.from().id(), edge.to().id(), edge.kind());
            return builder.build();
        }
    }
}
