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

import com.google.cloud.bigquery.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;

public final class BigQueryExecutionServiceImpl implements BigQueryExecutionService {

    private static final Logger LOG = Logger.getInstance(BigQueryExecutionServiceImpl.class);

    public BigQueryExecutionServiceImpl(@NotNull Project project) {
    }

    @Override
    public @NotNull BigQueryJobResult execute(
            @NotNull String sql,
            @NotNull String projectId,
            @NotNull String tableName
    ) {
        try {
            BigQuery bigQuery = GcpClientsUtils.bigQuery(projectId);
            QueryJobConfiguration config = QueryJobConfiguration.newBuilder(sql)
                    .setUseLegacySql(false)
                    .build();

            Job job = bigQuery.create(JobInfo.of(config));
            job = job.waitFor();

            if (job == null) {
                return failure(tableName, "Job no longer exists after submission");
            }
            if (job.getStatus().getError() != null) {
                return failure(tableName, job.getStatus().getError().getMessage());
            }

            TableResult tableResult = job.getQueryResults();
            Schema schema = tableResult.getSchema();

            BigQueryPagedResult pagedResult = new BigQueryPagedResult(
                    job,
                    schema,
                    tableResult.getTotalRows()
            );

            BigQueryJobStats stats = extractStats(job, projectId, tableResult.getTotalRows());
            return new BigQueryJobResult(tableName, stats, pagedResult, null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(tableName, "Query execution interrupted");
        } catch (RuntimeException e) {
            LOG.warn("Failed to build BigQuery client", e);
            return failure(tableName, "Authentication error: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("BigQuery execution failed", e);
            return failure(tableName, e.getMessage());
        }
    }

    private BigQueryJobStats extractStats(@NotNull Job job, @NotNull String projectId, long totalRows) {
        JobStatistics stats = job.getStatistics();
        JobStatistics.QueryStatistics queryStats = (stats instanceof JobStatistics.QueryStatistics qs) ? qs : null;
        String jobId = job.getJobId().getJob();
        String location = job.getJobId().getLocation();
        long creation = stats != null && stats.getCreationTime() != null ? stats.getCreationTime() : 0L;
        long start = stats != null && stats.getStartTime() != null ? stats.getStartTime() : 0L;
        long end = stats != null && stats.getEndTime() != null ? stats.getEndTime() : 0L;
        long bytes = queryStats != null && queryStats.getTotalBytesProcessed() != null
                ? queryStats.getTotalBytesProcessed() : 0L;
        boolean cacheHit = queryStats != null && Boolean.TRUE.equals(queryStats.getCacheHit());
        String stmtType = queryStats != null && queryStats.getStatementType() != null
                ? queryStats.getStatementType().name() : null;
        return new BigQueryJobStats(jobId, projectId, location, creation, start, end, bytes, cacheHit, stmtType, totalRows);
    }

    private BigQueryJobResult failure(@NotNull String tableName, @NotNull String message) {
        return new BigQueryJobResult(tableName, null, null, message);
    }
}
