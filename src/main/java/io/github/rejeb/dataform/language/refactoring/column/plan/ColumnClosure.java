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
package io.github.rejeb.dataform.language.refactoring.column.plan;

import io.github.rejeb.dataform.language.lineage.column.ColumnEdge;
import io.github.rejeb.dataform.language.lineage.column.ColumnLineageGraph;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.lineage.column.Confidence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The columns a rename reaches: those that carry the same name as the one being renamed.
 *
 * <p>A Dataform column flows through the layers of a project under the same name for as long as no
 * query gives it another one. Renaming it has to follow that flow in both directions — the actions
 * reading it and the actions producing it — and has to stop where the name stops being shared.</p>
 *
 * <p>Only {@link Confidence#DIRECT} edges carry the name: a {@code RENAME} edge names the column
 * differently on the other side, a {@code DERIVED} edge feeds an expression, and a {@code STAR} or
 * {@code AMBIGUOUS} edge means the analyzer could not tell what the other side is. The last two are
 * reported rather than followed, since a rename that walks past an unknown is a rename that breaks
 * a file it never looked at.</p>
 *
 * <p>A column of a declared source is never reached: the source is a table of BigQuery the project
 * does not build, so there is nothing to rename there. A column read straight from one is where the
 * rename starts publishing the new name, and it is marked so its declaration is written as
 * {@code old AS new} rather than renamed.</p>
 *
 * <p>Pure: it reads the graph and nothing else.</p>
 */
public final class ColumnClosure {

    private final Set<ColumnRef> columns;
    private final Set<ColumnRef> carried;
    private final Set<ColumnRef> aliased;
    private final List<ColumnRef> unknownOrigins;

    private ColumnClosure(@NotNull Set<ColumnRef> columns,
                          @NotNull Set<ColumnRef> carried,
                          @NotNull Set<ColumnRef> aliased,
                          @NotNull List<ColumnRef> unknownOrigins) {
        this.columns = columns;
        this.carried = carried;
        this.aliased = aliased;
        this.unknownOrigins = unknownOrigins;
    }

    /** The columns the rename reaches, the starting one included. */
    public @NotNull Set<ColumnRef> columns() {
        return columns;
    }

    /**
     * The columns another column of the closure feeds directly, and which therefore take the new
     * name from what they read rather than having to declare it themselves.
     *
     * <p>This is what tells a star that has to be expanded from one that has nothing to do. A star
     * publishes whatever its source calls the column: once the source is renamed, a carried column
     * is published under the new name without a character being written for it. Only a star whose
     * own input the rename does not reach — the first action of the chain, whose source is a literal
     * or a table outside the project — has to be turned into an explicit list.</p>
     */
    public @NotNull Set<ColumnRef> carried() {
        return carried;
    }

    /**
     * The columns of the closure read straight from a declared source, which keep reading the old
     * name and have to publish the new one themselves: their declaration is written
     * {@code old AS new}.
     */
    public @NotNull Set<ColumnRef> aliased() {
        return aliased;
    }

    /**
     * The columns of the closure whose own inputs could not be determined, because the query
     * producing them selects a star the analyzer could not expand.
     */
    public @NotNull List<ColumnRef> unknownOrigins() {
        return unknownOrigins;
    }

    /**
     * The closure of {@code start} in {@code graph}.
     *
     * @param sourceTables the full names of the tables declared to the project rather than built
     *                     by it, whose columns the rename never reaches
     */
    public static @NotNull ColumnClosure of(@NotNull ColumnLineageGraph graph,
                                            @NotNull ColumnRef start,
                                            @NotNull Set<String> sourceTables) {
        return walk(graph, start, sourceTables, true);
    }

    /**
     * The closure of {@code start} walked downstream only: the columns built from it, and not the
     * ones it is built from. This is the scope of a rename that stops at the file of the caret and
     * leaves everything upstream under its old name.
     */
    public static @NotNull ColumnClosure downstreamOf(@NotNull ColumnLineageGraph graph,
                                                      @NotNull ColumnRef start,
                                                      @NotNull Set<String> sourceTables) {
        return walk(graph, start, sourceTables, false);
    }

    private static @NotNull ColumnClosure walk(@NotNull ColumnLineageGraph graph,
                                               @NotNull ColumnRef start,
                                               @NotNull Set<String> sourceTables,
                                               boolean bothWays) {
        Map<ColumnRef, List<ColumnEdge>> byFrom = new HashMap<>();
        Map<ColumnRef, List<ColumnEdge>> byTo = new HashMap<>();
        for (ColumnEdge edge : graph.edges()) {
            byFrom.computeIfAbsent(edge.from(), column -> new ArrayList<>()).add(edge);
            byTo.computeIfAbsent(edge.to(), column -> new ArrayList<>()).add(edge);
        }

        Set<ColumnRef> reached = new LinkedHashSet<>();
        Set<ColumnRef> unknown = new LinkedHashSet<>();
        Set<ColumnRef> carried = new LinkedHashSet<>();
        Set<ColumnRef> aliased = new LinkedHashSet<>();
        Deque<ColumnRef> queue = new ArrayDeque<>();
        boolean startsOnASource = sourceTables.contains(start.tableFullName());
        if (!startsOnASource) reached.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            ColumnRef current = queue.poll();
            boolean source = sourceTables.contains(current.tableFullName());
            for (ColumnEdge edge : byTo.getOrDefault(current, List.of())) {
                if (source) break;
                if (edge.kind() == Confidence.STAR) unknown.add(current);
                if (edge.kind() != Confidence.DIRECT || !bothWays) continue;
                if (sourceTables.contains(edge.from().tableFullName())) {
                    aliased.add(current);
                } else if (reached.add(edge.from())) {
                    queue.add(edge.from());
                }
            }
            for (ColumnEdge edge : byFrom.getOrDefault(current, List.of())) {
                if (edge.kind() != Confidence.DIRECT) continue;
                if (sourceTables.contains(edge.to().tableFullName())) continue;
                if (source) aliased.add(edge.to());
                if (reached.add(edge.to())) queue.add(edge.to());
            }
        }
        for (ColumnEdge edge : graph.edges()) {
            if (edge.kind() == Confidence.DIRECT
                    && reached.contains(edge.from()) && reached.contains(edge.to())) {
                carried.add(edge.to());
            }
        }
        return new ColumnClosure(reached, carried, aliased, List.copyOf(unknown));
    }
}
