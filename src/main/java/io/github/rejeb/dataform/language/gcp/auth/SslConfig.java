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

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.ssl.CertificateManager;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * TLS setup shared by every HTTPS call the plugin makes on its own.
 * <p>
 * By default the IDE trust manager is used, so certificates issued by a corporate TLS inspecting
 * proxy are accepted as soon as they are trusted by the IDE or the system trust store, while
 * certificates remain verified. Setting the {@code dataform.plugin.ssl.trustAll} system property to
 * {@code true} disables certificate and hostname verification entirely, which exposes the stored
 * Google refresh token to man-in-the-middle interception and must only be used on a trusted network
 * for troubleshooting.
 */
public final class SslConfig {

    private static final Logger LOG = Logger.getInstance(SslConfig.class);

    private static final String TRUST_ALL_PROPERTY = "dataform.plugin.ssl.trustAll";

    private SslConfig() {
    }

    /**
     * @return {@code true} when certificate verification is disabled by system property
     */
    public static boolean isTrustAllEnabled() {
        return Boolean.getBoolean(TRUST_ALL_PROPERTY);
    }

    /**
     * @return the SSL context to use for plugin HTTPS calls
     */
    @NotNull
    public static SSLContext sslContext() {
        if (!isTrustAllEnabled()) {
            return CertificateManager.getInstance().getSslContext();
        }
        LOG.warn("TLS certificate verification is disabled for Dataform plugin HTTPS calls ("
                + TRUST_ALL_PROPERTY + "=true).");
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAllManager()}, new SecureRandom());
            return context;
        } catch (Exception e) {
            LOG.error("Cannot build a trust-all SSL context, falling back to the IDE one.", e);
            return CertificateManager.getInstance().getSslContext();
        }
    }

    /**
     * @return the hostname verifier to use for plugin HTTPS calls
     */
    @NotNull
    public static HostnameVerifier hostnameVerifier() {
        return isTrustAllEnabled()
                ? (hostname, session) -> true
                : javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier();
    }

    /**
     * @return a Google HTTP transport factory honouring the plugin TLS setup
     */
    @NotNull
    public static HttpTransportFactory httpTransportFactory() {
        NetHttpTransport transport = new NetHttpTransport.Builder()
                .setSslSocketFactory(sslContext().getSocketFactory())
                .setHostnameVerifier(hostnameVerifier())
                .build();
        return () -> transport;
    }

    private static X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
