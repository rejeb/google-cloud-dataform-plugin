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

import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Runs the extraction of planned actions as soon as the actions they read are done, rather than
 * wave by wave: an action no longer waits for the slowest dry-run of the previous wave when what it
 * reads finished long before.
 *
 * <p>The planned waves give a topological order, cycles already broken. An action only waits for
 * the dependencies scheduled before it in that order, so no wait can close a cycle, and a wait is a
 * composition of futures: no thread of the executor ever blocks on another task.</p>
 */
final class SchemaExtractionScheduler {

    private SchemaExtractionScheduler() {
    }

    /**
     * Runs the extraction of every planned action, each one once the planned actions it depends on
     * have run, whatever their outcome, and returns when all have run.
     *
     * @param waves      the planned waves, in the order they were planned
     * @param executor   the executor that bounds how many extractions run at once
     * @param extraction the extraction of one action
     */
    static void runAll(@NotNull List<List<SortableAction>> waves,
                       @NotNull Executor executor,
                       @NotNull Consumer<SortableAction> extraction) {
        Map<String, CompletableFuture<?>> scheduled = new HashMap<>();
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (List<SortableAction> wave : waves) {
            for (SortableAction action : wave) {
                CompletableFuture<?> task = CompletableFuture
                        .allOf(scheduledDependencies(action, scheduled))
                        .handle((ignored, failure) -> null)
                        .thenRunAsync(() -> extraction.accept(action), executor);
                scheduled.putIfAbsent(action.target().getFullName(), task);
                all.add(task);
            }
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
    }

    private static CompletableFuture<?> @NotNull [] scheduledDependencies(
            @NotNull SortableAction action,
            @NotNull Map<String, CompletableFuture<?>> scheduled) {
        List<CompletableFuture<?>> dependencies = new ArrayList<>();
        for (Target dependency : action.dependencyTargets()) {
            CompletableFuture<?> task = dependency == null ? null : scheduled.get(dependency.getFullName());
            if (task != null) dependencies.add(task);
        }
        return dependencies.toArray(CompletableFuture[]::new);
    }
}
