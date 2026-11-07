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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActionJobDetailsCacheTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> locations = new ArrayList<>();
    private boolean failing;
    private final ActionJobDetailsCache cache = new ActionJobDetailsCache(
            (action, location) -> {
                calls.incrementAndGet();
                locations.add(location);
                return failing ? null : ActionCostTest.details("DONE", 1024L);
            },
            action -> "EU",
            Runnable::run);

    @Test
    void anActionWithoutJobLoadsNothing() {
        assertNull(cache.load(action(InvocationActionState.SUCCEEDED, null)).join());
        assertEquals(0, calls.get());
    }

    @Test
    void twoLoadsOfOneFinishedJobFetchOnce() {
        BigQueryJobDetails first = cache.load(action(InvocationActionState.SUCCEEDED, "job")).join();
        BigQueryJobDetails second = cache.load(action(InvocationActionState.SUCCEEDED, "job")).join();

        assertEquals(1, calls.get());
        assertSame(first, second);
        assertEquals(List.of("EU"), locations);
    }

    @Test
    void aRunningActionIsFetchedAgain() {
        cache.load(action(InvocationActionState.RUNNING, "job")).join();
        cache.load(action(InvocationActionState.RUNNING, "job")).join();

        assertEquals(2, calls.get());
    }

    @Test
    void anActionThatFinishedIsFetchedOnceMore() {
        cache.load(action(InvocationActionState.RUNNING, "job")).join();
        cache.load(action(InvocationActionState.SUCCEEDED, "job")).join();
        cache.load(action(InvocationActionState.SUCCEEDED, "job")).join();

        assertEquals(2, calls.get());
    }

    @Test
    void aFailedFetchIsNotKept() {
        failing = true;
        assertNull(cache.load(action(InvocationActionState.FAILED, "job")).join());
        failing = false;

        cache.load(action(InvocationActionState.FAILED, "job")).join();

        assertEquals(2, calls.get());
    }

    private static InvocationActionResult action(InvocationActionState state, String jobId) {
        return new InvocationActionResult("p.d.t", state, null, null, null, jobId, "p", "EU", "d", null);
    }
}
