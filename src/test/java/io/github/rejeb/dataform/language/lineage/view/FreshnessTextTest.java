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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.lineage.dataplex.Freshness;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreshnessTextTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Clock CLOCK = Clock.fixed(at(11, 14, 58), ZONE);
    private static final String LAG = "\nKnowledge Catalog can lag by up to 24 hours";

    private static Instant at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    @Test
    void aTableWrittenTodayReadsLastWriteWithTheHoursSince() {
        assertEquals("Last write: Today 06:15 · 9 hours ago\nWritten by: Scheduled query" + LAG,
                FreshnessText.tooltip(new Freshness(at(11, 6, 15), "Scheduled query", false), TimeRange.LAST_30_DAYS, CLOCK));
    }

    @Test
    void aViewReadsCreatedOrReplacedAndSaysItIsNotTheAgeOfItsData() {
        assertEquals("Created or replaced: 9 Nov 06:16 · 2 days ago\nWritten by: BigQuery job\n"
                        + "A view reads its sources live: this is not the age of its data" + LAG,
                FreshnessText.tooltip(new Freshness(at(9, 6, 16), "BigQuery job", true), TimeRange.LAST_30_DAYS, CLOCK));
    }

    @Test
    void noRecordedWriteNamesTheRange() {
        assertEquals("No write recorded in the last 7 days" + LAG,
                FreshnessText.tooltip(new Freshness(null, null, false), TimeRange.LAST_7_DAYS, CLOCK));
    }

    @Test
    void aWriteLessThanAnHourAgoReadsInMinutes() {
        assertEquals("Last write: Today 14:46 · 12 minutes ago\nWritten by: BigQuery job" + LAG,
                FreshnessText.tooltip(new Freshness(at(11, 14, 46), "BigQuery job", false), TimeRange.LAST_30_DAYS, CLOCK));
        assertEquals("Last write: Today 14:57 · 1 minute ago\nWritten by: BigQuery job" + LAG,
                FreshnessText.tooltip(new Freshness(at(11, 14, 57), "BigQuery job", false), TimeRange.LAST_30_DAYS, CLOCK));
    }

    @Test
    void aWriteOfTheDayBeforeReadsYesterday() {
        assertEquals("Last write: Yesterday 20:00 · 19 hours ago\nWritten by: BigQuery job" + LAG,
                FreshnessText.tooltip(new Freshness(at(10, 20, 0), "BigQuery job", false), TimeRange.LAST_30_DAYS, CLOCK));
        assertEquals("Last write: Yesterday 06:15 · 1 day ago\nWritten by: BigQuery job" + LAG,
                FreshnessText.tooltip(new Freshness(at(10, 6, 15), "BigQuery job", false), TimeRange.LAST_30_DAYS, CLOCK));
    }

    @Test
    void anUnknownProcessLeavesTheWrittenByLineOut() {
        assertEquals("Last write: Today 06:15 · 9 hours ago" + LAG,
                FreshnessText.tooltip(new Freshness(at(11, 6, 15), null, false), TimeRange.LAST_30_DAYS, CLOCK));
    }
}
