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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInPriceListTest {

    @Test
    void theShippedListPricesEveryKnownLocationInUsd() {
        PriceList builtIn = BuiltInPriceList.load();

        assertTrue(builtIn.builtIn());
        assertEquals("USD", builtIn.currency());
        assertEquals(Instant.parse("2026-11-01T07:00:00Z"), builtIn.updatedAt());
        assertEquals(56, builtIn.locationCount());
        assertEquals(0, new BigDecimal("11.25").compareTo(builtIn.priceFor("southamerica-east1")));
        assertEquals(0, new BigDecimal("6.25").compareTo(builtIn.priceFor(BigQueryLocations.FALLBACK_KEY)));
    }
}
