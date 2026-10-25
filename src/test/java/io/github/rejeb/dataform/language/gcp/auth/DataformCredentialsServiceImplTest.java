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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class DataformCredentialsServiceImplTest extends BasePlatformTestCase {

    private static final String OTHER_CLIENT_ID = "123456789-other.apps.googleusercontent.com";
    private static final String ACCOUNT_EMAIL = "someone@example.com";
    private static final String TOKEN_RESPONSE =
            "{\"access_token\":\"adc-access-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

    private final PasswordSafeCredentialsStore store = new PasswordSafeCredentialsStore();
    private String realUserHome;
    private Path userHome;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        store.clear();
        realUserHome = System.getProperty("user.home");
        userHome = Files.createTempDirectory("dataform-user-home");
        System.setProperty("user.home", userHome.toString());
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            System.setProperty("user.home", realUserHome);
            NioFiles.deleteRecursively(userHome);
            store.clear();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testStoredCredentialOfAnotherOAuthClientIsDiscarded() throws Exception {
        store.save(new StoredCredentials(OTHER_CLIENT_ID, "other-secret", "other-refresh-token", ACCOUNT_EMAIL));

        Throwable failure = failureOf(new DataformCredentialsServiceImpl()::get);

        assertInstanceOf(failure, GcpAuthRequiredException.class);
        assertTrue(store.load().isEmpty());
    }

    public void testStoredCredentialOfTheConfiguredClientIsKept() throws Exception {
        store.save(new StoredCredentials(OAuthClientConfig.clientId(), OAuthClientConfig.clientSecret(),
                "refresh-token", ACCOUNT_EMAIL));

        String accountEmail = onPooledThread(new DataformCredentialsServiceImpl()::getAccountEmail);

        assertEquals(ACCOUNT_EMAIL, accountEmail);
        assertTrue(store.load().isPresent());
    }

    public void testApplicationDefaultCredentialsAreNeverUsed() throws Exception {
        HttpServer tokenServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tokenServer.createContext("/token", exchange -> {
            byte[] body = TOKEN_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        tokenServer.start();
        try {
            writeApplicationDefaultCredentials(
                    "http://127.0.0.1:" + tokenServer.getAddress().getPort() + "/token");

            Throwable failure = failureOf(new DataformCredentialsServiceImpl()::get);

            assertInstanceOf(failure, GcpAuthRequiredException.class);
        } finally {
            tokenServer.stop(0);
        }
    }

    private void writeApplicationDefaultCredentials(String tokenUri) throws IOException {
        Path file = userHome.resolve(".config").resolve("gcloud").resolve("application_default_credentials.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {
                  "type": "authorized_user",
                  "client_id": "%s",
                  "client_secret": "adc-secret",
                  "refresh_token": "adc-refresh-token",
                  "token_uri": "%s"
                }
                """.formatted(OTHER_CLIENT_ID, tokenUri));
    }

    private static <T> T onPooledThread(Callable<T> call) throws Exception {
        return ApplicationManager.getApplication().executeOnPooledThread(call).get(30, TimeUnit.SECONDS);
    }

    private static Throwable failureOf(Callable<?> call) throws Exception {
        return onPooledThread(() -> {
            try {
                call.call();
                return null;
            } catch (Exception e) {
                return e;
            }
        });
    }
}
