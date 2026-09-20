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

import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import io.github.rejeb.dataform.language.gcp.auth.SslConfig;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Resolves the GCP identity (name + email) of the credential owned by the plugin.
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
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final String FALLBACK_NAME = System.getProperty("user.name", "dataform-plugin");
    private static final String FALLBACK_EMAIL = FALLBACK_NAME + "@dataform-plugin.local";

    private GcpIdentityResolver() {}

    /**
     * Resolves the {@link CommitAuthorConfig} from the credential the plugin uses for every other
     * GCP call, so the author of a commit is the account that pushed it.
     * Never throws — returns a fallback identity on any error.
     */
    @NotNull
    public static CommitAuthorConfig resolve() {
        try {
            GoogleCredentials credentials = DataformCredentialsService.getInstance().get();
            credentials.refreshIfExpired();
            switch (credentials) {
                case ServiceAccountCredentials sa -> {
                    String email = sa.getClientEmail();
                    String name = sa.getClientId() != null ? sa.getClientId() : email;
                    return new CommitAuthorConfig(name, email);
                }
                case ComputeEngineCredentials ce -> {
                    String email = ce.getAccount();
                    return new CommitAuthorConfig(email, email);
                }
                case UserCredentials userCredentials -> {
                    return resolveFromUserInfo(credentials);
                }
                default -> {
                }
            }
            LOG.warn("Unknown credential type: " + credentials.getClass().getName()
                    + " — using fallback identity.");
        } catch (Exception e) {
            LOG.warn("Failed to resolve GCP identity — using fallback identity.", e);
        }
        return new CommitAuthorConfig(FALLBACK_NAME, FALLBACK_EMAIL);
    }

    @NotNull
    private static CommitAuthorConfig resolveFromUserInfo(
            @NotNull GoogleCredentials credentials
    ) throws IOException, InterruptedException {
        String accessToken = credentials.getAccessToken().getTokenValue();
        HttpRequest request = HttpRequest.newBuilder(URI.create(USERINFO_URL))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .sslContext(SslConfig.sslContext())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("userinfo endpoint returned " + response.statusCode());
        }
        String json = response.body();
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        String email = obj.has("email") ? obj.get("email").getAsString() : null;
        String name = obj.has("name") ? obj.get("name").getAsString() : null;
        if (email == null || email.isBlank()) {
            throw new IOException("email field missing from userinfo response");
        }
        return new CommitAuthorConfig(
                name != null && !name.isBlank() ? name : email,
                email
        );
    }
}
