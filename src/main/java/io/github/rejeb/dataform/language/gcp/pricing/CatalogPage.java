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
import java.util.Map;

/**
 * The on-demand query prices found in one page of the Cloud Billing Catalog SKU list.
 *
 * @param currency      ISO 4217 code of the prices, empty when the page has none
 * @param pricePerTib   price per TiB by lower-case service region
 * @param nextPageToken token of the next page, null or empty on the last page
 */
public record CatalogPage(@NotNull String currency,
                          @NotNull Map<String, BigDecimal> pricePerTib,
                          @Nullable String nextPageToken) {

    public CatalogPage {
        pricePerTib = Map.copyOf(pricePerTib);
    }
}
