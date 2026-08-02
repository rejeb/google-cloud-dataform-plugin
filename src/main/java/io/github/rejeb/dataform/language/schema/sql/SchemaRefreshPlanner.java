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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which actions a refresh has to dry-run again and in which order. An action is re-read
 * when its source changed since the schema was recorded, along with everything depending on it,
 * because a column added upstream changes what {@code SELECT *} downstream resolves to. The result
 * is grouped into waves: every action in a wave only depends on actions of earlier waves, so a wave
 * can be dry-run in parallel.
 */
final class SchemaRefreshPlanner {

    private static final Logger LOG = Logger.getInstance(SchemaRefreshPlanner.class);

    private final @Nullable String basePath;
    private final SchemaCacheStore cache;

    SchemaRefreshPlanner(@Nullable String basePath, @NotNull SchemaCacheStore cache) {
        this.basePath = basePath;
        this.cache = cache;
    }

    /**
     * The waves of actions to dry-run, in the order they must run. Actions declared in a file the
     * last compilation reported as failing are left out so they keep their last known schema
     * instead of being re-extracted from stale or absent SQL.
     */
    @NotNull
    List<List<SortableAction>> planWaves(@NotNull List<SortableAction> sorted,
                                         boolean forceRefresh,
                                         @NotNull Set<String> failedFileNames) {
        List<SortableAction> toRefresh = actionsToRefresh(sorted, forceRefresh).stream()
                .filter(action -> !hasFailedToCompile(action, failedFileNames))
                .collect(Collectors.toList());
        logSkipped(sorted.size(), toRefresh.size());
        return toRefresh.isEmpty() ? List.of() : computeWaves(toRefresh);
    }

    @NotNull
    private List<SortableAction> actionsToRefresh(@NotNull List<SortableAction> allActions,
                                                  boolean forceRefresh) {
        if (forceRefresh) {
            LOG.info("Force refresh enabled, refreshing all actions");
            return allActions;
        }
        if (basePath == null) {
            LOG.warn("Project base path is null, refreshing all actions");
            return allActions;
        }
        Set<String> affected = propagateToDependents(collectModifiedFqns(allActions), allActions);
        return allActions.stream()
                .filter(a -> affected.contains(a.target().getFullName()))
                .collect(Collectors.toList());
    }

    @NotNull
    private Set<String> collectModifiedFqns(@NotNull List<SortableAction> actions) {
        Set<String> modified = new HashSet<>();
        for (SortableAction action : actions) {
            String fqn = action.target().getFullName();
            if (isModified(fqn, ActionSourceFiles.fileNameOf(action))) modified.add(fqn);
        }
        return modified;
    }

    /**
     * Whether an action must be re-extracted. This check is read-only: the modification time is
     * recorded once the extraction succeeds, so an action whose dry-run failed stays modified and
     * is retried on the next refresh instead of being pinned to a stale schema.
     */
    private boolean isModified(@NotNull String fqn, @Nullable String fileName) {
        if (fileName == null) return !cache.contains(fqn);
        Long recorded = cache.persistedModificationTime(fqn);
        return recorded == null
                || basePath == null
                || recorded < ActionSourceFiles.modificationTime(basePath, fileName);
    }

    /** Whether an action belongs to a file the last compilation reported as failing. */
    private static boolean hasFailedToCompile(@NotNull SortableAction action,
                                              @NotNull Set<String> failedFileNames) {
        if (failedFileNames.isEmpty()) return false;
        String fileName = ActionSourceFiles.fileNameOf(action);
        if (fileName == null) return false;
        String normalized = fileName.replace("\\", "/");
        return failedFileNames.stream()
                .map(f -> f.replace("\\", "/"))
                .anyMatch(f -> normalized.endsWith(f) || f.endsWith(normalized));
    }

    @NotNull
    private static Set<String> propagateToDependents(@NotNull Set<String> modifiedFqns,
                                                     @NotNull List<SortableAction> allActions) {
        Map<String, Set<String>> reverseDeps = buildReverseDependencies(allActions);
        Set<String> affected = new HashSet<>(modifiedFqns);
        Queue<String> queue = new LinkedList<>(modifiedFqns);

        while (!queue.isEmpty()) {
            reverseDeps.getOrDefault(queue.poll(), Set.of()).stream()
                    .filter(affected::add)
                    .forEach(queue::add);
        }

        logPropagation(modifiedFqns.size(), affected.size());
        return affected;
    }

    @NotNull
    private static Map<String, Set<String>> buildReverseDependencies(@NotNull List<SortableAction> actions) {
        Map<String, Set<String>> reverse = new HashMap<>();
        for (SortableAction action : actions) {
            String fqn = action.target().getFullName();
            action.dependencyTargets().forEach(dep ->
                    reverse.computeIfAbsent(dep.getFullName(), k -> new HashSet<>()).add(fqn)
            );
        }
        return reverse;
    }

    @NotNull
    private static List<List<SortableAction>> computeWaves(@NotNull List<SortableAction> actions) {
        Map<String, Integer> levelByFqn = computeLevelByFqn(actions);
        int maxLevel = levelByFqn.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<List<SortableAction>> waves = new ArrayList<>(maxLevel + 1);
        for (int i = 0; i <= maxLevel; i++) waves.add(new ArrayList<>());

        actions.forEach(a -> waves.get(levelByFqn.getOrDefault(a.target().getFullName(), 0)).add(a));

        LOG.info("Computed " + (maxLevel + 1) + " wave(s) for " + actions.size() + " actions");
        return waves.stream().filter(l -> !l.isEmpty()).collect(Collectors.toList());
    }

    @NotNull
    private static Map<String, Integer> computeLevelByFqn(@NotNull List<SortableAction> actions) {
        Map<String, SortableAction> byFqn = actions.stream()
                .collect(Collectors.toMap(a -> a.target().getFullName(), a -> a, (a, b) -> a));
        Map<String, Integer> levels = new HashMap<>();
        Set<String> fqns = byFqn.keySet();
        actions.forEach(a -> resolveLevel(a, byFqn, fqns, levels, new HashSet<>()));
        return levels;
    }

    private static int resolveLevel(@NotNull SortableAction action,
                                    @NotNull Map<String, SortableAction> byFqn,
                                    @NotNull Set<String> knownFqns,
                                    @NotNull Map<String, Integer> levels,
                                    @NotNull Set<String> visiting) {
        String fqn = action.target().getFullName();
        if (levels.containsKey(fqn)) return levels.get(fqn);
        if (!visiting.add(fqn)) return 0;

        int maxDepLevel = action.dependencyTargets().stream()
                .filter(dep -> knownFqns.contains(dep.getFullName()))
                .mapToInt(dep -> resolveLevel(byFqn.get(dep.getFullName()), byFqn, knownFqns, levels, visiting))
                .max()
                .orElse(-1);

        int level = maxDepLevel + 1;
        levels.put(fqn, level);
        visiting.remove(fqn);
        return level;
    }

    private static void logSkipped(int total, int toRefresh) {
        int skipped = total - toRefresh;
        if (skipped > 0) LOG.info("Skipping " + skipped + " unchanged actions, refreshing " + toRefresh);
    }

    private static void logPropagation(int modifiedCount, int affectedCount) {
        if (affectedCount > modifiedCount) {
            LOG.info("Propagated refresh to " + (affectedCount - modifiedCount)
                    + " dependent actions (" + modifiedCount + " directly modified)");
        }
    }
}
