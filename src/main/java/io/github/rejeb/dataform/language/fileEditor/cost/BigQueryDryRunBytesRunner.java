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

import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobStatistics;
import com.google.cloud.bigquery.JobStatistics.QueryStatistics.StatementType;
import com.google.cloud.bigquery.TableId;
import com.intellij.openapi.diagnostic.Logger;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpCalls;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryDryRun;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dry-runs the raw compiled query of an action, unlike the schema dry-run which replaces its
 * dependencies with stubs. A script is reported as not evaluated.
 */
public final class BigQueryDryRunBytesRunner implements DryRunBytesRunner {

    private static final Logger LOG = Logger.getInstance(BigQueryDryRunBytesRunner.class);

    @Override
    public @NotNull CostEstimate run(@NotNull String billingProject, @Nullable String location, @NotNull String sql) {
        try {
            return GcpCalls.execute(AuthTrigger.BACKGROUND, () -> dryRun(billingProject, location, sql));
        } catch (Exception e) {
            String message = BigQueryDryRun.messageOf(e);
            LOG.info("BigQuery cost dry-run failed: " + message);
            return CostEstimate.failure(message);
        }
    }

    @NotNull
    private static CostEstimate dryRun(@NotNull String billingProject, @Nullable String location, @NotNull String sql) {
        Job job = BigQueryDryRun.run(billingProject, location, sql);
        JobStatistics.QueryStatistics queryStats =
                job.getStatistics() instanceof JobStatistics.QueryStatistics stats ? stats : null;
        String jobLocation = job.getJobId() != null ? job.getJobId().getLocation() : null;
        return estimateOf(queryStats != null ? queryStats.getTotalBytesProcessed() : null,
                queryStats != null ? queryStats.getStatementType() : null,
                queryStats != null ? queryStats.getReferencedTables() : null,
                jobLocation != null && !jobLocation.isBlank() ? jobLocation : location);
    }

    /**
     * The estimate a dry-run gives: its bytes, with the minimum BigQuery bills for the tables read.
     * The bytes of a script are no estimate, since a dry-run runs none of its statements.
     */
    @NotNull
    static CostEstimate estimateOf(@Nullable Long bytesProcessed, @Nullable StatementType statementType,
                                   @Nullable List<TableId> referencedTables, @Nullable String location) {
        if (StatementType.SCRIPT.equals(statementType)) {
            return CostEstimate.unevaluated(location);
        }
        return CostEstimate.success(bytesProcessed == null ? 0L : bytesProcessed, location,
                minimumBilledBytes(referencedTables));
    }

    /**
     * The least BigQuery bills for a query: its 10 MB minimum for each table the query reads.
     */
    static long minimumBilledBytes(@Nullable List<TableId> referencedTables) {
        return referencedTables == null ? 0 : referencedTables.size() * CostEstimate.MINIMUM_BILLED_BYTES;
    }
}
