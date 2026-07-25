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
package io.github.rejeb.dataform.language.lineage.column;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable directed graph of {@link ColumnRef}s. An edge {@code A -> B} means
 * "A feeds B" (dependency column to dependent column), consistent with the
 * table-level {@code LineageGraph}.
 */
public final class ColumnLineageGraph {

    private final Map<String, ColumnRef> columns;
    private final Map<String, Set<String>> predecessors;
    private final Map<String, Set<String>> successors;
    private final List<ColumnEdge> edges;
    private final Map<String, Set<String>> upstreamCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Set<String>> downstreamCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, List<ColumnRef>> columnsByTable;
    private final Set<String> unresolvedTables;
    private final Map<String, String> canonicalIds;

    private ColumnLineageGraph(Map<String, ColumnRef> columns,
                               Map<String, Set<String>> predecessors,
                               Map<String, Set<String>> successors,
                               List<ColumnEdge> edges,
                               Set<String> unresolvedTables,
                               Map<String, String> canonicalIds) {
        this.canonicalIds = canonicalIds;
        this.columns = columns;
        this.predecessors = predecessors;
        this.successors = successors;
        this.edges = edges;
        this.unresolvedTables = unresolvedTables;
        Map<String, List<ColumnRef>> byTable = new LinkedHashMap<>();
        for (ColumnRef column : columns.values()) {
            byTable.computeIfAbsent(column.tableNodeId(), k -> new java.util.ArrayList<>()).add(column);
        }
        this.columnsByTable = byTable;
    }

    public @NotNull Collection<ColumnRef> columns() {
        return Collections.unmodifiableCollection(columns.values());
    }

    /**
     * Fully qualified names of the actions whose schema could not be resolved, and whose column
     * lineage was therefore skipped. Callers should warn the user that the column graph is
     * incomplete: columns and edges involving these tables are missing.
     */
    public @NotNull Set<String> unresolvedTables() {
        return unresolvedTables;
    }

    /** Columns belonging to a table node, indexed for O(1) lookup. */
    public @NotNull List<ColumnRef> columnsForTable(@NotNull String tableNodeId) {
        return columnsByTable.getOrDefault(tableNodeId, List.of());
    }

    public @Nullable ColumnRef column(@NotNull String id) {
        ColumnRef exact = columns.get(id);
        return exact != null ? exact : columns.get(canonicalIds.get(Builder.key(id)));
    }

    public @NotNull List<ColumnEdge> edges() {
        return edges;
    }

    /** Direct dependencies of {@code id} (columns that feed into it). */
    public @NotNull Set<String> predecessors(@NotNull String id) {
        return predecessors.getOrDefault(id, Set.of());
    }

    /** Columns that depend directly on {@code id}. */
    public @NotNull Set<String> successors(@NotNull String id) {
        return successors.getOrDefault(id, Set.of());
    }

    /** Transitive upstream closure of {@code id} (cycle-guarded, memoized). */
    public @NotNull Set<String> upstream(@NotNull String id) {
        return upstreamCache.computeIfAbsent(id, k -> traverse(k, true));
    }

    /** Transitive downstream closure of {@code id} (cycle-guarded, memoized). */
    public @NotNull Set<String> downstream(@NotNull String id) {
        return downstreamCache.computeIfAbsent(id, k -> traverse(k, false));
    }

    private @NotNull Set<String> traverse(@NotNull String id, boolean upstream) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(id);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> next = upstream ? predecessors(current) : successors(current);
            for (String n : next) {
                if (result.add(n)) queue.add(n);
            }
        }
        return result;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, ColumnRef> columns = new LinkedHashMap<>();
        private final Map<String, Set<String>> predecessors = new LinkedHashMap<>();
        private final Map<String, Set<String>> successors = new LinkedHashMap<>();
        private final List<ColumnEdge> edges = new ArrayList<>();
        private final Set<String> unresolvedTables = new LinkedHashSet<>();
        private final Map<String, String> canonicalIds = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Records an action whose schema is unknown, so its column lineage was skipped.
         */
        public @NotNull Builder addUnresolvedTable(@NotNull String tableFullName) {
            unresolvedTables.add(tableFullName);
            return this;
        }

        public @NotNull Builder addColumn(@NotNull ColumnRef column) {
            if (columns.putIfAbsent(column.id(), column) == null) {
                canonicalIds.putIfAbsent(key(column.id()), column.id());
            }
            return this;
        }

        /**
         * Connects two known columns. Identifiers are resolved case-insensitively to the columns
         * declared through {@link #addColumn}; an edge naming an unknown column is dropped, so
         * edge resolution can never introduce a column a table does not have.
         */
        public @NotNull Builder addEdge(@NotNull String fromId, @NotNull String toId, @NotNull Confidence kind) {
            String from = canonicalIds.get(key(fromId));
            String to = canonicalIds.get(key(toId));
            if (from == null || to == null) return this;
            successors.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            predecessors.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            edges.add(new ColumnEdge(columns.get(from), columns.get(to), kind));
            return this;
        }

        private static @NotNull String key(@NotNull String id) {
            return id.toLowerCase(java.util.Locale.ROOT);
        }

        public @NotNull ColumnLineageGraph build() {
            Map<String, Set<String>> frozenPred = new LinkedHashMap<>();
            predecessors.forEach((k, v) -> frozenPred.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
            Map<String, Set<String>> frozenSucc = new LinkedHashMap<>();
            successors.forEach((k, v) -> frozenSucc.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
            return new ColumnLineageGraph(
                    Collections.unmodifiableMap(new LinkedHashMap<>(columns)),
                    Collections.unmodifiableMap(frozenPred),
                    Collections.unmodifiableMap(frozenSucc),
                    Collections.unmodifiableList(new ArrayList<>(edges)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(unresolvedTables)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(canonicalIds)));
        }
    }
}
