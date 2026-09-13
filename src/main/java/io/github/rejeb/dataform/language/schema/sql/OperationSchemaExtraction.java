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

import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.compilation.model.Target;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reads the schema of an operation that declares an output.
 *
 * <p>An operation with {@code hasOutput: true} promises a table under the name {@code self()}
 * gives it, and that table is what other actions {@code ref()}. Its statements are free-form —
 * DDL, a {@code MERGE}, an {@code UPDATE} — and the last of them need not produce rows at all, so
 * the schema is read from the table itself, the way a declaration's is. Only when the table has
 * not been created yet is the last statement tried, which serves an operation ending in a
 * {@code SELECT}.</p>
 *
 * <p>An operation that yields no schema either way is reported as a failure carrying the reason,
 * so the views asking why a table is unresolved have something to say.</p>
 */
final class OperationSchemaExtraction {

    static final String NO_RESULT_SET =
            "The operation's last statement produces no rows to read a schema from";

    private OperationSchemaExtraction() {
    }

    /** The dry-run query reading the schema of an existing table. */
    static @NotNull String tableQuery(@NotNull String fullName) {
        return "SELECT * FROM `" + fullName + "` LIMIT 0";
    }

    static @NotNull DryRunResult extract(@NotNull CompiledOperation operation,
                                         @NotNull ExtractionContext ctx) {
        DryRunResult fromTable = fromTable(operation.getTarget(), ctx);
        if (fromTable != null && !fromTable.columns().isEmpty()) return fromTable;

        DryRunResult fromQuery = fromLastQuery(operation.getQueries(), ctx);
        if (fromQuery != null && !fromQuery.columns().isEmpty()) return fromQuery;

        if (fromTable != null && fromTable.hasError()) return fromTable;
        if (fromQuery != null && fromQuery.hasError()) return fromQuery;
        return DryRunResult.failure(NO_RESULT_SET);
    }

    private static @Nullable DryRunResult fromTable(@Nullable Target target,
                                                    @NotNull ExtractionContext ctx) {
        if (target == null || target.getFullName() == null) return null;
        return run(ctx, tableQuery(target.getFullName()));
    }

    private static @Nullable DryRunResult fromLastQuery(@NotNull List<String> queries,
                                                        @NotNull ExtractionContext ctx) {
        if (queries.isEmpty()) return null;
        String last = queries.getLast();
        if (last == null || last.isBlank()) return null;
        return run(ctx, last);
    }

    private static @NotNull DryRunResult run(@NotNull ExtractionContext ctx, @NotNull String query) {
        return ctx.extractor().extractSchema(ctx.projectId(), ctx.location(), query);
    }
}
