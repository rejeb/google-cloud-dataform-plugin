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

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCostEstimatorImplTest {

    private final ScriptedRunner runner = new ScriptedRunner();

    @Test
    void theSameQueryIsDryRunOnce() {
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, Runnable::run, 200);

        CostEstimate first = estimator.estimate("billing", "EU", "select 1").join();
        CostEstimate second = estimator.estimate("billing", "EU", "select 1").join();

        assertEquals(1, runner.calls.get());
        assertEquals(first.bytesProcessed(), second.bytesProcessed());
    }

    @Test
    void aDifferentQueryIsANewDryRun() {
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, Runnable::run, 200);

        estimator.estimate("billing", "EU", "select 1").join();
        estimator.estimate("billing", "EU", "select 2").join();

        assertEquals(2, runner.calls.get());
    }

    @Test
    void anotherLocationOrBillingProjectIsANewDryRun() {
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, Runnable::run, 200);

        estimator.estimate("billing", "EU", "select 1").join();
        estimator.estimate("billing", "US", "select 1").join();
        estimator.estimate("other", "EU", "select 1").join();

        assertEquals(3, runner.calls.get());
    }

    @Test
    void aFailureIsNotKept() {
        runner.script.add(CostEstimate.failure("Not found: Table p:d.upstream"));
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, Runnable::run, 200);

        CostEstimate failed = estimator.estimate("billing", "EU", "select 1").join();
        CostEstimate retried = estimator.estimate("billing", "EU", "select 1").join();

        assertTrue(failed.failed());
        assertFalse(retried.failed());
        assertEquals(2, runner.calls.get());
    }

    @Test
    void theCacheIsBounded() {
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, Runnable::run, 2);

        estimator.estimate("billing", "EU", "a").join();
        estimator.estimate("billing", "EU", "b").join();
        estimator.estimate("billing", "EU", "c").join();
        estimator.estimate("billing", "EU", "a").join();

        assertEquals(4, runner.calls.get());
    }

    @Test
    void aRunnerExceptionBecomesAFailure() {
        runner.boom = true;
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, Runnable::run, 200);

        CostEstimate estimate = estimator.estimate("billing", "EU", "select 1").join();

        assertTrue(estimate.failed());
        assertEquals("boom", estimate.error());
    }

    @Test
    void aQueryInFlightIsSharedByLaterViews() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        runner.gate = new CountDownLatch(1);
        QueryCostEstimatorImpl estimator = new QueryCostEstimatorImpl(runner, pool, 200);

        CompletableFuture<CostEstimate> first = estimator.estimate("billing", "EU", "select 1");
        CompletableFuture<CostEstimate> second = estimator.estimate("billing", "EU", "select 1");
        runner.gate.countDown();
        first.join();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertSame(first, second);
        assertEquals(1, runner.calls.get());
    }

    private static final class ScriptedRunner implements DryRunBytesRunner {

        private final AtomicInteger calls = new AtomicInteger();
        private final Deque<CostEstimate> script = new ArrayDeque<>();
        private volatile boolean boom;
        private volatile CountDownLatch gate;

        @Override
        public CostEstimate run(String billingProject, String location, String sql) {
            calls.incrementAndGet();
            CountDownLatch latch = gate;
            if (latch != null) {
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (boom) {
                throw new RuntimeException("boom");
            }
            synchronized (script) {
                if (!script.isEmpty()) {
                    return script.poll();
                }
            }
            return CostEstimate.success(sql.length() * 1_000_000L, location);
        }
    }
}
