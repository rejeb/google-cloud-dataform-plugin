/*
 * Licensed under the Apache License, Version 2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.*;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class GcpBigQueryJobRepository implements BigQueryJobRepository {

    @Override
    @Nullable
    public BigQueryJobDetails getJobDetails(
            @NotNull String jobId,
            @NotNull String project,
            @Nullable String location
    ) {
        BigQuery bq = BigQueryOptions.newBuilder()
                .setProjectId(project)
                .build()
                .getService();

        Job job = bq.getJob(JobId.newBuilder()
                .setProject(project)
                .setLocation(location)
                .setJob(jobId)
                .build());

        if (job == null) return null;

        JobStatus status = job.getStatus();
        JobStatistics stats = job.getStatistics();

        String statusStr = computeJobStatus(status);
        String errorMsg = status.getError() != null ? status.getError().getMessage() : null;
        Instant startTime = stats.getStartTime() != null
                ? Instant.ofEpochMilli(stats.getStartTime()) : null;
        Instant endTime = stats.getEndTime() != null
                ? Instant.ofEpochMilli(stats.getEndTime()) : null;

        // bytes uniquement disponibles dans QueryStatistics
        Long bytesProcessed = null;
        Long bytesBilled = null;
        if (stats instanceof JobStatistics.QueryStatistics qs) {
            bytesProcessed = qs.getTotalBytesProcessed();
            bytesBilled = qs.getTotalBytesBilled();
        }

        // project et location réels depuis le job (pas depuis l'invocationName)
        String realProject = job.getJobId().getProject() != null
                ? job.getJobId().getProject() : project;
        String realLocation = job.getJobId().getLocation() != null
                ? job.getJobId().getLocation() : location;

        // Child jobs
        List<BigQueryChildJob> childJobs = new ArrayList<>();
        Page<Job> children = bq.listJobs(
                BigQuery.JobListOption.parentJobId(jobId),
                BigQuery.JobListOption.fields(
                        BigQuery.JobField.STATUS,
                        BigQuery.JobField.STATISTICS,
                        BigQuery.JobField.CONFIGURATION
                )
        );
        for (Job child : children.iterateAll()) {
            JobStatus cs = child.getStatus();
            JobStatistics cStats = child.getStatistics();

            String cStatus = computeJobStatus(cs);
            Instant cStart = cStats.getStartTime() != null
                    ? Instant.ofEpochMilli(cStats.getStartTime()) : null;
            Instant cEnd = cStats.getEndTime() != null
                    ? Instant.ofEpochMilli(cStats.getEndTime()) : null;

            // bytes child job
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

    private String computeJobStatus(JobStatus status) {
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