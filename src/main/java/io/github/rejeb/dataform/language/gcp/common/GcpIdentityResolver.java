/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Resolves the GCP identity (name + email) from Application Default Credentials.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@link ServiceAccountCredentials} — reads {@code clientEmail} directly</li>
 *   <li>{@link ComputeEngineCredentials} — reads the service account via metadata server</li>
 *   <li>{@link UserCredentials} — fetches email via {@code oauth2/v3/userinfo}</li>
 * </ul>
 *
 * Falls back to {@code system user@dataform-plugin.local} if identity cannot be resolved.
 */
public final class GcpIdentityResolver {

    private static final Logger LOG = Logger.getInstance(GcpIdentityResolver.class);
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String FALLBACK_NAME = System.getProperty("user.name", "dataform-plugin");
    private static final String FALLBACK_EMAIL = FALLBACK_NAME + "@dataform-plugin.local";

    private GcpIdentityResolver() {}

    /**
     * Resolves the {@link CommitAuthorConfig} from ADC.
     * Never throws — returns a fallback identity on any error.
     */
    @NotNull
    public static CommitAuthorConfig resolve() {
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
            credentials.refreshIfExpired();

            if (credentials instanceof ServiceAccountCredentials sa) {
                String email = sa.getClientEmail();
                String name = sa.getClientId() != null ? sa.getClientId() : email;
                return new CommitAuthorConfig(name, email);
            }

            if (credentials instanceof ComputeEngineCredentials ce) {
                String email = ce.getAccount(); // service account email on GCE
                return new CommitAuthorConfig(email, email);
            }

            if (credentials instanceof UserCredentials) {
                return resolveFromUserInfo(credentials);
            }

            LOG.warn("Unknown ADC credential type: " + credentials.getClass().getName()
                    + " — using fallback identity.");
        } catch (Exception e) {
            LOG.warn("Failed to resolve GCP identity from ADC — using fallback identity.", e);
        }
        return new CommitAuthorConfig(FALLBACK_NAME, FALLBACK_EMAIL);
    }

    @NotNull
    private static CommitAuthorConfig resolveFromUserInfo(
            @NotNull GoogleCredentials credentials
    ) throws IOException {
        String accessToken = credentials.getAccessToken().getTokenValue();

        URL url = new URL(USERINFO_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);

        try (InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String email = extractJsonField(json, "email");
            String name = extractJsonField(json, "name");
            if (email == null || email.isBlank()) {
                throw new IOException("email field missing from userinfo response: " + json);
            }
            return new CommitAuthorConfig(
                    name != null && !name.isBlank() ? name : email,
                    email
            );
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Minimal JSON field extractor — avoids pulling in a JSON dependency just for this.
     * Handles simple flat JSON: {@code {"field":"value"}}.
     */
    private static String extractJsonField(@NotNull String json, @NotNull String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
