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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpCalls;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public final class GcpBigQueryJobRepository implements BigQueryJobRepository {

    private static final Logger LOG = Logger.getInstance(GcpBigQueryJobRepository.class);

    private static final Map<String, String> DATASET_LOCATIONS = new ConcurrentHashMap<>();

    @Override
    @Nullable
    public String resolveDatasetLocation(@NotNull String project, @NotNull String dataset) {
        String cacheKey = project + ":" + dataset;
        String cached = DATASET_LOCATIONS.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            Dataset ds = GcpCalls.execute(AuthTrigger.BACKGROUND,
                    () -> GcpClientsUtils.bigQuery(project).getDataset(DatasetId.of(project, dataset)));
            if (ds == null || ds.getLocation() == null) {
                LOG.warn("BigQuery dataset " + cacheKey + " not found or has no location.");
                return null;
            }
            String location = ds.getLocation();
            DATASET_LOCATIONS.put(cacheKey, location);
            return location;
        } catch (Exception e) {
            LOG.warn("Failed to resolve the BigQuery location of dataset " + cacheKey, e);
            return null;
        }
    }

    @Override
    @Nullable
    public BigQueryJobDetails getJobDetails(
            @NotNull String jobId,
            @NotNull String project,
            @Nullable String location
    ) {
        BigQuery bq = GcpClientsUtils.bigQuery(project);
        CompletableFuture<List<BigQueryChildJob>> children = CompletableFuture.supplyAsync(
                () -> childJobsOf(bq, jobId), AppExecutorUtil.getAppExecutorService());

        Job job = GcpCalls.execute(AuthTrigger.USER_ACTION, () -> GcpClientsUtils.bigQuery(project).getJob(JobId.newBuilder()
                .setProject(project)
                .setLocation(location)
                .setJob(jobId)
                .build()));

        if (job == null) {
            children.cancel(true);
            LOG.warn("BigQuery returned no job for jobId=" + jobId
                    + " project=" + project + " location=" + location
                    + ". A wrong location is the most frequent cause.");
            return null;
        }

        JobStatus status = job.getStatus();
        JobStatistics stats = job.getStatistics();

        String statusStr = computeJobStatus(status);
        String errorMsg = status.getError() != null ? status.getError().getMessage() : null;
        Instant startTime = stats.getStartTime() != null
                ? Instant.ofEpochMilli(stats.getStartTime()) : null;
        Instant endTime = stats.getEndTime() != null
                ? Instant.ofEpochMilli(stats.getEndTime()) : null;

        Long bytesProcessed = null;
        Long bytesBilled = null;
        if (stats instanceof JobStatistics.QueryStatistics qs) {
            bytesProcessed = qs.getTotalBytesProcessed();
            bytesBilled = qs.getTotalBytesBilled();
        }

        String realProject = job.getJobId().getProject() != null
                ? job.getJobId().getProject() : project;
        String realLocation = job.getJobId().getLocation() != null
                ? job.getJobId().getLocation() : location;

        List<BigQueryChildJob> childJobs = joined(children);

        Integer statementsProcessed = childJobs.isEmpty() ? 1 : childJobs.size();

        return new BigQueryJobDetails(
                job.getJobId().getJob(),
                realProject,
                realLocation,
                statusStr, errorMsg,
                bytesProcessed, bytesBilled,
                startTime, endTime,
                statementsProcessed,
                childJobs
        );
    }

    /**
     * The statements a script job ran, listed while the job itself is being read: the listing only
     * needs the job id, so the two requests do not have to wait on each other.
     */
    @NotNull
    private static List<BigQueryChildJob> childJobsOf(@NotNull BigQuery bq, @NotNull String jobId) {
        List<BigQueryChildJob> childJobs = new ArrayList<>();
        Page<Job> pages = bq.listJobs(
                BigQuery.JobListOption.parentJobId(jobId),
                BigQuery.JobListOption.fields(
                        BigQuery.JobField.STATUS,
                        BigQuery.JobField.STATISTICS,
                        BigQuery.JobField.CONFIGURATION
                )
        );
        for (Job child : pages.iterateAll()) {
            JobStatus cs = child.getStatus();
            JobStatistics cStats = child.getStatistics();

            String cStatus = computeJobStatus(cs);
            Instant cStart = cStats.getStartTime() != null
                    ? Instant.ofEpochMilli(cStats.getStartTime()) : null;
            Instant cEnd = cStats.getEndTime() != null
                    ? Instant.ofEpochMilli(cStats.getEndTime()) : null;

            Long cBytes = null;
            if (cStats instanceof JobStatistics.QueryStatistics cqs) {
                cBytes = cqs.getTotalBytesProcessed();
            }

            String cQuery = null;
            if (child.getConfiguration() instanceof QueryJobConfiguration qc) {
                cQuery = qc.getQuery();
            }

            childJobs.add(new BigQueryChildJob(
                    child.getJobId().getJob(),
                    cStatus, cStart, cEnd, cQuery, cBytes
            ));
        }
        return childJobs;
    }

    private static <T> T joined(@NotNull CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw e;
        }
    }

    private static String computeJobStatus(JobStatus status) {
        if (status.getError() != null ||
                (status.getExecutionErrors() != null && !status.getExecutionErrors().isEmpty())) {
            return InvocationActionState.FAILED.name();
        } else if (status.getState() != null) {
            return status.getState().name();
        } else {
            return InvocationActionState.UNKNOWN.name();
        }
    }
}