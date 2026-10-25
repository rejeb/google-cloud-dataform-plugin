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
package io.github.rejeb.dataform.language.lineage.graph;

import io.github.rejeb.dataform.language.util.DirectedAdjacency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable directed acyclic graph of {@link LineageNode}s.
 *
 * <p>An edge {@code A → B} means "A is a dependency of B" (A feeds B).
 * {@link #predecessors(String)} returns the direct dependencies of a given node;
 * {@link #successors(String)} returns the nodes that depend on it.</p>
 */
public final class LineageGraph {

    private final Map<String, LineageNode> nodes;
    private final DirectedAdjacency adjacency;

    private LineageGraph(Map<String, LineageNode> nodes, DirectedAdjacency adjacency) {
        this.nodes = nodes;
        this.adjacency = adjacency;
    }

    public @NotNull Collection<LineageNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public @Nullable LineageNode node(@NotNull String id) {
        return nodes.get(id);
    }

    /** Direct dependencies of {@code id} (nodes that feed into it). */
    public @NotNull Set<String> predecessors(@NotNull String id) {
        return adjacency.predecessors(id);
    }

    /** Nodes that depend on {@code id}. */
    public @NotNull Set<String> successors(@NotNull String id) {
        return adjacency.successors(id);
    }

    /** Transitive upstream closure of {@code id} (cycle-guarded, memoized). */
    public @NotNull Set<String> ancestors(@NotNull String id) {
        return adjacency.upstream(id);
    }

    /** Transitive downstream closure of {@code id} (cycle-guarded, memoized). */
    public @NotNull Set<String> descendants(@NotNull String id) {
        return adjacency.downstream(id);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, LineageNode> nodes = new LinkedHashMap<>();
        private final DirectedAdjacency.Builder adjacency = DirectedAdjacency.builder();

        private Builder() {
        }

        /** Adds a node; silently ignored if a node with the same id already exists. */
        public @NotNull Builder addNode(@NotNull LineageNode node) {
            nodes.putIfAbsent(node.id(), node);
            return this;
        }

        /**
         * Adds a directed edge {@code fromId → toId} (from feeds to).
         * Silently ignored if either endpoint is not registered.
         */
        public @NotNull Builder addEdge(@NotNull String fromId, @NotNull String toId) {
            if (!nodes.containsKey(fromId) || !nodes.containsKey(toId)) return this;
            adjacency.addEdge(fromId, toId);
            return this;
        }

        public @NotNull LineageGraph build() {
            return new LineageGraph(Collections.unmodifiableMap(new LinkedHashMap<>(nodes)), adjacency.build());
        }
    }
}
