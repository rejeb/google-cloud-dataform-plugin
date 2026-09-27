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
package io.github.rejeb.dataform.language.validation;

import com.intellij.openapi.util.TextRange;
import io.github.rejeb.dataform.language.diagnostics.sql.hint.SqlFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A validation problem anchored to an exact range of the host document, with what can be said
 * and done about fixing it.
 */
public record SqlxValidationProblem(@NotNull TextRange range,
                                    @NotNull String message,
                                    @NotNull Kind kind,
                                    @NotNull Severity severity,
                                    @Nullable String hint,
                                    @NotNull List<SqlFix> fixes) {

    public SqlxValidationProblem {
        fixes = List.copyOf(fixes);
    }

    public SqlxValidationProblem(@NotNull TextRange range, @NotNull String message, @NotNull Kind kind) {
        this(range, message, kind, Severity.WEAK_WARNING, null, List.of());
    }

    /**
     * An error BigQuery answered the dry-run of the file's query with, placed in the file.
     */
    public static @NotNull SqlxValidationProblem bigQueryError(@NotNull TextRange range,
                                                               @NotNull String message,
                                                               @Nullable String hint,
                                                               @NotNull List<SqlFix> fixes) {
        return new SqlxValidationProblem(range, message, Kind.BIGQUERY_ERROR, Severity.ERROR, hint, fixes);
    }

    /**
     * An error the Dataform compiler reported for the file, placed in it.
     */
    public static @NotNull SqlxValidationProblem compilationError(@NotNull TextRange range,
                                                                  @NotNull String message,
                                                                  @Nullable String hint,
                                                                  @NotNull List<SqlFix> fixes) {
        return new SqlxValidationProblem(range, message, Kind.COMPILATION_ERROR, Severity.ERROR, hint, fixes);
    }

    /**
     * The message followed by the hint, as the problem reads in a chip and in the problem list.
     */
    public @NotNull String chipText() {
        return hint == null ? message : message + " → " + hint;
    }

    /**
     * The category of problem, used only for grouping and tests.
     */
    public enum Kind {
        UNRESOLVED_REFERENCE,
        UNKNOWN_CONFIG_KEY,
        INVALID_CONFIG_VALUE,
        BIGQUERY_ERROR,
        COMPILATION_ERROR
    }

    /**
     * How strongly the problem is painted.
     */
    public enum Severity {
        WEAK_WARNING,
        ERROR
    }
}
