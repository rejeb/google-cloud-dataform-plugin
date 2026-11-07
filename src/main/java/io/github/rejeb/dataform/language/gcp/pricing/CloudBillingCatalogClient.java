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

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.auth.SslConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Downloads the BigQuery SKUs of the Cloud Billing Catalog with the plugin's own credential.
 *
 * <p>No quota project is set: the catalog is public data and the call is accounted to the project of
 * the plugin's OAuth client, where the Cloud Billing API is enabled.
 */
public final class CloudBillingCatalogClient implements PriceListFetcher {

    private static final Logger LOG = Logger.getInstance(CloudBillingCatalogClient.class);
    private static final String SKUS_URL = "https://cloudbilling.googleapis.com/v1/services/24E6-581D-38E5/skus";
    private static final int PAGE_SIZE = 5000;
    private static final int MAX_PAGES = 20;
    private static final int MAX_LOGGED_BODY = 4000;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final int HTTP_OK = 200;

    @Override
    public @NotNull PriceList fetch(@NotNull String currency) throws Exception {
        String token = accessToken();
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        String listCurrency = currency;
        String pageToken = null;
        int pages = 0;
        do {
            HttpResponse<String> response = SslConfig.getWithBearer(url(currency, pageToken), token, HTTP_TIMEOUT);
            if (response.statusCode() != HTTP_OK) {
                throw refused(response);
            }
            CatalogPage page = CatalogSkuParser.parse(response.body());
            page.pricePerTib().forEach(prices::putIfAbsent);
            if (!page.currency().isEmpty()) {
                listCurrency = page.currency();
            }
            pageToken = page.nextPageToken();
            pages++;
        } while (pageToken != null && !pageToken.isBlank() && pages < MAX_PAGES);
        return new PriceList(listCurrency, Instant.now(), prices, false);
    }

    @NotNull
    private static String accessToken() throws IOException {
        GoogleCredentials credentials = DataformCredentialsService.getInstance().get();
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();
        if (token == null) {
            throw new IOException("No access token for the Cloud Billing API");
        }
        return token.getTokenValue();
    }

    @NotNull
    private static String url(@NotNull String currency, @Nullable String pageToken) {
        StringBuilder url = new StringBuilder(SKUS_URL)
                .append("?pageSize=").append(PAGE_SIZE)
                .append("&currencyCode=").append(URLEncoder.encode(currency, StandardCharsets.UTF_8));
        if (pageToken != null && !pageToken.isBlank()) {
            url.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    @NotNull
    private static IOException refused(@NotNull HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        LOG.warn("Cloud Billing API refused the BigQuery price list: HTTP " + response.statusCode() + " "
                + (body.length() > MAX_LOGGED_BODY ? body.substring(0, MAX_LOGGED_BODY) : body));
        String reason = errorStatus(body);
        return new IOException("Cloud Billing API answered HTTP " + response.statusCode()
                + (reason == null ? "" : " " + reason));
    }

    @Nullable
    private static String errorStatus(@NotNull String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement error = root.getAsJsonObject().get("error");
            if (error == null || !error.isJsonObject()) {
                return null;
            }
            JsonObject errorObject = error.getAsJsonObject();
            JsonElement status = errorObject.get("status");
            return status != null && status.isJsonPrimitive() ? status.getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
