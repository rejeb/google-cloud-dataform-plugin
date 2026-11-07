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

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogSkuParserTest {

    @Test
    void onlyOnDemandAnalysisPricedPerTibIsKeptAtItsPaidTier() throws IOException {
        CatalogPage page = CatalogSkuParser.parse(sample());

        assertEquals("USD", page.currency());
        assertEquals(4, page.pricePerTib().size());
        assertEquals(0, new BigDecimal("6.25").compareTo(page.pricePerTib().get("us")));
        assertEquals(0, new BigDecimal("6.25").compareTo(page.pricePerTib().get("europe")));
        assertEquals(0, new BigDecimal("7.5").compareTo(page.pricePerTib().get("europe-west1")));
        assertEquals(0, new BigDecimal("7.8125").compareTo(page.pricePerTib().get("aws-us-east-1")));
        assertNull(page.pricePerTib().get("global"));
        assertNull(page.pricePerTib().get("us-central1"));
        assertEquals("", page.nextPageToken());
    }

    @Test
    void aPageWithoutSkusIsEmpty() {
        CatalogPage page = CatalogSkuParser.parse("{\"nextPageToken\":\"abc\"}");

        assertEquals(0, page.pricePerTib().size());
        assertEquals("abc", page.nextPageToken());
    }

    private static String sample() throws IOException {
        try (InputStream in = CatalogSkuParserTest.class.getResourceAsStream("/pricing/bigquery-skus-sample.json")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
