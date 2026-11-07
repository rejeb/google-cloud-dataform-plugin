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

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Prices BigQuery bytes per query location, from the Cloud Billing Catalog or the settings.
 */
public interface BigQueryPricingService {

    /**
     * Returns the application-level instance.
     */
    static BigQueryPricingService getInstance() {
        return ApplicationManager.getApplication().getService(BigQueryPricingService.class);
    }

    /**
     * Returns the price of a location at once, without any I/O; a list that is missing, older than a
     * day or in another currency is refreshed in the background.
     *
     * @param location the BigQuery location of the query, null when unknown
     */
    @NotNull
    PriceQuote quote(@Nullable String location);

    /**
     * Downloads the price list in the background; concurrent calls share one download.
     *
     * @return the status once the download is over, whether it succeeded or not
     */
    @NotNull
    CompletableFuture<PriceListStatus> refresh();

    /**
     * Returns which price list is in use and why the last refresh failed, if it did.
     */
    @NotNull
    PriceListStatus status();
}
