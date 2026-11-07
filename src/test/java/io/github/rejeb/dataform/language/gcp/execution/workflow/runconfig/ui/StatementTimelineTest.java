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

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatementTimelineTest {

    private static final Instant S = Instant.parse("2026-11-03T10:42:02.400Z");

    @Test
    void ticksEveryFiveSecondsOverTwentyOneSeconds() {
        StatementTimeline timeline = StatementTimeline.of(S, S.plusMillis(21_400), List.of());

        assertEquals(List.of("0 s", "5 s", "10 s", "15 s", "20 s"), labels(timeline));
        assertEquals(5 / 21.4, timeline.ticks().get(1).at(), 1e-9);
    }

    @Test
    void ticksEveryTwoSecondsUnderTenSeconds() {
        assertEquals(List.of("0 s", "2 s", "4 s", "6 s", "8 s"),
                labels(StatementTimeline.of(S, S.plusMillis(9_800), List.of())));
    }

    @Test
    void longScriptsTickInMinutes() {
        assertEquals(List.of("0 s", "2 m", "4 m", "6 m"), labels(StatementTimeline.of(S, S.plusSeconds(360), List.of())));
    }

    @Test
    void barsAreFractionsOfTheScriptRange() {
        BigQueryChildJob child = job("DONE", S.plusMillis(2_600), S.plusMillis(20_800));

        StatementTimeline.Bar bar = StatementTimeline.of(S, S.plusMillis(21_400), List.of(child)).bar(child);

        assertEquals(2.6 / 21.4, bar.from(), 1e-9);
        assertEquals(20.8 / 21.4, bar.to(), 1e-9);
    }

    @Test
    void aRunningStatementRunsToTheEnd() {
        BigQueryChildJob child = job("RUNNING", S.plusSeconds(1), null);

        assertEquals(1.0, StatementTimeline.of(S, S.plusSeconds(10), List.of(child)).bar(child).to(), 1e-9);
    }

    @Test
    void aStatementThatNeverStartedHasNoBar() {
        BigQueryChildJob child = job("PENDING", null, null);

        assertNull(StatementTimeline.of(S, S.plusSeconds(10), List.of(child)).bar(child));
    }

    @Test
    void withoutScriptTimesTheStatementsSetTheRange() {
        StatementTimeline timeline = StatementTimeline.of(null, null, List.of(
                job("DONE", S.plusSeconds(1), S.plusSeconds(3)), job("DONE", S.plusSeconds(3), S.plusSeconds(11))));

        assertEquals(S.plusSeconds(1), timeline.origin());
        assertEquals(Duration.ofSeconds(10), timeline.span());
    }

    @Test
    void anEmptyRangeStaysFinite() {
        BigQueryChildJob child = job("DONE", S, S);

        StatementTimeline timeline = StatementTimeline.of(S, S, List.of(child));

        assertEquals(Duration.ofMillis(1), timeline.span());
        assertEquals(List.of("0 s"), labels(timeline));
        assertFalse(Double.isNaN(timeline.bar(child).to()));
    }

    @Test
    void barsStayInsideTheRange() {
        BigQueryChildJob child = job("DONE", S.minusSeconds(5), S.plusSeconds(50));

        StatementTimeline.Bar bar = StatementTimeline.of(S, S.plusSeconds(10), List.of(child)).bar(child);

        assertEquals(0.0, bar.from(), 1e-9);
        assertEquals(1.0, bar.to(), 1e-9);
    }

    private static List<String> labels(StatementTimeline timeline) {
        return timeline.ticks().stream().map(StatementTimeline.Tick::label).toList();
    }

    private static BigQueryChildJob job(String status, Instant start, Instant end) {
        return new BigQueryChildJob("job", status, start, end, "SELECT 1", null);
    }
}
