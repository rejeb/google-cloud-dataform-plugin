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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A BigQuery dry-run of standard SQL: the job BigQuery validates without running it, and the
 * message shown when it fails.
 */
public final class BigQueryDryRun {

    private BigQueryDryRun() {
    }

    static @NotNull JobInfo jobInfo(@NotNull String sql, @Nullable String location) {
        QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql)
                .setDryRun(true)
                .setUseLegacySql(false)
                .build();
        JobId jobId = location != null && !location.isBlank()
                ? JobId.newBuilder().setLocation(location).build()
                : JobId.of();
        return JobInfo.newBuilder(config).setJobId(jobId).build();
    }

    /**
     * Dry-runs the SQL, billed to the given project.
     *
     * @param billingProject the project the job is created in
     * @param location the location to run in, or {@code null} or blank to let BigQuery choose
     * @param sql the standard SQL to validate
     * @return the dry-run job, whose statistics hold the bytes, schema and statement type
     */
    public static @NotNull Job run(@NotNull String billingProject, @Nullable String location, @NotNull String sql) {
        return GcpClientsUtils.bigQuery(billingProject).create(jobInfo(sql, location));
    }

    /**
     * @return the message of the exception, else of its cause, else the exception's simple class name
     */
    public static @NotNull String messageOf(@NotNull Exception e) {
        String message = e.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }
}
