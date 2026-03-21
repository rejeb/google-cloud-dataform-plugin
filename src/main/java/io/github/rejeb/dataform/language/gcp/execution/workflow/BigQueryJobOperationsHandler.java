/*
 * Licensed under the Apache License, Version 2.0
 */
package io.github.rejeb.dataform.language.gcp.execution.workflow;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.repository.BigQueryJobRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BigQueryJobOperationsHandler implements BigQueryJobOperations {

    private final BigQueryJobRepository repository;

    public BigQueryJobOperationsHandler(@NotNull BigQueryJobRepository repository) {
        this.repository = repository;
    }

    @Override
    @Nullable
    public BigQueryJobDetails getJobDetails(
            @NotNull String jobId,
            @NotNull String project,
            @NotNull String location
    ) {
        return repository.getJobDetails(jobId, project, location);
    }
}