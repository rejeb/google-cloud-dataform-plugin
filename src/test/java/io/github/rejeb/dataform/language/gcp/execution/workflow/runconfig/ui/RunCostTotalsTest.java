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
import io.github.rejeb.dataform.language.gcp.pricing.CostFormat;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import io.github.rejeb.dataform.language.gcp.pricing.PriceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCostTotalsTest {

    private static final long GIB = 1L << 30;
    private static final Instant LIST_DATE = Instant.parse("2026-11-01T07:00:00Z");
    private static final Function<String, PriceQuote> QUOTES = location -> switch (location) {
        case "europe-west1" -> new PriceQuote("europe-west1", PriceSource.LIVE, new BigDecimal("7.50"), "USD", LIST_DATE, false);
        case "reserved" -> new PriceQuote("reserved", PriceSource.CAPACITY, null, "USD", null, false);
        default -> new PriceQuote(location, PriceSource.LIVE, new BigDecimal("6.25"), "USD", LIST_DATE, false);
    };

    private final Map<String, BigQueryJobDetails> details = new HashMap<>();

    @Test
    void finishedActionsAreSummed() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", "j2"));
        details.put("j1", details("j1", "EU", "DONE", Math.round(21.42 * GIB)));
        details.put("j2", details("j2", "EU", "DONE", Math.round(17.33 * GIB)));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertEquals(Math.round(21.42 * GIB) + Math.round(17.33 * GIB), totals.bytesBilled());
        assertEquals("$0.2365", CostFormat.money(totals.cost(), totals.currency()));
        assertTrue(totals.complete());
        assertEquals("all 2 actions", totals.coverageNote());
        assertEquals("$0.2365", totals.costText().value());
        assertEquals("EU · $6.25 per TiB billed · list price", totals.costText().note());
        assertEquals("<html>Total cost of the bytes billed by the actions of this run.<br>"
                + "EU · $6.25 per TiB billed · list price<br>Excludes the monthly free TiB and contract discounts.</html>", totals.costText().tooltip());
    }

    @Test
    void aFailedJobAddsBytesButNoCost() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", "j2"));
        details.put("j1", details("j1", "EU", "DONE", 10 * GIB));
        details.put("j2", details("j2", "EU", "FAILED", 10 * GIB));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertEquals(20 * GIB, totals.bytesBilled());
        assertEquals(0, CostFormat.costOf(10 * GIB, new BigDecimal("6.25")).compareTo(totals.cost()));
    }

    @Test
    void actionsWithoutJobAreNotCounted() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", null));
        details.put("j1", details("j1", "EU", "DONE", GIB));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertEquals(1, totals.withJobs());
        assertEquals(1, totals.counted());
        assertTrue(totals.complete());
        assertEquals("all 1 action", totals.coverageNote());
    }

    @Test
    void missingDetailsKeepTheTotalIncomplete() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", "j2"), action("c", "j3"));
        details.put("j1", details("j1", "EU", "DONE", GIB));
        details.put("j2", details("j2", "EU", "DONE", GIB));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertEquals(2, totals.counted());
        assertEquals(3, totals.withJobs());
        assertFalse(totals.complete());
        assertEquals("so far, 2 of 3 actions", totals.coverageNote());
        assertTrue(totals.costText().note().startsWith("so far · EU · "), totals.costText().note());
    }

    @Test
    void aRunningRunIsNeverComplete() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"));
        details.put("j1", details("j1", "EU", "DONE", GIB));

        assertFalse(RunCostTotals.of(actions, details, QUOTES, false).complete());
    }

    @Test
    void twoLocationsMakeTheLocationMixed() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", "j2"));
        details.put("j1", details("j1", "EU", "DONE", GIB));
        details.put("j2", details("j2", "europe-west1", "DONE", GIB));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertNull(totals.quote());
        assertTrue(totals.mixedLocations());
        BigDecimal expected = CostFormat.costOf(GIB, new BigDecimal("6.25"))
                .add(CostFormat.costOf(GIB, new BigDecimal("7.50")));
        assertEquals(0, expected.compareTo(totals.cost()));
        assertEquals("mixed locations", totals.costText().note());
    }

    @Test
    void capacityQuotesAddBytesOnly() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", "j2"));
        details.put("j1", details("j1", "EU", "DONE", GIB));
        details.put("j2", details("j2", "reserved", "DONE", 100 * GIB));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertEquals(101 * GIB, totals.bytesBilled());
        assertEquals(0, CostFormat.costOf(GIB, new BigDecimal("6.25")).compareTo(totals.cost()));
        assertEquals(1, totals.capacityActions());
        assertTrue(totals.costText().note().endsWith(" · 1 action on reservations"), totals.costText().note());
    }

    @Test
    void aJobOnAReservationAddsBytesOnly() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"), action("b", "j2"));
        details.put("j1", details("j1", "EU", "DONE", GIB));
        details.put("j2", new BigQueryJobDetails("j2", "p", "EU", "DONE", null, 100 * GIB, 100 * GIB,
                null, null, null, List.of(), "prod-etl"));

        RunCostTotals totals = RunCostTotals.of(actions, details, QUOTES, true);

        assertEquals(101 * GIB, totals.bytesBilled());
        assertEquals(0, CostFormat.costOf(GIB, new BigDecimal("6.25")).compareTo(totals.cost()));
        assertEquals(1, totals.capacityActions());
    }

    @Test
    void onlyCapacityHasNoPrice() {
        List<InvocationActionResult> actions = List.of(action("a", "j1"));
        details.put("j1", details("j1", "reserved", "DONE", GIB));

        assertEquals("capacity pricing", RunCostTotals.of(actions, details, QUOTES, true).costText().value());
        assertEquals("", RunCostTotals.of(actions, details, QUOTES, true).costText().tooltip());
    }

    private static InvocationActionResult action(String target, String jobId) {
        return new InvocationActionResult(target, InvocationActionState.SUCCEEDED, null, null, null,
                jobId, jobId == null ? null : "p", "EU", "d", null);
    }

    private static BigQueryJobDetails details(String jobId, String location, String status, long billed) {
        return new BigQueryJobDetails(jobId, "p", location, status, null, billed, billed, null, null, null, List.of(),
                null);
    }
}
