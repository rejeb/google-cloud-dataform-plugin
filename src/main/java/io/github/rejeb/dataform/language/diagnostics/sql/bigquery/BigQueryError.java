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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A BigQuery error message, taken apart.
 *
 * @param kind       what the error is about
 * @param message    the message without its position and without BigQuery's own suggestion
 * @param subject    the name, keyword or token the message is about, unquoted
 * @param qualifier  a second part the message gives: the alias a name was looked for in, the type
 *                   a field was looked for in, the clause a function is not allowed in, the
 *                   location a table was looked for in, the token that was expected, or the
 *                   signatures a function supports
 * @param suggestion the name BigQuery suggests with "Did you mean"
 * @param line       the one-based line of the error in the query sent, 0 when not given
 * @param column     the one-based column of the error in the query sent, 0 when not given
 */
public record BigQueryError(@NotNull BigQueryErrorKind kind,
                            @NotNull String message,
                            @Nullable String subject,
                            @Nullable String qualifier,
                            @Nullable String suggestion,
                            int line,
                            int column) {

    /**
     * Whether BigQuery said where in the query the error is.
     */
    public boolean hasPosition() {
        return line > 0 && column > 0;
    }
}
