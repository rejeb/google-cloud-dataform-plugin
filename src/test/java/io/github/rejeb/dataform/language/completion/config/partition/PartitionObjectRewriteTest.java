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
package io.github.rejeb.dataform.language.completion.config.partition;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartitionObjectRewriteTest {

    private static Optional<String> rewrite(String field, String dataType, String granularity) {
        return new PartitionObjectRewrite(field, dataType, granularity, null, null, null)
                .expression();
    }

    @Test
    void aDailyDateColumnIsWrittenOnItsOwn() {
        assertEquals(Optional.of("signup_date"), rewrite("signup_date", "date", "day"));
    }

    @Test
    void aDateColumnWithoutGranularityIsWrittenOnItsOwn() {
        assertEquals(Optional.of("signup_date"), rewrite("signup_date", "date", null));
    }

    @Test
    void aCoarserDateColumnIsTruncated() {
        assertEquals(Optional.of("DATE_TRUNC(signup_date, MONTH)"),
                rewrite("signup_date", "date", "month"));
        assertEquals(Optional.of("DATE_TRUNC(signup_date, YEAR)"),
                rewrite("signup_date", "date", "year"));
    }

    @Test
    void anHourlyDateColumnFallsBackToTheDailyPartitionBigQueryAllows() {
        assertEquals(Optional.of("signup_date"), rewrite("signup_date", "date", "hour"));
    }

    @Test
    void aTimestampColumnIsTruncatedToItsGranularity() {
        assertEquals(Optional.of("TIMESTAMP_TRUNC(order_ts, HOUR)"),
                rewrite("order_ts", "timestamp", "hour"));
        assertEquals(Optional.of("TIMESTAMP_TRUNC(order_ts, DAY)"),
                rewrite("order_ts", "timestamp", "day"));
    }

    @Test
    void aTimestampColumnWithoutGranularityTakesTheDailyOne() {
        assertEquals(Optional.of("TIMESTAMP_TRUNC(order_ts, DAY)"),
                rewrite("order_ts", "timestamp", null));
    }

    @Test
    void aDatetimeColumnIsTruncatedWithItsOwnFunction() {
        assertEquals(Optional.of("DATETIME_TRUNC(seen_at, MONTH)"),
                rewrite("seen_at", "datetime", "month"));
    }

    @Test
    void theTypeIsReadWhateverItsCase() {
        assertEquals(Optional.of("TIMESTAMP_TRUNC(order_ts, DAY)"),
                rewrite("order_ts", "TIMESTAMP", "DAY"));
    }

    @Test
    void bothSpellingsOfTheIntegerTypeAreBucketed() {
        assertEquals(Optional.of("RANGE_BUCKET(customer_id, GENERATE_ARRAY(0, 100, 10))"),
                rewrite("customer_id", "int64", null));
        assertEquals(Optional.of("RANGE_BUCKET(customer_id, GENERATE_ARRAY(0, 100, 10))"),
                rewrite("customer_id", "integer", null));
    }

    @Test
    void aRangeCarriesItsOwnBounds() {
        assertEquals(Optional.of("RANGE_BUCKET(customer_id, GENERATE_ARRAY(1, 500, 25))"),
                new PartitionObjectRewrite("customer_id", "int64", null, "1", "500", "25")
                        .expression());
    }

    @Test
    void aPartlyGivenRangeKeepsTheDefaultsForWhatIsMissing() {
        assertEquals(Optional.of("RANGE_BUCKET(customer_id, GENERATE_ARRAY(1, 100, 10))"),
                new PartitionObjectRewrite("customer_id", "int64", null, "1", null, null)
                        .expression());
    }

    @Test
    void anUnknownTypeIsNotRewritten() {
        assertEquals(Optional.empty(), rewrite("name", "string", null));
        assertEquals(Optional.empty(), rewrite("payload", "record", null));
    }

    @Test
    void aMissingTypeLeavesNothingToWrite() {
        assertEquals(Optional.empty(), rewrite("order_ts", null, "day"));
    }

    @Test
    void aMissingFieldLeavesNothingToWrite() {
        assertEquals(Optional.empty(), rewrite("", "timestamp", "day"));
    }

    @Test
    void aGranularityTheFunctionRejectsFallsBackToItsDefault() {
        assertEquals(Optional.of("TIMESTAMP_TRUNC(order_ts, DAY)"),
                rewrite("order_ts", "timestamp", "fortnight"));
    }
}
