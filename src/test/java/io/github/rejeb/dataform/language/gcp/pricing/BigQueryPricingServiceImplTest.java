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

import io.github.rejeb.dataform.language.settings.BigQueryPriceMode;
import io.github.rejeb.dataform.language.settings.DataformToolsSettingsImpl;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigQueryPricingServiceImplTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final DataformToolsSettingsImpl settings = new DataformToolsSettingsImpl();
    private final FakeFetcher fetcher = new FakeFetcher(clock);
    private ExecutorService pool;

    @AfterEach
    void stopPool() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    void withoutDownloadTheBuiltInListIsQuoted() {
        BigQueryPricingServiceImpl service = service(false);

        PriceQuote quote = service.quote("EU");

        assertEquals(PriceSource.BUILT_IN, quote.source());
        assertEquals("EU", quote.locationLabel());
        assertEquals(0, new BigDecimal("6.25").compareTo(quote.pricePerTib()));
        assertEquals("USD", quote.currency());
        assertFalse(quote.assumed());
    }

    @Test
    void afterADownloadTheQuoteIsLive() {
        BigQueryPricingServiceImpl service = service(true);

        service.refresh().join();
        PriceQuote quote = service.quote("europe-west1");

        assertEquals(PriceSource.LIVE, quote.source());
        assertEquals(0, new BigDecimal("7.50").compareTo(quote.pricePerTib()));
        assertEquals(T0, quote.listDate());
        assertTrue(quote.reliable());
    }

    @Test
    void aListOlderThanADayIsQuotedAsAnOldListWhileRefreshFails() {
        BigQueryPricingServiceImpl service = service(true);
        service.refresh().join();
        clock.advance(Duration.ofHours(25));
        fetcher.failing = true;

        PriceQuote quote = service.quote("europe-west1");

        assertEquals(PriceSource.OLD_LIST, quote.source());
        assertEquals(T0, quote.listDate());
        assertEquals(0, new BigDecimal("7.50").compareTo(quote.pricePerTib()));
        assertEquals(2, fetcher.calls.get());
        assertNotNull(service.status().lastError());
        assertEquals(3, service.status().locationCount());
    }

    @Test
    void aMissingLocationIsPricedWithTheUsPriceAndFlagged() {
        BigQueryPricingServiceImpl service = service(true);
        service.refresh().join();

        PriceQuote quote = service.quote("europe-west99");

        assertEquals("europe-west99 (US price assumed)", quote.locationLabel());
        assertTrue(quote.assumed());
        assertEquals(0, new BigDecimal("6.25").compareTo(quote.pricePerTib()));
    }

    @Test
    void anUnknownLocationIsQuotedAsAssumedUs() {
        BigQueryPricingServiceImpl service = service(false);

        PriceQuote quote = service.quote(null);

        assertEquals("US (assumed)", quote.locationLabel());
        assertTrue(quote.assumed());
        assertEquals(0, new BigDecimal("6.25").compareTo(quote.pricePerTib()));
    }

    @Test
    void fixedAndCapacityModesIgnoreTheList() {
        BigQueryPricingServiceImpl service = service(true);

        settings.updateCostSettings(true, BigQueryPriceMode.FIXED_PRICE, "5.00", "EUR");
        PriceQuote fixed = service.quote("EU");
        settings.updateCostSettings(true, BigQueryPriceMode.CAPACITY, "5.00", "EUR");
        PriceQuote capacity = service.quote("EU");

        assertEquals(PriceSource.FIXED, fixed.source());
        assertEquals(0, new BigDecimal("5").compareTo(fixed.pricePerTib()));
        assertEquals("EUR", fixed.currency());
        assertEquals(PriceSource.CAPACITY, capacity.source());
        assertNull(capacity.pricePerTib());
        assertEquals(0, fetcher.calls.get());
    }

    @Test
    void manyStaleQuotesDownloadOnce() throws InterruptedException {
        pool = Executors.newFixedThreadPool(4);
        fetcher.gate = new CountDownLatch(1);
        BigQueryPricingServiceImpl service = new BigQueryPricingServiceImpl(
                fetcher, clock, () -> settings, () -> true, pool);

        for (int i = 0; i < 10; i++) {
            service.quote("EU");
        }
        fetcher.gate.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(1, fetcher.calls.get());
        assertEquals(PriceSource.LIVE, service.quote("EU").source());
    }

    @Test
    void signedOutNeverDownloads() {
        BigQueryPricingServiceImpl service = service(false);

        service.quote("EU");
        service.refresh().join();

        assertEquals(0, fetcher.calls.get());
        assertEquals(PriceSource.BUILT_IN, service.quote("EU").source());
    }

    @Test
    void theDownloadedListSurvivesARestart() {
        BigQueryPricingServiceImpl first = service(true);
        first.refresh().join();
        PriceListState state = first.getState();

        FakeFetcher otherFetcher = new FakeFetcher(clock);
        BigQueryPricingServiceImpl second = new BigQueryPricingServiceImpl(
                otherFetcher, clock, () -> settings, () -> true, Runnable::run);
        second.loadState(state);
        PriceQuote quote = second.quote("europe-west1");

        assertEquals(PriceSource.LIVE, quote.source());
        assertEquals(0, new BigDecimal("7.50").compareTo(quote.pricePerTib()));
        assertEquals(T0, quote.listDate());
        assertEquals(0, otherFetcher.calls.get());
    }

    @Test
    void theStateSurvivesXmlSerialization() {
        BigQueryPricingServiceImpl first = service(true);
        first.refresh().join();
        Element element = XmlSerializer.serialize(first.getState());

        BigQueryPricingServiceImpl second = service(false);
        second.loadState(XmlSerializer.deserialize(element, PriceListState.class));

        assertEquals(PriceSource.LIVE, second.quote("europe-west1").source());
        assertEquals(3, second.status().locationCount());
    }

    @Test
    void aFailedRefreshIsNotRetriedOnEveryQuote() {
        fetcher.failing = true;
        BigQueryPricingServiceImpl service = service(true);

        service.quote("EU");
        service.quote("EU");
        assertEquals(1, fetcher.calls.get());

        clock.advance(Duration.ofMinutes(61));
        service.quote("EU");
        assertEquals(2, fetcher.calls.get());
    }

    @Test
    void anExplicitRefreshRunsEvenRightAfterAFailure() {
        fetcher.failing = true;
        BigQueryPricingServiceImpl service = service(true);
        service.quote("EU");
        fetcher.failing = false;

        service.refresh().join();

        assertEquals(2, fetcher.calls.get());
        assertNull(service.status().lastError());
        assertEquals(PriceSource.LIVE, service.quote("EU").source());
    }

    @Test
    void anotherCurrencyDownloadsTheListAgain() {
        BigQueryPricingServiceImpl service = service(true);
        service.refresh().join();
        settings.updateCostSettings(true, BigQueryPriceMode.LIST_PRICE, "6.25", "EUR");

        PriceQuote before = service.quote("EU");
        PriceQuote after = service.quote("EU");

        assertEquals(PriceSource.OLD_LIST, before.source());
        assertEquals("USD", before.currency());
        assertEquals("EUR", fetcher.lastCurrency);
        assertEquals(PriceSource.LIVE, after.source());
        assertEquals("EUR", after.currency());
    }

    @Test
    void aListWithoutAUsPriceIsRejected() {
        fetcher.prices = Map.of("europe-west1", new BigDecimal("7.50"));
        BigQueryPricingServiceImpl service = service(true);

        PriceListStatus status = service.refresh().join();

        assertTrue(status.builtIn());
        assertNotNull(status.lastError());
    }

    private BigQueryPricingServiceImpl service(boolean signedIn) {
        return new BigQueryPricingServiceImpl(fetcher, clock, () -> settings, () -> signedIn, Runnable::run);
    }

    private static final class FakeFetcher implements PriceListFetcher {

        private final Clock clock;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile boolean failing;
        private volatile CountDownLatch gate;
        private volatile String lastCurrency;
        private volatile Map<String, BigDecimal> prices = Map.of(
                "us", new BigDecimal("6.25"),
                "europe", new BigDecimal("6.25"),
                "europe-west1", new BigDecimal("7.50"));

        private FakeFetcher(Clock clock) {
            this.clock = clock;
        }

        @Override
        public PriceList fetch(String currency) throws Exception {
            calls.incrementAndGet();
            lastCurrency = currency;
            CountDownLatch latch = gate;
            if (latch != null && !latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gate never opened");
            }
            if (failing) {
                throw new IllegalStateException("403 SERVICE_DISABLED");
            }
            return new PriceList(currency, clock.instant(), prices, false);
        }
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
