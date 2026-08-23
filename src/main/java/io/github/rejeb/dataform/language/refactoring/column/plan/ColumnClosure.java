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
import java.util.LinkedHashSet;
import java.util.List;
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
 * <p>Pure: it reads the graph and nothing else.</p>
 */
public final class ColumnClosure {

    private final Set<ColumnRef> columns;
    private final List<ColumnRef> unknownOrigins;

    private ColumnClosure(@NotNull Set<ColumnRef> columns, @NotNull List<ColumnRef> unknownOrigins) {
        this.columns = columns;
        this.unknownOrigins = unknownOrigins;
    }

    /** The columns the rename reaches, the starting one included. */
    public @NotNull Set<ColumnRef> columns() {
        return columns;
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
     */
    public static @NotNull ColumnClosure of(@NotNull ColumnLineageGraph graph,
                                            @NotNull ColumnRef start) {
        return walk(graph, start, true);
    }

    /**
     * The closure of {@code start} walked downstream only: the columns built from it, and not the
     * ones it is built from. This is the scope of a rename that stops at the file of the caret and
     * leaves everything upstream under its old name.
     */
    public static @NotNull ColumnClosure downstreamOf(@NotNull ColumnLineageGraph graph,
                                                      @NotNull ColumnRef start) {
        return walk(graph, start, false);
    }

    private static @NotNull ColumnClosure walk(@NotNull ColumnLineageGraph graph,
                                               @NotNull ColumnRef start,
                                               boolean bothWays) {
        Set<ColumnRef> reached = new LinkedHashSet<>();
        List<ColumnRef> unknown = new ArrayList<>();
        Deque<ColumnRef> queue = new ArrayDeque<>();
        reached.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            ColumnRef current = queue.poll();
            for (ColumnEdge edge : graph.edges()) {
                if (edge.kind() == Confidence.STAR && edge.to().equals(current)
                        && !unknown.contains(current)) {
                    unknown.add(current);
                }
                if (edge.kind() != Confidence.DIRECT) continue;
                if (edge.from().equals(current) && reached.add(edge.to())) {
                    queue.add(edge.to());
                }
                if (bothWays && edge.to().equals(current) && reached.add(edge.from())) {
                    queue.add(edge.from());
                }
            }
        }
        return new ColumnClosure(reached, unknown);
    }
}
