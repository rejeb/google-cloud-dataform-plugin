/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DataformTopologicalSorter {

    private DataformTopologicalSorter() {
    }

    @NotNull
    public static List<SortableAction> sort(@NotNull CompiledGraph graph) {
        List<SortableAction> actions = new ArrayList<>();
        graph.getTables().stream()
                .map(SortableAction::of)
                .forEach(actions::add);
        graph.getOperations().stream()
                .filter(op -> op.isHasOutput() && !op.isDisabled())
                .map(SortableAction::of)
                .forEach(actions::add);

        Map<String, SortableAction> fqnToAction = new HashMap<>();
        for (SortableAction a : actions) {
            fqnToAction.put(a.target().getFullName(), a);
        }

        Map<SortableAction, Integer> inDegree = new LinkedHashMap<>();
        Map<SortableAction, List<SortableAction>> dependents = new HashMap<>();

        for (SortableAction a : actions) {
            inDegree.putIfAbsent(a, 0);
            for (Target dep : a.dependencyTargets()) {
                SortableAction depAction = fqnToAction.get(dep.getFullName());
                if (depAction == null) continue;
                dependents.computeIfAbsent(depAction, k -> new ArrayList<>()).add(a);
                inDegree.merge(a, 1, Integer::sum);
            }
        }

        Queue<SortableAction> ready = new ArrayDeque<>();
        for (Map.Entry<SortableAction, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) ready.add(e.getKey());
        }

        List<SortableAction> sorted = new ArrayList<>(actions.size());
        while (!ready.isEmpty()) {
            SortableAction current = ready.poll();
            sorted.add(current);
            for (SortableAction dependent : dependents.getOrDefault(current, Collections.emptyList())) {
                int remaining = inDegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) ready.add(dependent);
            }
        }

        for (SortableAction a : actions) {
            if (!sorted.contains(a)) sorted.add(a);
        }

        graph.getDeclarations().stream()
                .map(SortableAction::of)
                .forEach(sorted::add);

        return sorted;
    }
}
