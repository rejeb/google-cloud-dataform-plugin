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

import io.github.rejeb.dataform.language.compilation.model.CompiledTable;
import io.github.rejeb.dataform.language.compilation.model.SortableAction;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An action is extracted once the actions it reads are, and no later: a slow action of the same
 * level must not hold back the reader of another one.
 */
public class SchemaExtractionSchedulerTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    @Timeout(10)
    void aReaderDoesNotWaitForASlowActionItDoesNotRead() throws Exception {
        SortableAction slow = action("slow");
        SortableAction source = action("source");
        SortableAction reader = action("reader", source.target());
        CountDownLatch readerDone = new CountDownLatch(1);
        List<String> finished = new CopyOnWriteArrayList<>();

        SchemaExtractionScheduler.runAll(List.of(List.of(slow, source), List.of(reader)), executor, action -> {
            String name = action.target().getName();
            if (name.equals("slow")) {
                await(readerDone);
            }
            finished.add(name);
            if (name.equals("reader")) {
                readerDone.countDown();
            }
        });

        assertEquals(List.of("source", "reader", "slow"), finished);
    }

    @Test
    @Timeout(10)
    void anActionStartsOnlyOnceEveryActionItReadsIsDone() {
        SortableAction first = action("first");
        SortableAction second = action("second");
        SortableAction reader = action("reader", first.target(), second.target());
        List<String> finished = new CopyOnWriteArrayList<>();

        SchemaExtractionScheduler.runAll(List.of(List.of(first, second), List.of(reader)), executor, action -> {
            if (action.target().getName().equals("second")) sleep();
            finished.add(action.target().getName());
        });

        assertEquals("reader", finished.getLast());
        assertEquals(3, finished.size());
    }

    @Test
    @Timeout(10)
    void aFailedActionStillLetsItsReadersRun() {
        SortableAction failing = action("failing");
        SortableAction reader = action("reader", failing.target());
        List<String> finished = new CopyOnWriteArrayList<>();

        try {
            SchemaExtractionScheduler.runAll(List.of(List.of(failing), List.of(reader)), executor, action -> {
                if (action.target().getName().equals("failing")) throw new IllegalStateException("dry-run");
                finished.add(action.target().getName());
            });
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }

        assertEquals(List.of("reader"), finished);
    }

    @Test
    @Timeout(10)
    void aCycleDoesNotBlockTheRun() {
        Target aTarget = target("a");
        Target bTarget = target("b");
        SortableAction a = action(aTarget, bTarget);
        SortableAction b = action(bTarget, aTarget);
        List<String> finished = new CopyOnWriteArrayList<>();

        SchemaExtractionScheduler.runAll(List.of(List.of(a), List.of(b)), executor,
                action -> finished.add(action.target().getName()));

        assertEquals(List.of("a", "b"), finished);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "the reader waited for an action it does not read");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static SortableAction action(String name, Target... dependencies) {
        return action(target(name), dependencies);
    }

    private static SortableAction action(Target target, Target... dependencies) {
        CompiledTable table = new CompiledTable();
        set(table, "target", target);
        set(table, "dependencyTargets", List.of(dependencies));
        return SortableAction.of(table);
    }

    private static Target target(String name) {
        Target target = new Target();
        set(target, "database", "p");
        set(target, "schema", "d");
        set(target, "name", name);
        return target;
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
