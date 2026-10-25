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
package io.github.rejeb.dataform.language.compilation;

import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Tells whether the compiled graph matches the Dataform sources as they are now.
 *
 * <p>Every change of a source moves a generation counter; a compilation records the generation
 * it saw once it has flushed the documents. The graph is up to date while no change came after
 * the generation the last compilation covered. Readers that act on the graph wait for an
 * up-to-date one through {@link #upToDateGraph}, which shares one compilation between all of
 * them.</p>
 */
final class CompilationFreshness {

    static final int MAX_ATTEMPTS = 3;

    private final Executor executor;
    private final AtomicLong sourceGeneration = new AtomicLong();
    private final AtomicLong compiledGeneration = new AtomicLong(-1);
    private final AtomicReference<CompletableFuture<CompiledGraph>> inFlight = new AtomicReference<>();

    CompilationFreshness(@NotNull Executor executor) {
        this.executor = executor;
    }

    void noteSourceChanged() {
        sourceGeneration.incrementAndGet();
    }

    long currentGeneration() {
        return sourceGeneration.get();
    }

    void markCompiled(long generation) {
        compiledGeneration.accumulateAndGet(generation, Math::max);
    }

    boolean isUpToDate() {
        return compiledGeneration.get() == sourceGeneration.get();
    }

    @NotNull
    CompletableFuture<CompiledGraph> upToDateGraph(@NotNull Supplier<CompiledGraph> compile,
                                                   @NotNull Supplier<CompiledGraph> current) {
        CompiledGraph graph = current.get();
        if (graph != null && isUpToDate()) {
            return CompletableFuture.completedFuture(graph);
        }
        CompletableFuture<CompiledGraph> created = new CompletableFuture<>();
        CompletableFuture<CompiledGraph> running = inFlight.compareAndExchange(null, created);
        if (running != null) {
            return running;
        }
        executor.execute(() -> compileUntilUpToDate(compile, created));
        return created;
    }

    private void compileUntilUpToDate(@NotNull Supplier<CompiledGraph> compile,
                                      @NotNull CompletableFuture<CompiledGraph> result) {
        CompiledGraph graph = null;
        Throwable failure = null;
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                graph = compile.get();
                if (isUpToDate()) {
                    break;
                }
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            inFlight.compareAndSet(result, null);
        }
        if (failure != null) {
            result.completeExceptionally(failure);
        } else {
            result.complete(graph);
        }
    }
}
