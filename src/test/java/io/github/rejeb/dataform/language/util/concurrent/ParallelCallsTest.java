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
package io.github.rejeb.dataform.language.util.concurrent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelCallsTest {

    private static Callable<String> sleeping(String value, long millis) {
        return () -> {
            Thread.sleep(millis);
            return value;
        };
    }

    @Test
    void resultsComeInSubmissionOrderWhateverTheCompletionOrder() {
        List<String> results = ParallelCalls.shared().all(List.of(
                sleeping("a", 30), sleeping("b", 10), sleeping("c", 20)));

        assertEquals(List.of("a", "b", "c"), results);
    }

    @Test
    void theFirstFailureInSubmissionOrderIsRethrownAfterAllEnd() {
        AtomicBoolean firstRan = new AtomicBoolean();
        AtomicBoolean lastEnded = new AtomicBoolean();
        List<Callable<String>> calls = List.of(
                () -> {
                    firstRan.set(true);
                    return "1";
                },
                () -> {
                    Thread.sleep(20);
                    throw new IllegalStateException("2");
                },
                () -> {
                    lastEnded.set(true);
                    throw new IllegalStateException("3");
                });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ParallelCalls.shared().all(calls));

        assertEquals("2", failure.getMessage());
        assertTrue(firstRan.get());
        assertTrue(lastEnded.get());
    }

    @Test
    void aCheckedFailureIsWrappedInARuntimeException() {
        List<Callable<String>> calls = List.of(() -> {
            throw new java.io.IOException("io");
        });

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> ParallelCalls.direct().all(calls));

        assertTrue(failure.getCause() instanceof java.io.IOException);
    }

    @Test
    void noMoreThanTheCapRunAtOnce() {
        ParallelCalls calls = new ParallelCalls(Executors.newVirtualThreadPerTaskExecutor(), 2);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger highest = new AtomicInteger();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int value = i;
            tasks.add(() -> {
                highest.accumulateAndGet(running.incrementAndGet(), Math::max);
                Thread.sleep(20);
                running.decrementAndGet();
                return value;
            });
        }

        List<Integer> results = calls.all(tasks);

        assertEquals(List.of(0, 1, 2, 3, 4, 5), results);
        assertTrue(highest.get() <= 2, "highest concurrency " + highest.get());
    }

    @Test
    void nestedAllDoesNotDeadlock() {
        ParallelCalls calls = new ParallelCalls(Executors.newVirtualThreadPerTaskExecutor(), 1);
        Callable<List<String>> outer = () -> calls.all(List.of(
                sleeping("x", 5), sleeping("y", 5), sleeping("z", 5)));

        List<List<String>> results = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> calls.all(List.of(outer)));

        assertEquals(List.of(List.of("x", "y", "z")), results);
    }

    @Test
    void directRunsOnTheCallerThread() {
        Thread caller = Thread.currentThread();

        List<Thread> threads = ParallelCalls.direct().all(List.of(Thread::currentThread, Thread::currentThread));

        assertEquals(List.of(caller, caller), threads);
    }
}
