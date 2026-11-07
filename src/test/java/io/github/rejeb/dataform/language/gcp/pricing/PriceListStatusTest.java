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

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceListStatusTest {

    private static final Instant UPDATED = Instant.parse("2026-11-01T07:05:00Z");

    @Test
    void aDownloadedListGivesItsSizeAndDate() {
        PriceListStatus status = new PriceListStatus(56, UPDATED, false, null);
        assertEquals("Price list: 56 locations, updated 2026-11-01 07:05", status.describe(ZoneOffset.UTC));
    }

    @Test
    void theBuiltInListSaysSo() {
        PriceListStatus status = new PriceListStatus(56, UPDATED, true, null);
        assertEquals("Price list: built-in (56 locations)", status.describe(ZoneOffset.UTC));
    }

    @Test
    void aFailedRefreshIsShownAfterTheList() {
        PriceListStatus status = new PriceListStatus(56, UPDATED, true, "Cloud Billing API answered HTTP 403");
        assertEquals("Price list: built-in (56 locations) · refresh failed: Cloud Billing API answered HTTP 403",
                status.describe(ZoneOffset.UTC));
    }
}
