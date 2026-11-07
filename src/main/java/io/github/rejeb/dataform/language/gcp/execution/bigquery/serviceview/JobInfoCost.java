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
import io.github.rejeb.dataform.language.gcp.pricing.CostFormat;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.CostTooltip;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * The cost row of the Job Info tab of an executed query.
 */
public final class JobInfoCost {

    private static final String CACHE_HIT = "served from cache, not billed";

    private JobInfoCost() {
    }

    /**
     * The actual cost of the job's billed bytes; a cache hit costs nothing.
     */
    @NotNull
    public static CostText of(@NotNull BigQueryJobStats stats, @NotNull PriceQuote quote) {
        if (stats.cacheHit()) {
            return new CostText(CostFormat.money(BigDecimal.ZERO, quote.currency()), CACHE_HIT, true,
                    CostTooltip.job(CostFormat.priceNote(quote, true)));
        }
        return CostText.actual(stats.bytesBilled(), quote);
    }
}
