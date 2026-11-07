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
package io.github.rejeb.dataform.language.fileEditor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The SQL of an action as the query view shows it.
 *
 * @param type                   the Dataform type of the action, such as {@code table}, or {@code null}
 * @param preOpsCount            the number of pre-operation statements
 * @param incrementalPreOpsCount the number of incremental pre-operation statements
 * @param postOpsCount           the number of post-operation statements
 * @param assertions             the assertions the config of the action declares, shown in its section
 */
record FormattedCompiledQuery(
        @NotNull String tableName,
        @Nullable String preOps,
        @Nullable String incrementalPreOps,
        @Nullable String query,
        @Nullable String postOps,
        @Nullable String compilationErrors,
        @Nullable String type,
        int preOpsCount,
        int incrementalPreOpsCount,
        int postOpsCount,
        @NotNull List<FormattedCompiledQuery> assertions
) {

    FormattedCompiledQuery(@NotNull String tableName, @Nullable String preOps, @Nullable String incrementalPreOps,
                           @Nullable String query, @Nullable String postOps, @Nullable String compilationErrors,
                           @Nullable String type, int preOpsCount, int incrementalPreOpsCount, int postOpsCount) {
        this(tableName, preOps, incrementalPreOps, query, postOps, compilationErrors, type,
                preOpsCount, incrementalPreOpsCount, postOpsCount, List.of());
    }

    FormattedCompiledQuery(@NotNull String tableName, @Nullable String preOps, @Nullable String incrementalPreOps,
                           @Nullable String query, @Nullable String postOps, @Nullable String compilationErrors) {
        this(tableName, preOps, incrementalPreOps, query, postOps, compilationErrors, null,
                countOf(preOps), countOf(incrementalPreOps), countOf(postOps));
    }

    @NotNull
    FormattedCompiledQuery withAssertions(@NotNull List<FormattedCompiledQuery> assertions) {
        return new FormattedCompiledQuery(tableName, preOps, incrementalPreOps, query, postOps, compilationErrors,
                type, preOpsCount, incrementalPreOpsCount, postOpsCount, List.copyOf(assertions));
    }

    private static int countOf(@Nullable String sql) {
        return sql == null || sql.isBlank() ? 0 : 1;
    }
}
