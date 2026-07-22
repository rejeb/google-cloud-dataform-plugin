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
}
