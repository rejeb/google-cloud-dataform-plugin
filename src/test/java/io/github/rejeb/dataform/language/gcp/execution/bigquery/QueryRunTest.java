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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRunTest {

    @Test
    void aStartedRunIsRunningWithoutResult() {
        QueryRun run = QueryRun.running("db.analytics.orders", "SELECT 1", null, 1_000, () -> {
        });

        assertEquals(QueryRunState.RUNNING, run.state());
        assertNull(run.result());
        assertFalse(run.isFinished());
        assertEquals(1_000, run.createdAt());
    }

    @Test
    void aSuccessfulResultFinishesTheRunAsSucceeded() {
        BigQueryJobResult result = new BigQueryJobResult("db.analytics.orders", null, null, null);

        QueryRun run = started().finished(result, false);

        assertEquals(QueryRunState.SUCCEEDED, run.state());
        assertSame(result, run.result());
        assertTrue(run.isFinished());
        assertNull(run.cancel());
    }

    @Test
    void anErrorFinishesTheRunAsFailed() {
        QueryRun run = started().finished(new BigQueryJobResult("db.analytics.orders", null, null, "Syntax error"), false);

        assertEquals(QueryRunState.FAILED, run.state());
    }

    @Test
    void aCancelledRunIsCancelledWhateverItsResult() {
        QueryRun run = started().finished(
                new BigQueryJobResult("db.analytics.orders", null, null, "Query execution cancelled"), true);

        assertEquals(QueryRunState.CANCELLED, run.state());
    }

    @Test
    void theShortNameIsTheLastPartOfTheTableName() {
        assertEquals("orders", started().shortName());
        assertEquals("orders", QueryRun.running("orders", "SELECT 1", null, 0, null).shortName());
    }

    private static QueryRun started() {
        return QueryRun.running("db.analytics.orders", "SELECT 1", null, 0, () -> {
        });
    }
}
