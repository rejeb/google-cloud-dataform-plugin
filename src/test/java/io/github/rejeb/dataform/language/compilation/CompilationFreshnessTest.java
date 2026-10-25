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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CompilationFreshnessTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CompilationFreshness freshness = new CompilationFreshness(executor);
    private final AtomicInteger compilations = new AtomicInteger();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private Supplier<CompiledGraph> compilingTo(CompiledGraph graph) {
        return () -> {
            compilations.incrementAndGet();
            freshness.markCompiled(freshness.currentGeneration());
            return graph;
        };
    }

    @Test
    void testInitiallyNotUpToDate() {
        assertFalse(freshness.isUpToDate());
    }

    @Test
    void testUpToDateAfterMarkingTheCurrentGeneration() {
        freshness.markCompiled(freshness.currentGeneration());
        assertTrue(freshness.isUpToDate());

        freshness.noteSourceChanged();
        assertFalse(freshness.isUpToDate());
    }

    @Test
    void testUpToDateGraphReturnsCurrentWithoutCompilingWhenFresh() throws Exception {
        CompiledGraph current = new CompiledGraph();
        freshness.markCompiled(freshness.currentGeneration());

        CompiledGraph result = freshness.upToDateGraph(compilingTo(new CompiledGraph()), () -> current)
                .get(5, TimeUnit.SECONDS);

        assertSame(current, result);
        assertEquals(0, compilations.get());
    }

    @Test
    void testStaleGraphTriggersOneCompilation() throws Exception {
        CompiledGraph compiled = new CompiledGraph();

        CompiledGraph result = freshness.upToDateGraph(compilingTo(compiled), CompiledGraph::new)
                .get(5, TimeUnit.SECONDS);

        assertSame(compiled, result);
        assertEquals(1, compilations.get());
    }

    @Test
    void testConcurrentCallersShareOneCompilation() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CompiledGraph compiled = new CompiledGraph();
        Supplier<CompiledGraph> blocking = () -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return compilingTo(compiled).get();
        };

        CompletableFuture<CompiledGraph> first = freshness.upToDateGraph(blocking, CompiledGraph::new);
        CompletableFuture<CompiledGraph> second = freshness.upToDateGraph(blocking, CompiledGraph::new);
        release.countDown();

        assertSame(first, second);
        assertSame(compiled, first.get(5, TimeUnit.SECONDS));
        assertEquals(1, compilations.get());
    }

    @Test
    void testReusedGraphMarksTheGenerationUpToDate() throws Exception {
        CompiledGraph cached = new CompiledGraph();
        freshness.markCompiled(freshness.currentGeneration());
        freshness.noteSourceChanged();

        freshness.upToDateGraph(compilingTo(cached), () -> cached).get(5, TimeUnit.SECONDS);

        assertTrue(freshness.isUpToDate());
        assertEquals(1, compilations.get());
    }

    @Test
    void testGivesUpAfterThreeAttemptsWhileSourcesKeepChanging() throws Exception {
        Supplier<CompiledGraph> changing = () -> {
            CompiledGraph graph = compilingTo(new CompiledGraph()).get();
            freshness.noteSourceChanged();
            return graph;
        };

        CompiledGraph result = freshness.upToDateGraph(changing, CompiledGraph::new).get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(3, compilations.get());
    }

    @Test
    void testFailedCompilationDoesNotPoisonTheNextRequest() throws Exception {
        Supplier<CompiledGraph> failing = () -> {
            throw new IllegalStateException("boom");
        };
        CompletableFuture<CompiledGraph> failed = freshness.upToDateGraph(failing, CompiledGraph::new);
        assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));

        CompiledGraph compiled = new CompiledGraph();
        CompiledGraph result = freshness.upToDateGraph(compilingTo(compiled), CompiledGraph::new)
                .get(5, TimeUnit.SECONDS);

        assertSame(compiled, result);
    }
}
