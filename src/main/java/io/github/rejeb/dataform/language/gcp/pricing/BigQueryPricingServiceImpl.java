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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.settings.BigQueryPriceMode;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps the downloaded price list across restarts and quotes from it, from the built-in list until a
 * download succeeds, or from the settings in fixed price and capacity modes.
 */
@State(
        name = "DataformBigQueryPrices",
        storages = @Storage(value = "dataformBigQueryPrices.xml", roamingType = RoamingType.DISABLED)
)
public final class BigQueryPricingServiceImpl
        implements BigQueryPricingService, PersistentStateComponent<PriceListState> {

    private static final Logger LOG = Logger.getInstance(BigQueryPricingServiceImpl.class);
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final Duration RETRY_PAUSE = Duration.ofHours(1);
    private static final String NO_LOCATION_LABEL = "US (assumed)";
    private static final String US_PRICE_ASSUMED = " (US price assumed)";
    private static final String SIGNED_OUT = "sign in to Google Cloud to download it";
    private static final String NO_US_PRICE = "the downloaded list has no US price";

    private final PriceListFetcher fetcher;
    private final Clock clock;
    private final Supplier<DataformToolsSettings> settings;
    private final BooleanSupplier signedIn;
    private final Executor executor;
    private final AtomicReference<CompletableFuture<PriceListStatus>> inFlight = new AtomicReference<>();
    private volatile @Nullable PriceList downloaded;
    private volatile @Nullable PriceList builtIn;
    private volatile @Nullable String lastError;
    private volatile @Nullable Instant lastFailureAt;

    /**
     * Creates the application service.
     */
    public BigQueryPricingServiceImpl() {
        this(new CloudBillingCatalogClient(),
                Clock.systemUTC(),
                DataformToolsSettings::getInstance,
                () -> DataformCredentialsService.getInstance().isSignedIn(),
                AppExecutorUtil.createBoundedApplicationPoolExecutor("Dataform BigQuery Price List", 1));
    }

    BigQueryPricingServiceImpl(@NotNull PriceListFetcher fetcher,
                               @NotNull Clock clock,
                               @NotNull Supplier<DataformToolsSettings> settings,
                               @NotNull BooleanSupplier signedIn,
                               @NotNull Executor executor) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.settings = settings;
        this.signedIn = signedIn;
        this.executor = executor;
    }

    @Override
    public @NotNull PriceQuote quote(@Nullable String location) {
        DataformToolsSettings current = settings.get();
        BigQueryPriceMode mode = current.getPriceMode();
        String label = location == null ? "" : location.trim();
        if (mode == BigQueryPriceMode.CAPACITY) {
            return new PriceQuote(label, PriceSource.CAPACITY, null, current.getCurrency(), null, false);
        }
        if (mode == BigQueryPriceMode.FIXED_PRICE) {
            BigDecimal fixed = positiveNumber(current.getFixedPricePerTib());
            if (fixed != null) {
                return new PriceQuote(label, PriceSource.FIXED, fixed, current.getCurrency(), null, false);
            }
        }
        PriceList list = downloaded;
        PriceSource source;
        if (list == null) {
            list = builtInList();
            source = PriceSource.BUILT_IN;
        } else {
            source = isFresh(list, current.getCurrency()) ? PriceSource.LIVE : PriceSource.OLD_LIST;
        }
        if (source != PriceSource.LIVE) {
            refreshInBackground();
        }
        return quoteFrom(list, source, location);
    }

    @Override
    public @NotNull CompletableFuture<PriceListStatus> refresh() {
        while (true) {
            CompletableFuture<PriceListStatus> current = inFlight.get();
            if (current != null && !current.isDone()) {
                return current;
            }
            CompletableFuture<PriceListStatus> next = new CompletableFuture<>();
            if (inFlight.compareAndSet(current, next)) {
                try {
                    executor.execute(() -> download(next));
                } catch (RejectedExecutionException e) {
                    next.complete(status());
                }
                return next;
            }
        }
    }

    @Override
    public @NotNull PriceListStatus status() {
        PriceList list = downloaded;
        if (list == null) {
            PriceList shipped = builtInList();
            return new PriceListStatus(shipped.locationCount(), shipped.updatedAt(), true, lastError);
        }
        return new PriceListStatus(list.locationCount(), list.updatedAt(), false, lastError);
    }

    @Override
    public @NotNull PriceListState getState() {
        PriceListState state = new PriceListState();
        PriceList list = downloaded;
        if (list != null) {
            state.currency = list.currency();
            state.updatedAtMillis = list.updatedAt().toEpochMilli();
            list.pricePerTib().forEach((key, price) -> state.pricePerTib.put(key, price.toPlainString()));
        }
        return state;
    }

    @Override
    public void loadState(@NotNull PriceListState state) {
        Map<String, BigDecimal> prices = new HashMap<>();
        if (state.pricePerTib != null) {
            state.pricePerTib.forEach((key, text) -> {
                BigDecimal price = positiveNumber(text);
                if (key != null && price != null) {
                    prices.put(key, price);
                }
            });
        }
        if (state.currency == null || state.currency.isBlank() || !prices.containsKey(BigQueryLocations.FALLBACK_KEY)) {
            downloaded = null;
            return;
        }
        downloaded = new PriceList(state.currency, Instant.ofEpochMilli(state.updatedAtMillis), prices, false);
    }

    private void refreshInBackground() {
        Instant failedAt = lastFailureAt;
        if (failedAt != null && Duration.between(failedAt, clock.instant()).compareTo(RETRY_PAUSE) < 0) {
            return;
        }
        refresh();
    }

    private void download(@NotNull CompletableFuture<PriceListStatus> result) {
        try {
            if (!signedIn.getAsBoolean()) {
                lastError = SIGNED_OUT;
                return;
            }
            PriceList list = fetcher.fetch(settings.get().getCurrency());
            if (list.priceFor(BigQueryLocations.FALLBACK_KEY) == null) {
                throw new IllegalStateException(NO_US_PRICE);
            }
            downloaded = list;
            lastError = null;
            lastFailureAt = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed(e);
        } catch (Exception e) {
            failed(e);
        } finally {
            result.complete(status());
        }
    }

    private void failed(@NotNull Exception e) {
        LOG.info("BigQuery price list refresh failed", e);
        lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        lastFailureAt = clock.instant();
    }

    private boolean isFresh(@NotNull PriceList list, @NotNull String currency) {
        return list.currency().equals(currency)
                && Duration.between(list.updatedAt(), clock.instant()).compareTo(MAX_AGE) < 0;
    }

    @NotNull
    private static PriceQuote quoteFrom(@NotNull PriceList list, @NotNull PriceSource source, @Nullable String location) {
        String key = BigQueryLocations.priceKey(location);
        BigDecimal usPrice = list.priceFor(BigQueryLocations.FALLBACK_KEY);
        if (key == null || location == null) {
            return new PriceQuote(NO_LOCATION_LABEL, source, usPrice, list.currency(), list.updatedAt(), true);
        }
        BigDecimal price = list.priceFor(key);
        if (price == null) {
            return new PriceQuote(location.trim() + US_PRICE_ASSUMED, source, usPrice, list.currency(),
                    list.updatedAt(), true);
        }
        return new PriceQuote(location.trim(), source, price, list.currency(), list.updatedAt(), false);
    }

    @NotNull
    private PriceList builtInList() {
        PriceList list = builtIn;
        if (list == null) {
            list = BuiltInPriceList.load();
            builtIn = list;
        }
        return list;
    }

    @Nullable
    private static BigDecimal positiveNumber(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
