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
package io.github.rejeb.dataform.language.columns.impact;

import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.DryRunBuildProblem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hands each new dry-run failure to the column change that most likely caused it: among the
 * changes of the tables the failing action reads, directly or through other actions, the one whose
 * column the error names, preferring a table read directly, then any change of a table read
 * directly, then the first change upstream.
 */
final class ColumnImpacts {

    private ColumnImpacts() {
    }

    /**
     * @param changes     the column changes, in the order they are to be shown
     * @param newFailures the dry-runs that fail since the changes
     * @param readsOf     the full names of the actions each action reads directly
     * @return the changes some action reads, each with the failures handed to it
     */
    static @NotNull List<ColumnImpact> of(@NotNull List<ColumnChange> changes,
                                          @NotNull List<DryRunBuildProblem> newFailures,
                                          @NotNull Map<String, Set<String>> readsOf) {
        Map<ColumnChange, List<DryRunBuildProblem>> affected = new LinkedHashMap<>();
        Set<String> read = new HashSet<>();
        readsOf.values().forEach(read::addAll);
        for (ColumnChange change : changes) {
            if (read.contains(change.tableFullName())) affected.put(change, new ArrayList<>());
        }
        for (DryRunBuildProblem failure : newFailures) {
            ColumnChange cause = causeOf(failure, List.copyOf(affected.keySet()), readsOf);
            if (cause != null) affected.get(cause).add(failure);
        }
        List<ColumnImpact> impacts = new ArrayList<>();
        affected.forEach((change, failures) -> impacts.add(new ColumnImpact(change, List.copyOf(failures))));
        return impacts;
    }

    private static @Nullable ColumnChange causeOf(@NotNull DryRunBuildProblem failure,
                                                  @NotNull List<ColumnChange> changes,
                                                  @NotNull Map<String, Set<String>> readsOf) {
        Set<String> direct = readsOf.getOrDefault(failure.actionFullName(), Set.of());
        Set<String> upstream = upstreamOf(failure.actionFullName(), readsOf);
        List<ColumnChange> candidates = changes.stream().filter(c -> upstream.contains(c.tableFullName())).toList();
        if (candidates.isEmpty()) return null;
        List<ColumnChange> named = candidates.stream().filter(c -> c.oldName().equalsIgnoreCase(failure.subject())).toList();
        for (List<ColumnChange> group : List.of(named, candidates)) {
            for (ColumnChange change : group) {
                if (direct.contains(change.tableFullName())) return change;
            }
            if (!group.isEmpty()) return group.getFirst();
        }
        return null;
    }

    private static @NotNull Set<String> upstreamOf(@NotNull String action, @NotNull Map<String, Set<String>> readsOf) {
        Set<String> upstream = new HashSet<>();
        Deque<String> toVisit = new ArrayDeque<>(readsOf.getOrDefault(action, Set.of()));
        while (!toVisit.isEmpty()) {
            String table = toVisit.pop();
            if (upstream.add(table)) toVisit.addAll(readsOf.getOrDefault(table, Set.of()));
        }
        return upstream;
    }
}
