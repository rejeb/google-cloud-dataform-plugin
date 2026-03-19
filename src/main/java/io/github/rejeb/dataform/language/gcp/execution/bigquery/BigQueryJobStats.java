/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable statistics for a completed BigQuery job.
 *
 * @param jobId            BigQuery job ID
 * @param projectId        GCP project ID
 * @param location         Job location (e.g. "EU", "US")
 * @param creationTime     Job creation timestamp in ms
 * @param startTime        Job start timestamp in ms
 * @param endTime          Job end timestamp in ms
 * @param bytesProcessed   Total bytes billed
 * @param cacheHit         Whether results were served from cache
 * @param statementType    SQL statement type (e.g. "SELECT")
 * @param totalRows        Tolal row count
 */
public record BigQueryJobStats(
        @NotNull String jobId,
        @NotNull String projectId,
        @Nullable String location,
        long creationTime,
        long startTime,
        long endTime,
        long bytesProcessed,
        boolean cacheHit,
        @Nullable String statementType,
        long totalRows
) {}
