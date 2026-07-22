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
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.dataform.v1.DataformClient;
import com.google.cloud.dataform.v1.DataformSettings;
import com.google.cloud.http.HttpTransportOptions;
import io.github.rejeb.dataform.language.gcp.auth.SslConfig;
import io.github.rejeb.dataform.language.gcp.auth.DataformCredentialsService;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class GcpClientsUtils {

    private static final String RESOURCE_NAME_PREFIX = "projects/";

    public static BigQuery bigQuery(String projectId) {
        return BigQueryOptions.newBuilder()
                .setCredentials(getCredentials(projectId))
                .setProjectId(projectId)
                .setTransportOptions(HttpTransportOptions.newBuilder()
                        .setHttpTransportFactory(SslConfig.httpTransportFactory())
                        .build())
                .build()
                .getService();
    }

    /**
     * @param quotaProjectId project billed for quota of client-based API calls, may be {@code null}
     * @return a Dataform client bound to the plugin credential
     */
    public static DataformClient dataformClient(@Nullable String quotaProjectId) throws IOException {
        DataformSettings settings = DataformSettings.newHttpJsonBuilder()
                .setCredentialsProvider(() -> getCredentials(quotaProjectId))
                .setTransportChannelProvider(DataformSettings.defaultHttpJsonTransportProviderBuilder()
                        .setHttpTransport(SslConfig.httpTransportFactory().create())
                        .build())
                .build();
        return DataformClient.create(settings);
    }

    public static DataformClient dataformClient() throws IOException {
        return dataformClient(null);
    }

    /**
     * @return the credential owned by the plugin, never triggering an interactive sign-in
     */
    public static Credentials getCredentials() throws RuntimeException {
        return getCredentials(null);
    }

    /**
     * @param quotaProjectId project billed for quota of client-based API calls, may be {@code null}
     * @return the credential owned by the plugin, never triggering an interactive sign-in
     */
    public static Credentials getCredentials(@Nullable String quotaProjectId) throws RuntimeException {
        GoogleCredentials credentials = DataformCredentialsService.getInstance().get();
        if (quotaProjectId == null || quotaProjectId.isBlank()) {
            return credentials;
        }
        return credentials.createWithQuotaProject(quotaProjectId);
    }

    /**
     * Extracts the project id from a GCP resource name such as
     * {@code projects/my-project/locations/eu/repositories/my-repo}.
     *
     * @param resourceName fully qualified resource name
     * @return the project id, or {@code null} when the name has an unexpected shape
     */
    @Nullable
    public static String projectIdFromResourceName(@Nullable String resourceName) {
        if (resourceName == null || !resourceName.startsWith(RESOURCE_NAME_PREFIX)) {
            return null;
        }
        int start = RESOURCE_NAME_PREFIX.length();
        int end = resourceName.indexOf('/', start);
        String projectId = end < 0 ? resourceName.substring(start) : resourceName.substring(start, end);
        return projectId.isBlank() ? null : projectId;
    }
}
