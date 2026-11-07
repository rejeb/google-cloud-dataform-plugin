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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueryResultsRegistryTest {

    @Test
    void removingAFailedResultDoesNotThrow() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        QueryRun run = start(registry, "orders");
        registry.finish(run, new BigQueryJobResult("orders", null, null, "Syntax error"), false);

        registry.remove("orders");

        assertNull(registry.get("orders"));
    }

    @Test
    void removingASuccessfulResultDisposesItsPages() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        BigQueryPagedResult pages = mock(BigQueryPagedResult.class);
        QueryRun run = start(registry, "orders");
        registry.finish(run, new BigQueryJobResult("orders", null, pages, null), false);

        registry.remove("orders");

        verify(pages).dispose();
        assertNull(registry.get("orders"));
    }

    @Test
    void aStartedQueryIsListedAsRunning() {
        QueryResultsRegistry registry = new QueryResultsRegistry();

        start(registry, "orders");

        assertEquals(QueryRunState.RUNNING, registry.get("orders").state());
    }

    @Test
    void finishingAQueryKeepsItsResult() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        QueryRun run = start(registry, "orders");
        BigQueryJobResult result = new BigQueryJobResult("orders", null, null, null);

        QueryRun finished = registry.finish(run, result, false);

        assertSame(finished, registry.get("orders"));
        assertEquals(QueryRunState.SUCCEEDED, finished.state());
        assertSame(result, finished.result());
    }

    @Test
    void anOlderRunDoesNotReplaceTheRunThatFollowedIt() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        QueryRun first = start(registry, "orders");
        QueryRun second = start(registry, "orders");
        BigQueryPagedResult pages = mock(BigQueryPagedResult.class);

        assertNull(registry.finish(first, new BigQueryJobResult("orders", null, pages, null), false));

        assertSame(second, registry.get("orders"));
        verify(pages).dispose();
    }

    @Test
    void aRemovedRunIsNotListedAgainWhenItFinishes() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        QueryRun run = start(registry, "orders");
        registry.remove("orders");

        assertNull(registry.finish(run, new BigQueryJobResult("orders", null, null, null), false));

        assertNull(registry.get("orders"));
    }

    @Test
    void rerunningATableDisposesThePagesOfItsLastResult() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        BigQueryPagedResult pages = mock(BigQueryPagedResult.class);
        registry.finish(start(registry, "orders"), new BigQueryJobResult("orders", null, pages, null), false);

        start(registry, "orders");

        verify(pages).dispose();
    }

    @Test
    void removingARunningQueryCancelsIt() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        AtomicBoolean cancelled = new AtomicBoolean();
        registry.start(QueryRun.running("orders", "SELECT 1", null, 0, () -> cancelled.set(true)));

        registry.remove("orders");

        assertTrue(cancelled.get());
    }

    @Test
    void removingTheFinishedQueriesKeepsTheRunningOnes() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        registry.finish(start(registry, "orders"), new BigQueryJobResult("orders", null, null, null), false);
        registry.finish(start(registry, "customers"), new BigQueryJobResult("customers", null, null, "Error"), false);
        start(registry, "payments");

        registry.removeFinished();

        assertEquals(List.of("payments"), registry.getAll().stream().map(QueryRun::tableName).toList());
        assertFalse(registry.hasFinished());
    }

    private static QueryRun start(QueryResultsRegistry registry, String table) {
        QueryRun run = QueryRun.running(table, "SELECT 1", null, 0, () -> {
        });
        registry.start(run);
        return run;
    }
}
