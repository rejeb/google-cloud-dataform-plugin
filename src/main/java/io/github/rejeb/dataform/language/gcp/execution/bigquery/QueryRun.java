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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A BigQuery query run from the SQLX preview, listed in the Services view from its start.
 *
 * @param tableName the Dataform target the query compiles, {@code project.dataset.table}
 * @param sql       the SQL sent to BigQuery
 * @param source    the SQLX file the query comes from, if any
 * @param createdAt when the run started, in milliseconds
 * @param state     where the run stands
 * @param result    the BigQuery result once the run finished
 * @param cancel    cancels the run while it is running
 */
public record QueryRun(
        @NotNull String tableName,
        @NotNull String sql,
        @Nullable VirtualFile source,
        long createdAt,
        @NotNull QueryRunState state,
        @Nullable BigQueryJobResult result,
        @Nullable Runnable cancel
) {

    /**
     * A run that has just started.
     */
    public static @NotNull QueryRun running(@NotNull String tableName, @NotNull String sql,
                                            @Nullable VirtualFile source, long createdAt,
                                            @Nullable Runnable cancel) {
        return new QueryRun(tableName, sql, source, createdAt, QueryRunState.RUNNING, null, cancel);
    }

    /**
     * This run finished with the given result; a cancelled run is cancelled whatever its result.
     */
    public @NotNull QueryRun finished(@NotNull BigQueryJobResult result, boolean cancelled) {
        QueryRunState finalState = cancelled ? QueryRunState.CANCELLED
                : result.isSuccess() ? QueryRunState.SUCCEEDED : QueryRunState.FAILED;
        return new QueryRun(tableName, sql, source, createdAt, finalState, result, null);
    }

    /**
     * Whether the run is over, whatever its outcome.
     */
    public boolean isFinished() {
        return state != QueryRunState.RUNNING;
    }

    /**
     * The table name without its project and dataset.
     */
    public @NotNull String shortName() {
        int dot = tableName.lastIndexOf('.');
        return dot < 0 ? tableName : tableName.substring(dot + 1);
    }
}
