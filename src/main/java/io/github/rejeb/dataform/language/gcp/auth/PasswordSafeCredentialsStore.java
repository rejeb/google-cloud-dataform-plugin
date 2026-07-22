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
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class PasswordSafeCredentialsStore implements DataformCredentialsStore {

    private static final Logger LOG = Logger.getInstance(PasswordSafeCredentialsStore.class);

    private static final String SERVICE_NAME = "Dataform GCP";
    private static final String KEY = "application-default-credentials";

    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String ACCOUNT_EMAIL = "account_email";

    @Override
    public @NotNull Optional<StoredCredentials> load() {
        String value = PasswordSafe.getInstance().getPassword(attributes());
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonObject json = JsonParser.parseString(value).getAsJsonObject();
            if (!json.has(CLIENT_ID) || !json.has(CLIENT_SECRET) || !json.has(REFRESH_TOKEN)) {
                return Optional.empty();
            }
            return Optional.of(new StoredCredentials(
                    json.get(CLIENT_ID).getAsString(),
                    json.get(CLIENT_SECRET).getAsString(),
                    json.get(REFRESH_TOKEN).getAsString(),
                    json.has(ACCOUNT_EMAIL) ? json.get(ACCOUNT_EMAIL).getAsString() : null
            ));
        } catch (Exception e) {
            LOG.warn("Stored Dataform GCP credential is unreadable, ignoring it.", e);
            return Optional.empty();
        }
    }

    @Override
    public void save(@NotNull StoredCredentials credentials) {
        JsonObject json = new JsonObject();
        json.addProperty(CLIENT_ID, credentials.clientId());
        json.addProperty(CLIENT_SECRET, credentials.clientSecret());
        json.addProperty(REFRESH_TOKEN, credentials.refreshToken());
        if (credentials.accountEmail() != null) {
            json.addProperty(ACCOUNT_EMAIL, credentials.accountEmail());
        }
        PasswordSafe.getInstance().set(attributes(), new Credentials(KEY, json.toString()));
    }

    @Override
    public void clear() {
        PasswordSafe.getInstance().set(attributes(), null);
    }

    private static CredentialAttributes attributes() {
        return new CredentialAttributes(
                CredentialAttributesKt.generateServiceName(SERVICE_NAME, KEY), KEY);
    }
}
