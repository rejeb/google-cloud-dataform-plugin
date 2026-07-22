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
package io.github.rejeb.dataform.language.gcp.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Loopback OAuth 2.0 authorization code flow with PKCE, equivalent to what
 * {@code gcloud auth application-default login} performs, but keeping the resulting credential
 * inside the plugin.
 */
public final class GoogleOAuthLoginFlow {

    private static final Logger LOG = Logger.getInstance(GoogleOAuthLoginFlow.class);

    private static final Duration OVERALL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_MILLIS = 200L;

    private static final String SUCCESS_REDIRECT_URL = "https://docs.cloud.google.com/sdk/auth_success";

    private GoogleOAuthLoginFlow() {
    }

    /**
     * Runs the interactive sign-in. Must be called from a background thread, and only as a direct
     * consequence of a user action.
     *
     * @param indicator progress indicator used to honour cancellation
     * @return the credential to persist
     * @throws IOException when the flow could not be completed
     */
    @NotNull
    public static StoredCredentials execute(@NotNull ProgressIndicator indicator) throws IOException {
        if (!OAuthClientConfig.isConfigured()) {
            throw new IOException("No OAuth client configured for the Dataform plugin sign-in flow.");
        }
        String verifier = randomUrlSafe(64);
        String challenge = codeChallenge(verifier);
        String state = randomUrlSafe(24);

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            String redirectUri = "http://127.0.0.1:" + server.getAddress().getPort();
            server.createContext("/", exchange -> handleCallback(exchange, state, codeFuture));
            server.start();

            indicator.setText("Waiting for Google sign-in in your browser...");
            BrowserUtil.browse(authorizationUrl(redirectUri, challenge, state));

            String code = awaitCode(codeFuture, indicator);
            return exchangeCode(code, verifier, redirectUri);
        } finally {
            server.stop(0);
        }
    }

    private static void handleCallback(
            HttpExchange exchange,
            String expectedState,
            CompletableFuture<String> codeFuture
    ) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String code = params.get("code");
        String state = params.get("state");
        String error = params.get("error");

        IOException failure = null;
        if (error != null) {
            failure = new IOException("Sign-in refused: " + error);
        } else if (code == null || !expectedState.equals(state)) {
            failure = new IOException("Invalid authorization response.");
        }

        if (failure == null) {
            exchange.getResponseHeaders().add("Location", SUCCESS_REDIRECT_URL);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            codeFuture.complete(code);
            return;
        }

        byte[] bytes = errorPage(failure.getMessage()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        codeFuture.completeExceptionally(failure);
    }

    private static String awaitCode(
            CompletableFuture<String> codeFuture,
            ProgressIndicator indicator
    ) throws IOException {
        long deadline = System.nanoTime() + OVERALL_TIMEOUT.toNanos();
        while (true) {
            if (indicator.isCanceled()) {
                codeFuture.cancel(true);
                throw new ProcessCanceledException();
            }
            if (System.nanoTime() > deadline) {
                codeFuture.cancel(true);
                throw new IOException("Timed out waiting for the Google sign-in to complete.");
            }
            try {
                return codeFuture.get(POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProcessCanceledException();
            } catch (Exception e) {
                throw new IOException(rootMessage(e), e);
            }
        }
    }

    private static StoredCredentials exchangeCode(
            String code,
            String verifier,
            String redirectUri
    ) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", code);
        form.put("client_id", OAuthClientConfig.clientId());
        form.put("client_secret", OAuthClientConfig.clientSecret());
        form.put("code_verifier", verifier);
        form.put("grant_type", "authorization_code");
        form.put("redirect_uri", redirectUri);

        JsonObject response = postForm(OAuthClientConfig.TOKEN_ENDPOINT, form);
        if (!response.has("refresh_token")) {
            throw new IOException("Google did not return a refresh token. "
                    + "Revoke the plugin access in your Google account and sign in again.");
        }
        String refreshToken = response.get("refresh_token").getAsString();
        String accessToken = response.has("access_token")
                ? response.get("access_token").getAsString()
                : null;

        return new StoredCredentials(
                OAuthClientConfig.clientId(),
                OAuthClientConfig.clientSecret(),
                refreshToken,
                resolveEmail(accessToken)
        );
    }

    @Nullable
    private static String resolveEmail(@Nullable String accessToken) {
        if (accessToken == null) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(OAuthClientConfig.USERINFO_ENDPOINT))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            return json.has("email") ? json.get("email").getAsString() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.debug("Could not resolve the signed-in account email.", e);
            return null;
        }
    }

    private static JsonObject postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        form.forEach((key, value) -> {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(encode(key)).append('=').append(encode(value));
        });
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() != 200) {
                throw new IOException("Google token endpoint returned " + response.statusCode()
                        + ": " + json);
            }
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException();
        }
    }

    private static String authorizationUrl(String redirectUri, String challenge, String state) {
        return OAuthClientConfig.AUTH_ENDPOINT
                + "?response_type=code"
                + "&client_id=" + encode(OAuthClientConfig.clientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(String.join(" ", OAuthClientConfig.SCOPES))
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256"
                + "&state=" + encode(state)
                + "&access_type=offline"
                + "&prompt=consent";
    }

    private static Map<String, String> parseQuery(@Nullable String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            params.put(
                    java.net.URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .sslContext(SslConfig.sslContext())
                .build();
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String codeChallenge(String verifier) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IOException("SHA-256 is not available for PKCE.", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String errorPage(String message) {
        return "<html><body style=\"font-family:sans-serif\"><h3>Sign-in failed</h3><p>"
                + message + "</p></body></html>";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }
}
