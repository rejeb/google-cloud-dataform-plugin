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
package io.github.rejeb.dataform.language.gcp.execution.workflow.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public record BigQueryJobDetails(
        @NotNull String jobId,
        @NotNull String project,
        @NotNull String location,
        @NotNull String status,
        @Nullable String errorMessage,
        @Nullable Long bytesProcessed,
        @Nullable Long bytesBilled,
        @Nullable Instant startTime,
        @Nullable Instant endTime,
        @Nullable Integer statementsProcessed,
        @NotNull List<BigQueryChildJob> childJobs,
        @Nullable String reservationId,
        @Nullable Long slotMillis
) {

    /**
     * Details of a job whose slot time is unknown.
     */
    public BigQueryJobDetails(@NotNull String jobId, @NotNull String project, @NotNull String location,
                              @NotNull String status, @Nullable String errorMessage, @Nullable Long bytesProcessed,
                              @Nullable Long bytesBilled, @Nullable Instant startTime, @Nullable Instant endTime,
                              @Nullable Integer statementsProcessed, @NotNull List<BigQueryChildJob> childJobs,
                              @Nullable String reservationId) {
        this(jobId, project, location, status, errorMessage, bytesProcessed, bytesBilled, startTime, endTime,
                statementsProcessed, childJobs, reservationId, null);
    }

    /**
     * A statement of a script job.
     *
     * @param statementType the BigQuery statement type, such as {@code CREATE_SCHEMA} or {@code MERGE}
     * @param target        the table the statement creates or changes, as {@code project.dataset.table}
     * @param errorMessage  why the statement failed
     */
    public record BigQueryChildJob(
            @NotNull String jobId,
            @NotNull String status,
            @Nullable Instant startTime,
            @Nullable Instant endTime,
            @Nullable String query,
            @Nullable Long bytesProcessed,
            @Nullable String statementType,
            @Nullable String target,
            @Nullable String errorMessage
    ) {

        /**
         * A child job whose statement type, target and error are unknown.
         */
        public BigQueryChildJob(@NotNull String jobId, @NotNull String status, @Nullable Instant startTime,
                                @Nullable Instant endTime, @Nullable String query, @Nullable Long bytesProcessed) {
            this(jobId, status, startTime, endTime, query, bytesProcessed, null, null, null);
        }
    }
}