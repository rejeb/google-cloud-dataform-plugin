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
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestRequestTest {

    private final Deque<Runnable> background = new ArrayDeque<>();
    private final Deque<Runnable> ui = new ArrayDeque<>();
    private final LatestRequest latest = new LatestRequest(background::add, ui::add);
    private final List<Object> delivered = new ArrayList<>();
    private final List<RuntimeException> failures = new ArrayList<>();

    @Test
    void theOnlyRequestDeliversItsResult() {
        latest.submit(() -> "a", delivered::add);

        drain(background);
        drain(ui);

        assertEquals(List.of("a"), delivered);
    }

    @Test
    void aLaterRequestDropsAnEarlierResult() {
        latest.submit(() -> "a", delivered::add);
        latest.submit(() -> "b", delivered::add);

        drain(background);
        drain(ui);

        assertEquals(List.of("b"), delivered);
    }

    @Test
    void aLaterRequestDropsAnEarlierResultFinishingLast() {
        latest.submit(() -> "a", delivered::add);
        latest.submit(() -> "b", delivered::add);

        background.pollLast().run();
        background.pollLast().run();
        drain(ui);

        assertEquals(List.of("b"), delivered);
    }

    @Test
    void cancelDropsThePendingResult() {
        latest.submit(() -> "a", delivered::add);

        drain(background);
        latest.cancel();
        drain(ui);

        assertTrue(delivered.isEmpty());
    }

    @Test
    void aFailureOfTheDeclaredTypeIsDelivered() {
        IllegalStateException failure = new IllegalStateException("boom");
        latest.submit(() -> {
            throw failure;
        }, delivered::add, IllegalStateException.class, failures::add);

        drain(background);
        drain(ui);

        assertEquals(List.of(failure), failures);
        assertTrue(delivered.isEmpty());
    }

    @Test
    void aCancelledFailureIsDropped() {
        latest.submit(() -> {
            throw new IllegalStateException("boom");
        }, delivered::add, IllegalStateException.class, failures::add);

        drain(background);
        latest.cancel();
        drain(ui);

        assertTrue(failures.isEmpty());
    }

    @Test
    void aFailureOfAnotherTypePropagates() {
        latest.submit(() -> {
            throw new IllegalStateException("boom");
        }, delivered::add, IllegalArgumentException.class, failures::add);

        assertThrows(IllegalStateException.class, () -> background.poll().run());
        assertTrue(ui.isEmpty());
    }

    @Test
    void aProcessCanceledExceptionIsNeverCaught() {
        latest.submit(() -> {
            throw new ProcessCanceledException();
        }, delivered::add, RuntimeException.class, failures::add);

        assertThrows(ProcessCanceledException.class, () -> background.poll().run());
        assertTrue(ui.isEmpty());
    }

    @Test
    void awaitDeliversTheValue() {
        latest.await(CompletableFuture.completedFuture("v"), delivered::add);

        drain(ui);

        assertEquals(List.of("v"), delivered);
    }

    @Test
    void awaitIsDroppedByALaterRequest() {
        CompletableFuture<String> future = new CompletableFuture<>();
        latest.await(future, delivered::add);

        latest.cancel();
        future.complete("v");
        drain(ui);

        assertTrue(delivered.isEmpty());
    }

    @Test
    void aFailedFutureDeliversNothing() {
        latest.await(CompletableFuture.failedFuture(new IllegalStateException("boom")), delivered::add);

        drain(ui);

        assertTrue(delivered.isEmpty());
    }

    private static void drain(Deque<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }
}
