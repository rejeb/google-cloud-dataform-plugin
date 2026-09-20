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
package io.github.rejeb.dataform.language.gcp.common;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.auth.SslConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists the service accounts of a GCP project through the IAM REST API.
 *
 * <p>Uses the credential owned by the plugin without ever triggering an interactive sign-in. When
 * the caller lacks the {@code iam.serviceAccounts.list} permission the result is flagged as
 * {@link Result#permissionDenied()} so callers can degrade gracefully.
 */
public final class ServiceAccountLister {

    private static final Logger LOG = Logger.getInstance(ServiceAccountLister.class);
    private static final String IAM_BASE = "https://iam.googleapis.com/v1/projects/";
    private static final int PAGE_SIZE = 100;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;

    private ServiceAccountLister() {
    }

    /**
     * Result of a service account listing.
     *
     * @param emails           the service account emails, never {@code null}
     * @param permissionDenied {@code true} when the caller is not allowed to list service accounts
     */
    public record Result(@NotNull List<String> emails, boolean permissionDenied) {

        static Result denied() {
            return new Result(List.of(), true);
        }

        static Result unavailable() {
            return new Result(List.of(), false);
        }
    }

    /**
     * Lists the service accounts of {@code projectId}.
     *
     * <p>Never throws: any failure other than a permission error yields an empty, non-denied result
     * so the caller can behave as a plain text field.
     *
     * @param projectId the GCP project id, must not be blank
     * @return the listing result
     */
    @NotNull
    public static Result list(@Nullable String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Result.unavailable();
        }
        try {
            String token = accessToken(projectId);
            if (token == null) {
                return Result.unavailable();
            }
            List<String> emails = new ArrayList<>();
            String pageToken = null;
            do {
                Page page = fetchPage(projectId, token, pageToken);
                if (page == null) {
                    return emails.isEmpty() ? Result.unavailable() : new Result(emails, false);
                }
                if (page.denied()) {
                    return Result.denied();
                }
                emails.addAll(page.emails);
                pageToken = page.nextPageToken;
            } while (pageToken != null && !pageToken.isBlank());
            return new Result(List.copyOf(emails), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.unavailable();
        } catch (Exception e) {
            LOG.info("Failed to list service accounts for project " + projectId, e);
            return Result.unavailable();
        }
    }

    @Nullable
    private static String accessToken(@NotNull String projectId) {
        try {
            GoogleCredentials credentials = DataformCredentialsService.getInstance()
                    .get()
                    .createWithQuotaProject(projectId.trim());
            credentials.refreshIfExpired();
            return credentials.getAccessToken() != null
                    ? credentials.getAccessToken().getTokenValue()
                    : null;
        } catch (Exception e) {
            LOG.info("No usable credential to list service accounts.", e);
            return null;
        }
    }

    @Nullable
    private static Page fetchPage(@NotNull String projectId,
                                  @NotNull String token,
                                  @Nullable String pageToken) throws IOException, InterruptedException {
        StringBuilder spec = new StringBuilder(IAM_BASE)
                .append(URLEncoder.encode(projectId, StandardCharsets.UTF_8))
                .append("/serviceAccounts?pageSize=").append(PAGE_SIZE);
        if (pageToken != null && !pageToken.isBlank()) {
            spec.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(spec.toString()))
                .header("Authorization", "Bearer " + token)
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .sslContext(SslConfig.sslContext())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status == HTTP_FORBIDDEN || status == HTTP_UNAUTHORIZED) {
            return Page.deniedPage();
        }
        if (status != HTTP_OK) {
            return null;
        }
        return parse(response.body());
    }

    @NotNull
    private static Page parse(@NotNull String json) {
        List<String> emails = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray accounts = root.has("accounts") ? root.getAsJsonArray("accounts") : null;
        if (accounts != null) {
            for (JsonElement element : accounts) {
                JsonObject account = element.getAsJsonObject();
                if (account.has("email")) {
                    String email = account.get("email").getAsString();
                    if (email != null && !email.isBlank()) {
                        emails.add(email);
                    }
                }
            }
        }
        String next = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : null;
        return new Page(emails, next, false);
    }

    private record Page(@NotNull List<String> emails, @Nullable String nextPageToken, boolean denied) {

        static Page deniedPage() {
            return new Page(List.of(), null, true);
        }
    }
}
