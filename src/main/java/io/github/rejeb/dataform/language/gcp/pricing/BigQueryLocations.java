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

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Maps a BigQuery location to the region name Cloud Billing prices it under.
 */
public final class BigQueryLocations {

    public static final String FALLBACK_KEY = "us";

    private static final String EU_LOCATION = "eu";
    private static final String EU_PRICE_KEY = "europe";

    private BigQueryLocations() {
    }

    /**
     * Returns the price key of a BigQuery location: its lower-case name, with the {@code EU}
     * multi-region priced as {@code europe}; null when there is no location.
     */
    @Nullable
    public static String priceKey(@Nullable String location) {
        if (location == null || location.isBlank()) return null;
        String key = location.trim().toLowerCase(Locale.ROOT);
        return EU_LOCATION.equals(key) ? EU_PRICE_KEY : key;
    }
}
