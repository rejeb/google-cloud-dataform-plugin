/*
 * Licensed under the Apache License, Version 2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Business-level contract for fetching BigQuery job details.
 * Must be called off the EDT.
 */
public interface BigQueryJobOperations {

    @Nullable
    BigQueryJobDetails getJobDetails(
            @NotNull String jobId,
            @NotNull String project,
            @NotNull String location
    );
}