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

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Outcome of a BigQuery dry-run: either the resolved columns or the message BigQuery rejected
 * the query with. A run that resolved nothing without failing carries neither.
 */
public record DryRunResult(@NotNull List<ColumnInfo> columns, @Nullable String errorMessage) {

    /** A run that resolved the given columns. */
    @NotNull
    public static DryRunResult success(@NotNull List<ColumnInfo> columns) {
        return new DryRunResult(columns, null);
    }

    /** A run BigQuery rejected with the given message. */
    @NotNull
    public static DryRunResult failure(@NotNull String errorMessage) {
        return new DryRunResult(List.of(), errorMessage);
    }

    /** A run that produced no schema and no error, for actions carrying no query to send. */
    @NotNull
    public static DryRunResult empty() {
        return new DryRunResult(List.of(), null);
    }

    /** Whether the dry-run failed. */
    public boolean hasError() {
        return errorMessage != null;
    }
}
