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

import java.util.List;

/**
 * OAuth client used by the plugin sign-in flow.
 * <p>
 * The defaults are the Google Cloud SDK desktop client, so that the sign-in produces exactly the
 * same kind of credential as {@code gcloud auth application-default login}. These values are not
 * owned by this plugin and Google may rotate them at any time: both are overridable with system
 * properties so a fork or an internal build can register its own desktop client instead.
 */
public final class OAuthClientConfig {

    private static final String CLIENT_ID_PROPERTY = "dataform.plugin.oauth.clientId";
    private static final String CLIENT_SECRET_PROPERTY = "dataform.plugin.oauth.clientSecret";

    private static final String DEFAULT_CLIENT_ID =
            "764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com";
    private static final String DEFAULT_CLIENT_SECRET = "d-FL95Q19q7MQmFpd7hHD0Ty";

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
}
