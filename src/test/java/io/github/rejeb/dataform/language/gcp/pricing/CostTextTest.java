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
package io.github.rejeb.dataform.language.gcp.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostTextTest {

    private static final long GIB = 1L << 30;
    private static final BigDecimal USD_625 = new BigDecimal("6.25");
    private static final PriceQuote LIVE = new PriceQuote("EU", PriceSource.LIVE, USD_625, "USD",
            Instant.parse("2026-11-01T07:00:00Z"), false);

    @Test
    void anEstimateIsMarkedAsApproximate() {
        CostText text = CostText.estimate(Math.round(4.82 * GIB), LIVE);
        assertEquals("≈ $0.0294", text.value());
        assertEquals("EU · $6.25 per TiB · list price", text.note());
        assertTrue(text.reliable());
    }

    @Test
    void theBuiltInListIsNotReliable() {
        PriceQuote builtIn = new PriceQuote("EU", PriceSource.BUILT_IN, USD_625, "USD", null, false);
        assertFalse(CostText.estimate(1, builtIn).reliable());
    }

    @Test
    void anAssumedLocationIsNotReliable() {
        PriceQuote assumed = new PriceQuote("US (assumed)", PriceSource.LIVE, USD_625, "USD", null, true);
        assertFalse(CostText.estimate(1, assumed).reliable());
    }

    @Test
    void capacityPricingHasNoPrice() {
        PriceQuote capacity = new PriceQuote("EU", PriceSource.CAPACITY, null, "USD", null, false);
        assertEquals("capacity pricing", CostText.estimate(1, capacity).value());
        assertEquals("", CostText.estimate(1, capacity).note());
    }

    @Test
    void anActualCostIsExactAndBilled() {
        CostText text = CostText.actual(Math.round(4.83 * GIB), LIVE);
        assertEquals("$0.0295", text.value());
        assertEquals("EU · $6.25 per TiB billed · list price", text.note());
    }

    @Test
    void anEstimateExplainsItsPriceOnHover() {
        assertEquals("<html>Dry-run estimate of the query shown.<br>EU · $6.25 per TiB · list price<br>Excludes the monthly free TiB and contract discounts.</html>",
                CostText.estimate(1, LIVE).tooltip());
    }

    @Test
    void anActualCostExplainsItsPriceOnHover() {
        assertEquals("<html>Cost of the bytes billed by this job.<br>EU · $6.25 per TiB billed · list price<br>Excludes the monthly free TiB and contract discounts.</html>",
                CostText.actual(1, LIVE).tooltip());
    }

    @Test
    void capacityPricingHasNoTooltip() {
        PriceQuote capacity = new PriceQuote("EU", PriceSource.CAPACITY, null, "USD", null, false);
        assertEquals("", CostText.estimate(1, capacity).tooltip());
    }

    @Test
    void nothingBilledCostsNothing() {
        assertEquals("$0.00", CostText.actual(0, LIVE).value());
    }
}
