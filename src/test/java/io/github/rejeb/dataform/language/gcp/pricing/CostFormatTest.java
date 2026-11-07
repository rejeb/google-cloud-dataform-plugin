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

class CostFormatTest {

    private static final long GIB = 1L << 30;
    private static final BigDecimal USD_625 = new BigDecimal("6.25");
    private static final PriceQuote LIVE = new PriceQuote("EU", PriceSource.LIVE, USD_625, "USD",
            Instant.parse("2026-11-01T07:00:00Z"), false);

    @Test
    void anAmountBelowOneHasFourDecimals() {
        assertEquals("$0.0294", CostFormat.money(CostFormat.costOf(Math.round(4.82 * GIB), USD_625), "USD"));
    }

    @Test
    void anAmountFromOneHasTwoDecimals() {
        assertEquals("$14.44", CostFormat.money(CostFormat.costOf(Math.round(2365.44 * GIB), USD_625), "USD"));
    }

    @Test
    void aTinyAmountIsShownAsBelowTheSmallestUnit() {
        assertEquals("< $0.0001", CostFormat.money(CostFormat.costOf(12L << 20, USD_625), "USD"));
    }

    @Test
    void zeroBytesCostNothing() {
        assertEquals("$0.00", CostFormat.money(CostFormat.costOf(0, USD_625), "USD"));
    }

    @Test
    void theCurrencySymbolFollowsTheCurrency() {
        assertEquals("€0.0264", CostFormat.money(
                CostFormat.costOf(Math.round(4.82 * GIB), new BigDecimal("5.613125")), "EUR"));
    }

    @Test
    void aLivePriceNamesItsLocation() {
        assertEquals("EU · $6.25 per TiB · list price", CostFormat.priceNote(LIVE, false));
    }

    @Test
    void aBilledNoteSaysSo() {
        assertEquals("EU · $6.25 per TiB billed · list price", CostFormat.priceNote(LIVE, true));
    }

    @Test
    void anOldListGivesItsDate() {
        PriceQuote old = new PriceQuote("EU", PriceSource.OLD_LIST, USD_625, "USD",
                Instant.parse("2026-10-30T07:00:00Z"), false);
        assertEquals("EU · $6.25 per TiB · list price of 2026-10-30", CostFormat.priceNote(old, false));
    }

    @Test
    void theBuiltInListMayBeOutdated() {
        PriceQuote builtIn = new PriceQuote("EU", PriceSource.BUILT_IN, USD_625, "USD", null, false);
        assertEquals("EU · $6.25 per TiB · built-in list, may be outdated", CostFormat.priceNote(builtIn, false));
    }

    @Test
    void aFixedPriceHasNoLocation() {
        PriceQuote fixed = new PriceQuote("EU", PriceSource.FIXED, new BigDecimal("5"), "USD", null, false);
        assertEquals("Fixed price · $5.00 per TiB", CostFormat.priceNote(fixed, false));
    }

    @Test
    void anAssumedLocationShowsItsLabel() {
        PriceQuote assumed = new PriceQuote("US (assumed)", PriceSource.LIVE, USD_625, "USD", null, true);
        assertEquals("US (assumed) · $6.25 per TiB · list price", CostFormat.priceNote(assumed, false));
    }
}
