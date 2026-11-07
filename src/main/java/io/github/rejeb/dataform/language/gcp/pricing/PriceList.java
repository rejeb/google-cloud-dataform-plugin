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
import java.util.Map;

/**
 * BigQuery on-demand query prices per TiB, keyed by the price key of a location.
 *
 * @param currency    ISO 4217 code of every price
 * @param updatedAt   when the list was downloaded, or the date of the built-in list
 * @param pricePerTib price per TiB by price key, see {@link BigQueryLocations#priceKey(String)}
 * @param builtIn     whether the list is the one shipped with the plugin
 */
public record PriceList(@NotNull String currency,
                        @NotNull Instant updatedAt,
                        @NotNull Map<String, BigDecimal> pricePerTib,
                        boolean builtIn) {

    public PriceList {
        pricePerTib = Map.copyOf(pricePerTib);
    }

    /**
     * Returns the price per TiB of a price key, or null when the list has none.
     */
    @Nullable
    public BigDecimal priceFor(@NotNull String priceKey) {
        return pricePerTib.get(priceKey);
    }

    /**
     * Returns the number of locations the list prices.
     */
    public int locationCount() {
        return pricePerTib.size();
    }
}
