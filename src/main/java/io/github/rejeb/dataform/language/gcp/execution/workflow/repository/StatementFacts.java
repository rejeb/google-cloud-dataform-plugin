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

import com.google.cloud.bigquery.TableId;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * What the statements of a script job tell about themselves, read from the values BigQuery returns
 * for its child jobs.
 */
final class StatementFacts {

    private static final Set<String> DML = Set.of("INSERT", "UPDATE", "DELETE", "MERGE");

    private static final Comparator<BigQueryChildJob> RUN_ORDER = Comparator
            .comparing(BigQueryChildJob::startTime, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(BigQueryChildJob::jobId);

    private StatementFacts() {
    }

    /**
     * The table a statement creates or changes, as {@code project.dataset.table}: the DDL target
     * when BigQuery gives one, else the destination of an INSERT, UPDATE, DELETE or MERGE. Other
     * statements have none, a SELECT's destination being BigQuery's temporary table.
     */
    @Nullable
    static String target(@Nullable String statementType, @Nullable TableId ddlTarget, @Nullable TableId destination) {
        if (ddlTarget != null) return name(ddlTarget);
        if (destination != null && statementType != null && DML.contains(statementType)) return name(destination);
        return null;
    }

    /**
     * The child jobs in the order they ran: BigQuery lists the most recent first. Statements that
     * never started come last; ties keep the job id order.
     */
    @NotNull
    static List<BigQueryChildJob> inRunOrder(@NotNull List<BigQueryChildJob> jobs) {
        return jobs.stream().sorted(RUN_ORDER).toList();
    }

    @NotNull
    private static String name(@NotNull TableId table) {
        String datasetAndTable = table.getDataset() + "." + table.getTable();
        return table.getProject() != null ? table.getProject() + "." + datasetAndTable : datasetAndTable;
    }
}
