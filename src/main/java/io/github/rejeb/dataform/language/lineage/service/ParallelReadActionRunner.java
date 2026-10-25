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
package io.github.rejeb.dataform.language.lineage.service;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.columns.analysis.UnitAnalysisRunner;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Runs the analyses of a background lineage build side by side, each in a non-blocking read action
 * of its own on a bounded executor. A write action cancels the analyses it meets and they start
 * again after it, so a build in the background never holds up typing, and no thread waits for a
 * read lock while holding one.
 *
 * <p>Only for a caller that holds no read action: it waits for the analyses, and they need the
 * read lock that such a caller would keep from a pending write action.</p>
 */
final class ParallelReadActionRunner implements UnitAnalysisRunner {

    private final Project project;
    private final ExecutorService executor;

    ParallelReadActionRunner(@NotNull Project project, @NotNull ExecutorService executor) {
        this.project = project;
        this.executor = executor;
    }

    @Override
    public @NotNull <T> List<T> runAll(@NotNull List<? extends Supplier<T>> tasks) {
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Supplier<T> task : tasks) {
            futures.add(executor.submit(() -> ReadAction.nonBlocking(task::get)
                    .expireWith(project)
                    .executeSynchronously()));
        }
        List<T> results = new ArrayList<>(tasks.size());
        try {
            for (Future<T> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException e) {
            cancelAll(futures);
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(e);
        } catch (ExecutionException e) {
            cancelAll(futures);
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        }
        return results;
    }

    private static void cancelAll(@NotNull List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }
}
