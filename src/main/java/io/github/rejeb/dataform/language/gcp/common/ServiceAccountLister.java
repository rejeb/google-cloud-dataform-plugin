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
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.auth.SslConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Lists the service accounts usable in a GCP project.
 *
 * <p>Two sources are merged: the accounts owned by the project, from the IAM API, then the service
 * accounts granted a role in the project IAM policy, from the Cloud Resource Manager API. The policy
 * is the only place listing the Google-provided service agents, such as the Dataform one, since they
 * belong to Google-owned projects. Both APIs must be enabled in the project of the plugin's OAuth
 * client.
 *
 * <p>Uses the credential owned by the plugin without ever triggering an interactive sign-in. When
 * the caller may read neither source the result is flagged as {@link Result#permissionDenied()} so
 * callers can degrade gracefully.
 */
public final class ServiceAccountLister {

    private static final Logger LOG = Logger.getInstance(ServiceAccountLister.class);
    private static final String IAM_BASE = "https://iam.googleapis.com/v1/projects/";
    private static final String RESOURCE_MANAGER_BASE = "https://cloudresourcemanager.googleapis.com/v1/projects/";
    private static final String GET_IAM_POLICY_BODY = "{\"options\":{\"requestedPolicyVersion\":3}}";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_LOGGED_BODY = 2000;
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

        static Result merge(@NotNull Result owned, @NotNull Result granted) {
            LinkedHashSet<String> emails = new LinkedHashSet<>(owned.emails());
            emails.addAll(granted.emails());
            return new Result(List.copyOf(emails), owned.permissionDenied() && granted.permissionDenied());
        }
    }

    /**
     * Lists the service accounts of {@code projectId}, those owned by the project first, then the
     * other ones granted a role in it, Google-provided service agents included.
     *
     * <p>Never throws: when one source fails the other one is still listed, and any failure other
     * than a permission error on both yields a non-denied result so the caller can behave as a plain
     * text field.
     *
     * @param projectId the GCP project id, must not be blank
     * @return the listing result
     */
    @NotNull
    public static Result list(@Nullable String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Result.unavailable();
        }
        String project = projectId.trim();
        try {
            String token = accessToken(project);
            if (token == null) {
                return Result.unavailable();
            }
            return Result.merge(projectAccounts(project, token), grantedAccounts(project, token));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.unavailable();
        }
    }

    @Nullable
    private static String accessToken(@NotNull String projectId) {
        try {
            GoogleCredentials credentials = DataformCredentialsService.getInstance()
                    .get()
                    .createWithQuotaProject(projectId);
            credentials.refreshIfExpired();
            return credentials.getAccessToken() != null
                    ? credentials.getAccessToken().getTokenValue()
                    : null;
        } catch (Exception e) {
            LOG.info("No usable credential to list service accounts.", e);
            return null;
        }
    }

    @NotNull
    private static Result projectAccounts(@NotNull String projectId,
                                          @NotNull String token) throws InterruptedException {
        List<String> emails = new ArrayList<>();
        String pageToken = null;
        try {
            do {
                HttpResponse<String> response = SslConfig.getWithBearer(accountsUrl(projectId, pageToken), token,
                        HTTP_TIMEOUT);
                if (isDenied(response)) {
                    logRefusal("IAM API refused the service accounts of " + projectId, response);
                    return Result.denied();
                }
                if (response.statusCode() != HTTP_OK) {
                    logRefusal("IAM API failed to list the service accounts of " + projectId, response);
                    break;
                }
                ServiceAccountResponseParser.AccountsPage page =
                        ServiceAccountResponseParser.accountsPage(response.body());
                emails.addAll(page.emails());
                pageToken = page.nextPageToken();
            } while (pageToken != null && !pageToken.isBlank());
        } catch (IOException | RuntimeException e) {
            LOG.info("Failed to list the service accounts of project " + projectId, e);
        }
        return new Result(List.copyOf(emails), false);
    }

    @NotNull
    private static Result grantedAccounts(@NotNull String projectId,
                                          @NotNull String token) throws InterruptedException {
        try {
            HttpResponse<String> response = SslConfig.postJsonWithBearer(policyUrl(projectId), token,
                    GET_IAM_POLICY_BODY, HTTP_TIMEOUT);
            if (isDenied(response)) {
                logRefusal("Cloud Resource Manager API refused the IAM policy of " + projectId, response);
                return Result.denied();
            }
            if (response.statusCode() != HTTP_OK) {
                logRefusal("Cloud Resource Manager API failed to read the IAM policy of " + projectId, response);
                return Result.unavailable();
            }
            return new Result(ServiceAccountResponseParser.policyServiceAccounts(response.body()), false);
        } catch (IOException | RuntimeException e) {
            LOG.info("Failed to read the IAM policy of project " + projectId, e);
            return Result.unavailable();
        }
    }

    @NotNull
    private static String accountsUrl(@NotNull String projectId, @Nullable String pageToken) {
        StringBuilder url = new StringBuilder(IAM_BASE)
                .append(URLEncoder.encode(projectId, StandardCharsets.UTF_8))
                .append("/serviceAccounts?pageSize=").append(PAGE_SIZE);
        if (pageToken != null && !pageToken.isBlank()) {
            url.append("&pageToken=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    @NotNull
    private static String policyUrl(@NotNull String projectId) {
        return RESOURCE_MANAGER_BASE + URLEncoder.encode(projectId, StandardCharsets.UTF_8) + ":getIamPolicy";
    }

    private static boolean isDenied(@NotNull HttpResponse<String> response) {
        return response.statusCode() == HTTP_FORBIDDEN || response.statusCode() == HTTP_UNAUTHORIZED;
    }

    private static void logRefusal(@NotNull String message, @NotNull HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        LOG.info(message + ": HTTP " + response.statusCode() + " "
                + (body.length() > MAX_LOGGED_BODY ? body.substring(0, MAX_LOGGED_BODY) : body));
    }
}
