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
        @NotNull List<BigQueryChildJob> childJobs
) {
    public record BigQueryChildJob(
            @NotNull String jobId,
            @NotNull String status,
            @Nullable Instant startTime,
            @Nullable Instant endTime,
            @Nullable String query,
            @Nullable Long bytesProcessed
    ) {

        public BigQueryChildJob withQuery(@NotNull String query) {
            return new BigQueryChildJob(
                    jobId, status, startTime, endTime, query, bytesProcessed
            );
        }
    }

    public BigQueryJobDetails withChildJobs(@NotNull List<BigQueryChildJob> childJobs) {
        return new BigQueryJobDetails(
                jobId, project, location, status, errorMessage,
                bytesProcessed, bytesBilled, startTime, endTime,
                statementsProcessed, childJobs
        );
    }
}