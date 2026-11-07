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

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * A cost as shown to the user.
 *
 * @param value    the cost, or what replaces it when there is no price
 * @param note     where the price comes from, empty when there is none
 * @param reliable whether the price can be trusted
 * @param tooltip  HTML explaining the price on hover, empty when there is no price
 */
public record CostText(@NotNull String value, @NotNull String note, boolean reliable, @NotNull String tooltip) {

    /**
     * A cost without price to explain on hover.
     */
    public CostText(@NotNull String value, @NotNull String note, boolean reliable) {
        this(value, note, reliable, "");
    }

    private static final String CAPACITY = "capacity pricing";
    private static final String ESTIMATE_PREFIX = "≈ ";

    /**
     * The estimated cost of a dry-run's processed bytes.
     */
    @NotNull
    public static CostText estimate(long bytesProcessed, @NotNull PriceQuote quote) {
        return of(bytesProcessed, quote, false, ESTIMATE_PREFIX);
    }

    /**
     * The estimated cost of a query that processes less than BigQuery bills at least.
     */
    @NotNull
    public static CostText estimateAtMinimum(long minimumBilled, @NotNull PriceQuote quote) {
        CostText cost = estimate(minimumBilled, quote);
        return cost.tooltip().isEmpty()
                ? cost
                : new CostText(cost.value(), cost.note(), cost.reliable(), CostTooltip.estimateAtMinimum(cost.note()));
    }

    /**
     * The actual cost of a job's billed bytes.
     */
    @NotNull
    public static CostText actual(long bytesBilled, @NotNull PriceQuote quote) {
        return of(bytesBilled, quote, true, "");
    }

    @NotNull
    private static CostText of(long bytes, @NotNull PriceQuote quote, boolean billed, @NotNull String prefix) {
        BigDecimal price = quote.pricePerTib();
        if (quote.source() == PriceSource.CAPACITY || price == null) return new CostText(CAPACITY, "", true);
        String value = prefix + CostFormat.money(CostFormat.costOf(bytes, price), quote.currency());
        String note = CostFormat.priceNote(quote, billed);
        String tooltip = billed ? CostTooltip.job(note) : CostTooltip.estimate(note);
        return new CostText(value, note, quote.reliable(), tooltip);
    }
}
