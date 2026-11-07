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
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The price a cost is computed with, and where it comes from.
 *
 * @param locationLabel the location as shown to the user, flagged when it had to be assumed
 * @param source        where the price comes from
 * @param pricePerTib   the price per TiB, null for capacity pricing
 * @param currency      ISO 4217 code of the price
 * @param listDate      when the price list was updated, null when unknown or irrelevant
 * @param assumed       whether the location, or its price, had to be assumed
 */
public record PriceQuote(@NotNull String locationLabel,
                         @NotNull PriceSource source,
                         @Nullable BigDecimal pricePerTib,
                         @NotNull String currency,
                         @Nullable Instant listDate,
                         boolean assumed) {

    /**
     * Tells whether the price can be trusted as today's list price of the right location.
     */
    public boolean reliable() {
        return !assumed && source != PriceSource.OLD_LIST && source != PriceSource.BUILT_IN;
    }
}
