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
package io.github.rejeb.dataform.language.fileEditor.cost;

import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs at most a few cost dry-runs at once and keeps the latest successful estimates.
 */
public final class QueryCostEstimatorImpl implements QueryCostEstimator {

    private static final int MAX_PARALLEL_DRY_RUNS = 4;
    private static final int MAX_ENTRIES = 200;
    private static final String NOT_STARTED = "The cost dry-run could not be started";

    private final DryRunBytesRunner runner;
    private final Executor executor;
    private final Map<Key, CompletableFuture<CostEstimate>> estimates;

    /**
     * Creates the project service.
     */
    public QueryCostEstimatorImpl(@NotNull Project project) {
        this(new BigQueryDryRunBytesRunner(),
                AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform cost estimate", MAX_PARALLEL_DRY_RUNS),
                MAX_ENTRIES);
    }

    QueryCostEstimatorImpl(@NotNull DryRunBytesRunner runner, @NotNull Executor executor, int maxEntries) {
        this.runner = runner;
        this.executor = executor;
        this.estimates = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, CompletableFuture<CostEstimate>> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public @NotNull CompletableFuture<CostEstimate> estimate(@NotNull String billingProject,
                                                             @Nullable String location,
                                                             @NotNull String sql) {
        Key key = new Key(billingProject, location == null ? "" : location, sql);
        CompletableFuture<CostEstimate> future;
        synchronized (estimates) {
            future = estimates.get(key);
            if (future != null) {
                return future;
            }
            future = new CompletableFuture<>();
            estimates.put(key, future);
        }
        CompletableFuture<CostEstimate> started = future;
        try {
            executor.execute(() -> run(key, started, location));
        } catch (RejectedExecutionException e) {
            complete(key, started, CostEstimate.failure(NOT_STARTED));
        }
        return started;
    }

    private void run(@NotNull Key key, @NotNull CompletableFuture<CostEstimate> future, @Nullable String location) {
        CostEstimate result;
        try {
            result = runner.run(key.billingProject(), location, key.sql());
        } catch (RuntimeException e) {
            result = CostEstimate.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        complete(key, future, result);
    }

    private void complete(@NotNull Key key, @NotNull CompletableFuture<CostEstimate> future, @NotNull CostEstimate result) {
        if (result.failed()) {
            synchronized (estimates) {
                estimates.remove(key, future);
            }
        }
        future.complete(result);
    }

    private record Key(@NotNull String billingProject, @NotNull String location, @NotNull String sql) {
    }
}
