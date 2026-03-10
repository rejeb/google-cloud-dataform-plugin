package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DataformTopologicalSorter {

    private DataformTopologicalSorter() {}

    @NotNull
    public static List<CompiledTable> sort(@NotNull CompiledGraph graph) {
        List<CompiledTable> tables = graph.getTables();

        Map<String, CompiledTable> fqnToTable = new HashMap<>();
        for (CompiledTable t : tables) {
            fqnToTable.put(t.getTarget().getFullName(), t);
        }

        Map<CompiledTable, Integer> inDegree = new LinkedHashMap<>();
        Map<CompiledTable, List<CompiledTable>> dependents = new HashMap<>();

        for (CompiledTable t : tables) {
            inDegree.putIfAbsent(t, 0);
            for (Target dep : t.getDependencyTargets()) {
                CompiledTable depTable = fqnToTable.get(dep.getFullName());
                if (depTable == null) continue;

                dependents.computeIfAbsent(depTable, k -> new ArrayList<>()).add(t);
                inDegree.merge(t, 1, Integer::sum);
            }
        }

        Queue<CompiledTable> ready = new ArrayDeque<>();
        for (Map.Entry<CompiledTable, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) ready.add(e.getKey());
        }

        List<CompiledTable> sorted = new ArrayList<>(tables.size());
        while (!ready.isEmpty()) {
            CompiledTable current = ready.poll();
            sorted.add(current);

            for (CompiledTable dependent : dependents.getOrDefault(current, Collections.emptyList())) {
                int remaining = inDegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) ready.add(dependent);
            }
        }

        for (CompiledTable t : tables) {
            if (!sorted.contains(t)) sorted.add(t);
        }

        return sorted;
    }
}
