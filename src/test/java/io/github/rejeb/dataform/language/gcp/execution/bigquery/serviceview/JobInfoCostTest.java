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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview;

import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobStats;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import io.github.rejeb.dataform.language.gcp.pricing.PriceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobInfoCostTest {

    private static final long GIB = 1L << 30;
    private static final PriceQuote LIVE = new PriceQuote("EU", PriceSource.LIVE, new BigDecimal("6.25"), "USD",
            Instant.parse("2026-11-01T07:00:00Z"), false);

    @Test
    void aCacheHitIsNotBilled() {
        BigQueryJobStats hit = new BigQueryJobStats("j", "p", "EU", 0, 0, 0, 0, 0, true, "SELECT", 0, 0);

        CostText cost = JobInfoCost.of(hit, LIVE);

        assertEquals("$0.00", cost.value());
        assertEquals("served from cache, not billed", cost.note());
        assertTrue(cost.tooltip().contains("EU · $6.25 per TiB billed · list price"), cost.tooltip());
    }

    @Test
    void aRunCostsItsBilledBytes() {
        long billed = Math.round(2.46 * GIB);
        BigQueryJobStats run = new BigQueryJobStats("j", "p", "EU", 0, 0, 0, billed, billed, false, "SELECT", 18422, 0);

        CostText cost = JobInfoCost.of(run, LIVE);

        assertEquals("$0.0150", cost.value());
        assertEquals("EU · $6.25 per TiB billed · list price", cost.note());
    }

    @Test
    void theBilledBytesAreCostedNotTheProcessedOnes() {
        BigQueryJobStats run = new BigQueryJobStats("j", "p", "EU", 0, 0, 0, 1000 * GIB, 0, false, "SELECT", 1, 0);

        assertEquals("$0.00", JobInfoCost.of(run, LIVE).value());
    }
}
