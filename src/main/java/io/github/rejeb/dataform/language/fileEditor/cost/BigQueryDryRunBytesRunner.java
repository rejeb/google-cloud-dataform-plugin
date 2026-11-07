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
package io.github.rejeb.dataform.language.fileEditor.cost;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableId;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpCalls;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dry-runs the raw compiled query of an action, unlike the schema dry-run which replaces its
 * dependencies with stubs.
 */
public final class BigQueryDryRunBytesRunner implements DryRunBytesRunner {

    private static final Logger LOG = Logger.getInstance(BigQueryDryRunBytesRunner.class);

    @Override
    public @NotNull CostEstimate run(@NotNull String billingProject, @Nullable String location, @NotNull String sql) {
        try {
            return GcpCalls.execute(AuthTrigger.BACKGROUND, () -> dryRun(billingProject, location, sql));
        } catch (Exception e) {
            String message = messageOf(e);
            LOG.info("BigQuery cost dry-run failed: " + message);
            return CostEstimate.failure(message);
        }
    }

    @NotNull
    private static CostEstimate dryRun(@NotNull String billingProject, @Nullable String location, @NotNull String sql) {
        BigQuery bigQuery = GcpClientsUtils.bigQuery(billingProject);
        QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql)
                .setDryRun(true)
                .setUseLegacySql(false)
                .build();
        JobId jobId = location != null && !location.isBlank()
                ? JobId.newBuilder().setLocation(location).build()
                : JobId.of();
        Job job = bigQuery.create(JobInfo.newBuilder(config).setJobId(jobId).build());
        JobStatistics.QueryStatistics queryStats =
                job.getStatistics() instanceof JobStatistics.QueryStatistics stats ? stats : null;
        Long bytes = queryStats != null ? queryStats.getTotalBytesProcessed() : null;
        String jobLocation = job.getJobId() != null ? job.getJobId().getLocation() : null;
        return CostEstimate.success(bytes == null ? 0L : bytes,
                jobLocation != null && !jobLocation.isBlank() ? jobLocation : location,
                queryStats != null ? minimumBilledBytes(queryStats.getReferencedTables()) : 0);
    }

    static long minimumBilledBytes(@Nullable List<TableId> referencedTables) {
        return referencedTables == null || referencedTables.isEmpty() ? 0 : CostEstimate.MINIMUM_BILLED_BYTES;
    }

    @NotNull
    private static String messageOf(@NotNull Exception e) {
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
