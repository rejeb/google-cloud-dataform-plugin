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
import com.intellij.util.net.ssl.CertificateManager;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * TLS setup shared by every HTTPS call the plugin makes on its own.
 * <p>
 * Certificates are always verified, using the IDE trust manager so that certificates issued by a
 * corporate TLS inspecting proxy are accepted as soon as they are trusted by the IDE or by the
 * system trust store.
 */
public final class SslConfig {

    private SslConfig() {
    }

    /**
     * @return the SSL context to use for plugin HTTPS calls
     */
    @NotNull
    public static SSLContext sslContext() {
        return CertificateManager.getInstance().getSslContext();
    }

    /**
     * @return a Google HTTP transport factory honouring the plugin TLS setup
     */
    @NotNull
    public static HttpTransportFactory httpTransportFactory() {
        NetHttpTransport transport = new NetHttpTransport.Builder()
                .setSslSocketFactory(sslContext().getSocketFactory())
                .build();
        return () -> transport;
    }

    /**
     * @param connectTimeout how long a connection may take to open
     * @return an HTTP client honouring the plugin TLS setup
     */
    @NotNull
    public static HttpClient httpClient(@NotNull Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).sslContext(sslContext()).build();
    }

    /**
     * Sends a GET request authorized by an OAuth access token.
     *
     * @param url     the URL to fetch
     * @param token   the access token
     * @param timeout how long connecting and answering may each take
     * @return the response, with its body read as UTF-8 text
     * @throws IOException          when the request fails
     * @throws InterruptedException when the thread is interrupted while waiting
     */
    @NotNull
    public static HttpResponse<String> getWithBearer(@NotNull String url, @NotNull String token,
                                                     @NotNull Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token).timeout(timeout).GET().build();
        return httpClient(timeout).send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
