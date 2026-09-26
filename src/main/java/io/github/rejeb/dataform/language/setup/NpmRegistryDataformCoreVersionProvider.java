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
package io.github.rejeb.dataform.language.setup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public final class NpmRegistryDataformCoreVersionProvider implements DataformCoreVersionProvider {

    private static final Logger LOG = Logger.getInstance(NpmRegistryDataformCoreVersionProvider.class);
    private static final String LATEST_URL = "https://registry.npmjs.org/@dataform/core/latest";
    private static final int TIMEOUT_MS = 5_000;

    private volatile String latestVersion;

    @Override
    public Optional<String> fetchLatestVersion() {
        try {
            String body = HttpRequests.request(LATEST_URL)
                    .accept("application/json")
                    .connectTimeout(TIMEOUT_MS)
                    .readTimeout(TIMEOUT_MS)
                    .readString();
            Optional<String> version = Optional.ofNullable(parseVersion(body));
            version.ifPresent(v -> latestVersion = v);
            return version;
        } catch (Exception e) {
            LOG.info("Unable to fetch the latest @dataform/core version: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public @NotNull String knownLatestVersion() {
        return Objects.requireNonNullElse(latestVersion, FALLBACK_VERSION);
    }

    /**
     * Extracts the {@code version} field of an npm registry package manifest.
     */
    static @Nullable String parseVersion(@Nullable String manifest) {
        if (manifest == null || manifest.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(manifest);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject json = root.getAsJsonObject();
            JsonElement version = json.get("version");
            if (version == null || !version.isJsonPrimitive()) {
                return null;
            }
            String value = version.getAsString().trim();
            return value.isEmpty() ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
