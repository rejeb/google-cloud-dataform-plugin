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
package io.github.rejeb.dataform.language.util;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleAuthUtils;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.dataform.v1.DataformClient;
import com.google.cloud.dataform.v1.DataformSettings;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class GcpClientsUtils {
    private static final Logger LOG = Logger.getInstance(GcpClientsUtils.class);

    private static GoogleCredentials googleCredentials;

    public static BigQuery bigQuery(String projectId) {
        return BigQueryOptions.newBuilder()
                .setCredentials(GcpClientsUtils.getCredentials())
                .setProjectId(projectId)
                .build()
                .getService();
    }

    public static DataformClient dataformClient() throws IOException {
        DataformSettings settings = DataformSettings.newBuilder()
                .setCredentialsProvider(GcpClientsUtils::getCredentials)
                .build();
        return DataformClient.create(settings);
    }

    public static synchronized Credentials getCredentials() throws RuntimeException {
        if (googleCredentials != null) {
            try {
                googleCredentials.refreshIfExpired();
                return googleCredentials;
            } catch (Exception e) {
                LOG.warn("Cached credentials refresh failed, reloading from ADC.", e);
            }
        }
        googleCredentials = loadFromDisk();
        return googleCredentials;
    }

    private static GoogleCredentials loadFromDisk() throws RuntimeException {
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        String adcPath = envPath != null ? envPath : GoogleAuthUtils.getWellKnownCredentialsPath();
        try (InputStream stream = Files.newInputStream(Path.of(adcPath))) {
            return GoogleCredentials.fromStream(stream).createScoped();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
