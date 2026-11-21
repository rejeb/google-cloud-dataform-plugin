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
package io.github.rejeb.dataform.language.ui;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs one kind of request in the background and hands only the answer of the latest one to the
 * UI: an answer arriving after a newer request, or after {@link #cancel()}, is dropped.
 */
public final class LatestRequest {

    private final Executor background;
    private final Executor ui;
    private final AtomicLong generation = new AtomicLong();

    /**
     * @param background where the work runs
     * @param ui how an answer reaches the UI, with the modality the caller needs
     */
    public LatestRequest(@NotNull Executor background, @NotNull Executor ui) {
        this.background = background;
        this.ui = ui;
    }

    /**
     * Runs the work and gives its result to {@code onResult} on the UI, unless a later request came.
     * A failure of the work propagates on the background thread.
     */
    public <T> void submit(@NotNull Supplier<? extends T> work, @NotNull Consumer<? super T> onResult) {
        long asked = generation.incrementAndGet();
        background.execute(() -> {
            T result = work.get();
            deliver(asked, () -> onResult.accept(result));
        });
    }

    /**
     * Runs the work and gives its result to {@code onResult}, or its failure of the given type to
     * {@code onFailure}, on the UI, unless a later request came. Other failures, and a
     * {@link ProcessCanceledException}, propagate on the background thread.
     */
    public <T, E extends RuntimeException> void submit(@NotNull Supplier<? extends T> work,
                                                       @NotNull Consumer<? super T> onResult,
                                                       @NotNull Class<E> failure,
                                                       @NotNull Consumer<? super E> onFailure) {
        long asked = generation.incrementAndGet();
        background.execute(() -> {
            T result;
            try {
                result = work.get();
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (RuntimeException e) {
                if (!failure.isInstance(e)) throw e;
                E failed = failure.cast(e);
                deliver(asked, () -> onFailure.accept(failed));
                return;
            }
            deliver(asked, () -> onResult.accept(result));
        });
    }

    /**
     * Gives the value of the future to {@code onResult} on the UI when it completes, unless a later
     * request came. A failed future gives nothing.
     */
    public <T> void await(@NotNull CompletableFuture<? extends T> future, @NotNull Consumer<? super T> onResult) {
        long asked = generation.incrementAndGet();
        future.thenAccept(result -> deliver(asked, () -> onResult.accept(result)));
    }

    /**
     * Drops the answer of every pending request.
     */
    public void cancel() {
        generation.incrementAndGet();
    }

    private void deliver(long asked, @NotNull Runnable answer) {
        if (asked != generation.get()) return;
        ui.execute(() -> {
            if (asked == generation.get()) answer.run();
        });
    }
}
