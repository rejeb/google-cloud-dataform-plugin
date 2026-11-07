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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the BigQuery on-demand query prices out of a Cloud Billing Catalog SKU list page.
 */
public final class CatalogSkuParser {

    private static final String ON_DEMAND_ANALYSIS = "OnDemandAnalysis";
    private static final String PER_TIB = "TiBy";
    private static final int NANOS_SCALE = 9;

    private CatalogSkuParser() {
    }

    /**
     * Keeps the SKUs of the OnDemandAnalysis group priced per TiB and maps each of their service
     * regions to the price of their paid tier, the tier with the highest start usage amount.
     */
    @NotNull
    public static CatalogPage parse(@NotNull String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        String currency = null;
        for (JsonElement element : array(root, "skus")) {
            JsonObject unitPrice = paidUnitPrice(element.getAsJsonObject());
            if (unitPrice == null) {
                continue;
            }
            BigDecimal price = amountOf(unitPrice);
            if (currency == null) {
                currency = string(unitPrice, "currencyCode");
            }
            for (JsonElement region : array(element.getAsJsonObject(), "serviceRegions")) {
                prices.putIfAbsent(region.getAsString().toLowerCase(Locale.ROOT), price);
            }
        }
        return new CatalogPage(currency == null ? "" : currency, prices, string(root, "nextPageToken"));
    }

    @Nullable
    private static JsonObject paidUnitPrice(@NotNull JsonObject sku) {
        JsonObject category = object(sku, "category");
        if (category == null || !ON_DEMAND_ANALYSIS.equals(string(category, "resourceGroup"))) {
            return null;
        }
        JsonArray pricingInfo = array(sku, "pricingInfo");
        if (pricingInfo.isEmpty() || !pricingInfo.get(0).isJsonObject()) {
            return null;
        }
        JsonObject expression = object(pricingInfo.get(0).getAsJsonObject(), "pricingExpression");
        if (expression == null || !PER_TIB.equals(string(expression, "usageUnit"))) {
            return null;
        }
        JsonObject paidTier = null;
        double highestStart = Double.NEGATIVE_INFINITY;
        for (JsonElement tier : array(expression, "tieredRates")) {
            JsonObject rate = tier.getAsJsonObject();
            double start = rate.has("startUsageAmount") ? rate.get("startUsageAmount").getAsDouble() : 0;
            if (start > highestStart) {
                highestStart = start;
                paidTier = rate;
            }
        }
        return paidTier == null ? null : object(paidTier, "unitPrice");
    }

    @NotNull
    private static BigDecimal amountOf(@NotNull JsonObject unitPrice) {
        String units = string(unitPrice, "units");
        BigDecimal whole = units == null || units.isBlank() ? BigDecimal.ZERO : new BigDecimal(units);
        long nanos = unitPrice.has("nanos") ? unitPrice.get("nanos").getAsLong() : 0;
        return whole.add(BigDecimal.valueOf(nanos, NANOS_SCALE)).stripTrailingZeros();
    }

    @NotNull
    private static JsonArray array(@NotNull JsonObject owner, @NotNull String name) {
        JsonElement value = owner.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    @Nullable
    private static JsonObject object(@NotNull JsonObject owner, @NotNull String name) {
        JsonElement value = owner.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    @Nullable
    private static String string(@NotNull JsonObject owner, @NotNull String name) {
        JsonElement value = owner.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
