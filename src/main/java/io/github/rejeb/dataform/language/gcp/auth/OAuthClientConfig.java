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

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * OAuth client used by the plugin sign-in flow.
 * <p>
 * The default client is not part of the sources: the build writes it, Base64 encoded, to the
 * {@code oauth-client.properties} resource from the {@code dataformOAuthClientId} and
 * {@code dataformOAuthClientSecret} Gradle properties. A build without them has no default client
 * and sign-in stays disabled. Both values are overridable, in plain text, with system properties so
 * a fork or an internal build can register its own desktop client instead.
 */
public final class OAuthClientConfig {

    private static final Logger LOG = Logger.getInstance(OAuthClientConfig.class);

    private static final String CLIENT_ID_PROPERTY = "dataform.plugin.oauth.clientId";
    private static final String CLIENT_SECRET_PROPERTY = "dataform.plugin.oauth.clientSecret";
    private static final String DEFAULTS_RESOURCE = "oauth-client.properties";
    private static final Properties DEFAULTS = loadDefaults();
    private static final String DEFAULT_CLIENT_ID = decode(DEFAULTS.getProperty("clientId", ""));
    private static final String DEFAULT_CLIENT_SECRET = decode(DEFAULTS.getProperty("clientSecret", ""));

    public static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    public static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";

    public static final List<String> SCOPES = List.of(
            "openid",
            "email",
            "https://www.googleapis.com/auth/cloud-platform"
    );

    private OAuthClientConfig() {
    }

    /**
     * @return the OAuth desktop client id used by the sign-in flow
     */
    public static String clientId() {
        return System.getProperty(CLIENT_ID_PROPERTY, DEFAULT_CLIENT_ID);
    }

    /**
     * @return the OAuth desktop client secret used by the sign-in flow
     */
    public static String clientSecret() {
        return System.getProperty(CLIENT_SECRET_PROPERTY, DEFAULT_CLIENT_SECRET);
    }

    /**
     * @return {@code true} when an OAuth client is configured and sign-in can run
     */
    public static boolean isConfigured() {
        return !clientId().isBlank() && !clientSecret().isBlank();
    }

    private static Properties loadDefaults() {
        Properties properties = new Properties();
        try (InputStream input = OAuthClientConfig.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            LOG.warn("Cannot read the default OAuth client", e);
        }
        return properties;
    }

    static String decode(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            LOG.warn("The default OAuth client is not valid Base64");
            return "";
        }
    }
}
