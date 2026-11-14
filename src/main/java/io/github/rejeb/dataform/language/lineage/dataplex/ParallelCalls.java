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
package io.github.rejeb.dataform.language.lineage.dataplex;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Runs the independent calls of one action together and gives their results in submission order.
 * At most {@link #MAX_IN_FLIGHT} calls run at once across the plugin; a call waiting on calls of its
 * own gives its place back while it waits.
 */
public final class ParallelCalls {

    public static final int MAX_IN_FLIGHT = 64;

    private static final ParallelCalls SHARED =
            new ParallelCalls(Executors.newVirtualThreadPerTaskExecutor(), MAX_IN_FLIGHT);
    private static final ParallelCalls DIRECT = new ParallelCalls(Runnable::run, 0);

    private final Executor executor;
    private final @Nullable Semaphore permits;
    private final ThreadLocal<Boolean> holding = ThreadLocal.withInitial(() -> false);

    ParallelCalls(@NotNull Executor executor, int maxInFlight) {
        this.executor = executor;
        this.permits = maxInFlight > 0 ? new Semaphore(maxInFlight) : null;
    }

    /**
     * The plugin-wide instance: one virtual thread per call, at most {@link #MAX_IN_FLIGHT} in flight.
     */
    public static @NotNull ParallelCalls shared() {
        return SHARED;
    }

    /**
     * An instance running every call on the caller thread, one after the other.
     */
    public static @NotNull ParallelCalls direct() {
        return DIRECT;
    }

    /**
     * Runs every call, waits for all of them and returns their results in submission order. When calls
     * failed, the failure of the first failed call in submission order is rethrown, a checked exception
     * wrapped in a {@link RuntimeException}.
     */
    public <T> @NotNull List<T> all(@NotNull List<? extends Callable<? extends T>> calls) {
        List<CompletableFuture<T>> futures = new ArrayList<>(calls.size());
        for (Callable<? extends T> call : calls) {
            futures.add(CompletableFuture.supplyAsync(() -> run(call), executor));
        }
        boolean gaveBack = givePermitBack();
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).exceptionally(failure -> null).join();
        } finally {
            if (gaveBack) takePermit();
        }
        List<T> results = new ArrayList<>(futures.size());
        for (CompletableFuture<T> future : futures) {
            try {
                results.add(future.join());
            } catch (CompletionException failure) {
                throw unwrap(failure.getCause());
            }
        }
        return results;
    }

    private <T> T run(@NotNull Callable<? extends T> call) {
        takePermit();
        holding.set(true);
        try {
            return call.call();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CompletionException(failure);
        } finally {
            holding.set(false);
            if (permits != null) permits.release();
        }
    }

    private boolean givePermitBack() {
        if (permits == null || !holding.get()) return false;
        permits.release();
        return true;
    }

    private void takePermit() {
        if (permits == null) return;
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(interrupted);
        }
    }

    private static @NotNull RuntimeException unwrap(@Nullable Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error error) throw error;
        return new RuntimeException(cause);
    }
}
