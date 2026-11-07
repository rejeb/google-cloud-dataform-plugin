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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The USD price list shipped with the plugin, used until a list is downloaded.
 */
public final class BuiltInPriceList {

    private static final String RESOURCE = "/pricing/bigquery-on-demand-prices.json";

    private BuiltInPriceList() {
    }

    /**
     * Reads the shipped list; its update time is the effective time of the catalog it was taken from.
     */
    @NotNull
    public static PriceList load() {
        try (InputStream in = BuiltInPriceList.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, BigDecimal> prices = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("pricePerTib").entrySet()) {
                prices.put(entry.getKey(), entry.getValue().getAsBigDecimal());
            }
            return new PriceList(root.get("currency").getAsString(),
                    Instant.parse(root.get("effectiveTime").getAsString()), prices, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
