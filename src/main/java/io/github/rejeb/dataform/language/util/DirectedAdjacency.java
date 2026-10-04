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
package io.github.rejeb.dataform.language.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable edges of a directed graph whose nodes are identified by strings. An edge
 * {@code A -> B} means "A feeds B": {@code A} is a predecessor of {@code B}, {@code B} a successor
 * of {@code A}. Transitive closures are cycle-guarded and memoized.
 */
public final class DirectedAdjacency {

    private final Map<String, Set<String>> predecessors;
    private final Map<String, Set<String>> successors;
    private final Map<String, Set<String>> upstreamCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> downstreamCache = new ConcurrentHashMap<>();

    private DirectedAdjacency(@NotNull Map<String, Set<String>> predecessors,
                              @NotNull Map<String, Set<String>> successors) {
        this.predecessors = predecessors;
        this.successors = successors;
    }

    /**
     * Returns the direct predecessors of a node: the nodes feeding it.
     */
    public @NotNull Set<String> predecessors(@NotNull String id) {
        return predecessors.getOrDefault(id, Set.of());
    }

    /**
     * Returns the direct successors of a node: the nodes it feeds.
     */
    public @NotNull Set<String> successors(@NotNull String id) {
        return successors.getOrDefault(id, Set.of());
    }

    /**
     * Returns every node feeding a node, directly or not.
     */
    public @NotNull Set<String> upstream(@NotNull String id) {
        return upstreamCache.computeIfAbsent(id, k -> traverse(k, true));
    }

    /**
     * Returns every node a node feeds, directly or not.
     */
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

    /**
     * Returns a builder of an empty adjacency.
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Set<String>> predecessors = new LinkedHashMap<>();
        private final Map<String, Set<String>> successors = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds the edge {@code fromId -> toId}; adding it again changes nothing.
         */
        public void addEdge(@NotNull String fromId, @NotNull String toId) {
            successors.computeIfAbsent(fromId, k -> new LinkedHashSet<>()).add(toId);
            predecessors.computeIfAbsent(toId, k -> new LinkedHashSet<>()).add(fromId);
        }

        /**
         * Returns an immutable copy of the edges added so far.
         */
        public @NotNull DirectedAdjacency build() {
            return new DirectedAdjacency(frozen(predecessors), frozen(successors));
        }

        private static @NotNull Map<String, Set<String>> frozen(@NotNull Map<String, Set<String>> edges) {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            edges.forEach((k, v) -> copy.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
            return Collections.unmodifiableMap(copy);
        }
    }
}
