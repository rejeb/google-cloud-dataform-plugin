/*
 * Licensed under the Apache License, Version 2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Low-level contract for fetching BigQuery job details.
 */
public interface BigQueryJobRepository {

    /**
     * Fetches job details and child jobs. Must be called off the EDT.
     *
     * @return null if the job does not exist
     */
    @Nullable
    BigQueryJobDetails getJobDetails(
            @NotNull String jobId,
            @NotNull String project,
            @Nullable String location
    );
}