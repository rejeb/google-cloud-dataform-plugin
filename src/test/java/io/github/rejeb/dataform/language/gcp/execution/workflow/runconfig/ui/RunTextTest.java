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
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.rejeb.dataform.language.util.Utils.formatBytes;

class RunTextTest {

    private static final Instant START = Instant.parse("2026-11-02T20:52:49Z");

    @Test
    void durationsUnderAMinuteKeepOneDecimalLikeThePrototype() {
        assertEquals("5 s", RunText.duration(Duration.ofSeconds(5)));
        assertEquals("22.5 s", RunText.duration(Duration.ofMillis(22_480)));
        assertEquals("0 s", RunText.duration(Duration.ofMillis(20)));
    }

    @Test
    void longerDurationsCountMinutesAndHours() {
        assertEquals("1 m 5 s", RunText.duration(Duration.ofSeconds(65)));
        assertEquals("2 h 3 m", RunText.duration(Duration.ofMinutes(123).plusSeconds(4)));
    }

    @Test
    void aDurationNeedsBothEnds() {
        assertEquals("41 s", RunText.duration(START, START.plusSeconds(41)));
        assertNull(RunText.duration(START, null));
        assertNull(RunText.duration(null, START));
    }

    @Test
    void statesReadAsWordsLikeThePrototype() {
        assertEquals("Succeeded", RunText.state(InvocationActionState.SUCCEEDED));
        assertEquals("Failed", RunText.state(InvocationActionState.FAILED));
        assertEquals("Waiting", RunText.state(InvocationActionState.PENDING));
        assertEquals("Skipped", RunText.state(InvocationActionState.DISABLED));
        assertEquals("Running", RunText.state(WorkflowInvocationState.RUNNING));
        assertEquals("Cancelled", RunText.state(WorkflowInvocationState.CANCELLED));
    }

    @Test
    void aTargetSplitsIntoSchemaAndName() {
        assertEquals("users", RunText.name("p.ds.users"));
        assertEquals("ds", RunText.schema("p.ds.users"));
        assertEquals("ds.users", RunText.schemaAndName("p.ds.users"));
        assertEquals("users", RunText.name("users"));
        assertNull(RunText.schema("users"));
        assertEquals("users", RunText.schemaAndName("users"));
    }

    @Test
    void theElapsedTimeRunsFromTheStartToTheEnd() {
        InvocationSummary summary = new InvocationSummary("inv", "c", "WORKSPACE", null, null, START,
                START.plusMillis(22_500));

        assertEquals(Duration.ofMillis(22_500), RunText.elapsed(summary));
        assertNull(RunText.elapsed(null));
    }

    @Test
    void actionsAreCountedByOutcome() {
        ActionCounts counts = ActionCounts.of(List.of(
                action(InvocationActionState.SUCCEEDED), action(InvocationActionState.SUCCEEDED),
                action(InvocationActionState.FAILED), action(InvocationActionState.SKIPPED),
                action(InvocationActionState.DISABLED), action(InvocationActionState.CANCELLED),
                action(InvocationActionState.RUNNING), action(InvocationActionState.PENDING)));

        assertEquals(new ActionCounts(2, 1, 2, 1, 2), counts);
        assertEquals(6, counts.finished());
        assertEquals(8, counts.total());
    }

    @Test
    void statementKindsComeFromTheBigQueryType() {
        assertEquals("CREATE SCHEMA", RunText.statementKind("CREATE_SCHEMA", "create schema x"));
    }

    @Test
    void withoutTypeTheKindIsTheFirstWordOfTheSql() {
        assertEquals("DECLARE", RunText.statementKind(null, "  declare x INT64"));
        assertEquals("Statement", RunText.statementKind(null, null));
        assertEquals("Statement", RunText.statementKind(null, "  "));
    }

    @Test
    void theTableNameIsTheLastSegmentOfTheTarget() {
        assertEquals("orders_daily", RunText.tableName("acme-prod.analytics.orders_daily"));
        assertNull(RunText.tableName(null));
    }

    @Test
    void barLabelsTellTheDurationOrTheFailure() {
        assertEquals("0.4 s", RunText.barLabel(job("DONE", START, START.plusMillis(400))));
        assertEquals("failed after 6.1 s", RunText.barLabel(job("FAILED", START, START.plusMillis(6_100))));
        assertEquals("running", RunText.barLabel(job("RUNNING", START, null)));
        assertEquals("", RunText.barLabel(job("DONE", null, null)));
    }

    @Test
    void theScriptSummaryCountsTheStatements() {
        BigQueryChildJob done = job("DONE", START, START.plusSeconds(1));

        assertEquals("3 statements · 21.4 s · " + formatBytes(2_600_000_000L) + " processed",
                RunText.scriptSummary(details(List.of(done, done, done), START.plusMillis(21_400), 2_600_000_000L)));
    }

    @Test
    void theScriptSummaryTellsWhereAFailedScriptStopped() {
        BigQueryChildJob done = job("DONE", START, START.plusSeconds(1));
        BigQueryChildJob failed = job("FAILED", START, START.plusSeconds(1));

        assertEquals("stopped at statement 3 · 9.8 s · " + formatBytes(11_010_048L) + " processed",
                RunText.scriptSummary(details(List.of(done, done, failed), START.plusMillis(9_800), 11_010_048L)));
    }

    @Test
    void aSingleStatementIsCountedInTheSingular() {
        BigQueryChildJob done = job("DONE", START, START.plusSeconds(1));

        assertTrue(RunText.scriptSummary(details(List.of(done), START.plusSeconds(1), null)).startsWith("1 statement ·"));
    }

    @Test
    void aJobWithoutStatementsLeavesTheCountOut() {
        assertEquals("21.4 s", RunText.scriptSummary(details(List.of(), START.plusMillis(21_400), null)));
    }

    @Test
    void timeRangesUseTheLocalClock() {
        Instant start = Instant.parse("2026-11-03T10:42:05Z");
        Instant end = Instant.parse("2026-11-03T10:42:23Z");

        assertEquals("10:42:05 → 10:42:23", RunText.timeRange(start, end, ZoneOffset.UTC));
        assertEquals("10:42:05 →", RunText.timeRange(start, null, ZoneOffset.UTC));
        assertEquals("", RunText.timeRange(null, null, ZoneOffset.UTC));
    }

    @Test
    void ticksAreLabelledInSecondsMinutesOrHours() {
        assertEquals(List.of("0 s", "20 s", "2 m", "2 h"),
                List.of(RunText.tick(0), RunText.tick(20), RunText.tick(120), RunText.tick(7200)));
    }

    private static BigQueryChildJob job(String status, Instant start, Instant end) {
        return new BigQueryChildJob("job", status, start, end, "SELECT 1", null);
    }

    private static BigQueryJobDetails details(List<BigQueryChildJob> children, Instant end, Long bytes) {
        return new BigQueryJobDetails("script", "p", "EU", "DONE", null, bytes, bytes, START, end,
                children.size(), children, null);
    }

    private static InvocationActionResult action(InvocationActionState state) {
        return new InvocationActionResult("p.ds." + state, state, null, null, null, null, null, null, null, null);
    }
}
