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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of a BigQuery query execution.
 *
 * @param tableName  Dataform table name (used as node label in Services view)
 * @param stats      Job statistics
 * @param pagedResult    Result rows
 * @param errorMessage Non-null if execution failed
 */
public record BigQueryJobResult(
        @NotNull String tableName,
        @Nullable BigQueryJobStats stats,
        @Nullable BigQueryPagedResult pagedResult,
        @Nullable String errorMessage
) {
    /** @return true if the execution completed without error */
    public boolean isSuccess() {
        return errorMessage == null;
    }
}
