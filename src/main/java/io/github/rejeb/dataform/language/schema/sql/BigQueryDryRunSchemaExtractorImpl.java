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
package io.github.rejeb.dataform.language.schema.sql;

import com.google.cloud.bigquery.*;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

public final class BigQueryDryRunSchemaExtractorImpl implements BigQueryDryRunSchemaExtractor {

    private static final Logger LOG = Logger.getInstance(BigQueryDryRunSchemaExtractorImpl.class);

    @NotNull
    public List<ColumnInfo> extractSchema(@NotNull String projectId,
                                          @Nullable String location,
                                          @NotNull String dryRunQuery) {
        try {
            BigQuery bigQuery = GcpClientsUtils.bigQuery(projectId);

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

            List<ColumnInfo> columns = new ArrayList<>(schema.getFields().stream()
                    .map(BigQueryDryRunSchemaExtractorImpl::fieldToColumnInfo)
                    .toList());

            addPartitionColumnIfMissing(dryRunJob, columns);

            return columns;

        } catch (BigQueryException e) {
            LOG.warn("BigQuery dry-run [" + dryRunQuery + "] failed: [" + e.getCode() + "] " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.warn("Unexpected error during BigQuery dry-run", e);
            return Collections.emptyList();
        }
    }

    /**
     * Checks whether the dry-run job's destination table has time partitioning and,
     * if the partition column is not already present in the schema, adds it.
     * <p>
     * This covers two cases:
     * <ul>
     *   <li>Ingestion-time partitioning: the pseudo-column {@code _PARTITIONTIME} is never
     *       included in {@code QueryStatistics.getSchema()}, so we add it explicitly.</li>
     *   <li>Column-based partitioning: the column is normally already in the schema,
     *       but this method acts as a safety net in case it's absent.</li>
     * </ul>
     */
    private static void addPartitionColumnIfMissing(@NotNull Job job,
                                                    @NotNull List<ColumnInfo> columns) {
        try {
            JobConfiguration jobConfig = job.getConfiguration();
            if (!(jobConfig instanceof QueryJobConfiguration queryConfig)) return;

            TimePartitioning timePartitioning = queryConfig.getTimePartitioning();
            if (timePartitioning == null) return;

            // If field is null, it's ingestion-time partitioning → pseudo-column _PARTITIONTIME
            String partitionField = timePartitioning.getField();
            if (partitionField == null || partitionField.isBlank()) {
                partitionField = "_PARTITIONTIME";
            }

            String finalField = partitionField;
            boolean alreadyPresent = columns.stream()
                    .anyMatch(c -> c.name().equalsIgnoreCase(finalField));

            if (!alreadyPresent) {
                String type = resolvePartitionColumnType(partitionField);
                columns.add(new ColumnInfo(finalField, type, "NULLABLE", "Partitioning column"));
            }
        } catch (Exception e) {
            LOG.debug("Could not extract partitioning info from dry-run job: " + e.getMessage());
        }
    }

    @NotNull
    private static String resolvePartitionColumnType(@NotNull String partitionField) {
        if ("_PARTITIONTIME".equalsIgnoreCase(partitionField)) return "TIMESTAMP";
        if ("_PARTITIONDATE".equalsIgnoreCase(partitionField)) return "DATE";
        return "TIMESTAMP";
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
        String description = field.getDescription();
        List<ColumnInfo> subFields = Collections.emptyList();
        if (field.getType() == LegacySQLTypeName.RECORD
                && field.getSubFields() != null
                && !field.getSubFields().isEmpty()) {
            subFields = field.getSubFields().stream()
                    .map(BigQueryDryRunSchemaExtractorImpl::fieldToColumnInfo)
                    .toList();
        }

        return new ColumnInfo(field.getName(), type, mode, description, subFields);
    }
}
