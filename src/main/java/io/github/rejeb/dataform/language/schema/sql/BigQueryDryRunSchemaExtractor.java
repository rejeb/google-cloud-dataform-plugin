package io.github.rejeb.dataform.language.schema.sql;

import com.google.cloud.bigquery.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class BigQueryDryRunSchemaExtractor {

    private static final Logger LOG = Logger.getInstance(BigQueryDryRunSchemaExtractor.class);

    @NotNull
    public List<ColumnInfo> extractSchema(@NotNull String projectId,
                                          @Nullable String location,
                                          @NotNull String dryRunQuery) {
        try {
            BigQuery bigQuery = buildBigQueryClient(projectId);

            QueryJobConfiguration config = QueryJobConfiguration.newBuilder(dryRunQuery)
                    .setDryRun(true)
                    .setUseLegacySql(false)
                    .build();

            JobId jobId = location != null && !location.isBlank()
                    ? JobId.newBuilder().setLocation(location).build()
                    : JobId.of();

            Job dryRunJob = bigQuery.create(JobInfo.newBuilder(config).setJobId(jobId).build());

            Schema schema = extractSchemaFromJob(dryRunJob);
            if (schema == null || schema.getFields() == null) {
                LOG.debug("BigQuery dry-run returned no schema for project: " + projectId);
                return Collections.emptyList();
            }

            return schema.getFields().stream()
                    .map(BigQueryDryRunSchemaExtractor::fieldToColumnInfo)
                    .toList();

        } catch (BigQueryException e) {
            LOG.warn("BigQuery dry-run [" + dryRunQuery + "] failed: [" + e.getCode() + "] " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.warn("Unexpected error during BigQuery dry-run", e);
            return Collections.emptyList();
        }
    }


    @NotNull
    private static BigQuery buildBigQueryClient(@NotNull String projectId) {
        return BigQueryOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }

    @Nullable
    private static Schema extractSchemaFromJob(@NotNull Job job) {
        JobStatistics statistics = job.getStatistics();
        if (!(statistics instanceof JobStatistics.QueryStatistics queryStats)) {
            return null;
        }
        return queryStats.getSchema();
    }

    @NotNull
    private static ColumnInfo fieldToColumnInfo(@NotNull Field field) {
        String type = field.getType().getStandardType().name();
        String mode = Optional.ofNullable(field.getMode())
                .map(Field.Mode::name)
                .orElse("NULLABLE");

        List<ColumnInfo> subFields = Collections.emptyList();
        if (field.getType() == LegacySQLTypeName.RECORD
                && field.getSubFields() != null
                && !field.getSubFields().isEmpty()) {
            subFields = field.getSubFields().stream()
                    .map(BigQueryDryRunSchemaExtractor::fieldToColumnInfo)
                    .toList();
        }

        return new ColumnInfo(field.getName(), type, mode, subFields);
    }
}
