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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.SslConfig;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reads the reservation a BigQuery job ran on, which the BigQuery client in use does not expose:
 * a REST jobs.get restricted to {@code statistics.reservation_id}.
 */
public final class JobReservationReader {

    private static final Logger LOG = Logger.getInstance(JobReservationReader.class);
    private static final String JOBS_URL = "https://bigquery.googleapis.com/bigquery/v2/projects/";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final int HTTP_OK = 200;

    private JobReservationReader() {
    }

    /**
     * Returns the short name of the reservation the job ran on, or null when it ran on demand or the
     * reservation cannot be read.
     */
    @Nullable
    public static String read(@NotNull String project, @Nullable String location, @NotNull String jobId) {
        try {
            String token = accessToken(project);
            if (token == null) return null;
            String url = JOBS_URL + encode(project) + "/jobs/" + encode(jobId)
                    + "?fields=" + encode("statistics(reservation_id)")
                    + (location == null || location.isBlank() ? "" : "&location=" + encode(location));
            HttpResponse<String> response = SslConfig.getWithBearer(url, token, HTTP_TIMEOUT);
            if (response.statusCode() != HTTP_OK) {
                LOG.info("Could not read the reservation of job " + jobId + ": HTTP " + response.statusCode());
                return null;
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.info("Could not read the reservation of job " + jobId, e);
            return null;
        }
    }

    /**
     * Returns the short name of {@code statistics.reservation_id} in a jobs.get answer: the text after
     * its last {@code /}, {@code .} or {@code :}.
     */
    @Nullable
    public static String parse(@NotNull String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonElement statistics = root.getAsJsonObject().get("statistics");
            if (statistics == null || !statistics.isJsonObject()) return null;
            JsonElement reservation = statistics.getAsJsonObject().get("reservation_id");
            if (reservation == null || !reservation.isJsonPrimitive()) return null;
            String id = reservation.getAsString().trim();
            if (id.isEmpty()) return null;
            int cut = Math.max(id.lastIndexOf('/'), Math.max(id.lastIndexOf('.'), id.lastIndexOf(':')));
            String name = id.substring(cut + 1);
            return name.isEmpty() ? id : name;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String accessToken(@NotNull String project) throws Exception {
        Credentials credentials = GcpClientsUtils.getCredentials(project);
        if (!(credentials instanceof GoogleCredentials google)) return null;
        google.refreshIfExpired();
        AccessToken token = google.getAccessToken();
        return token != null ? token.getTokenValue() : null;
    }

    @NotNull
    private static String encode(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
