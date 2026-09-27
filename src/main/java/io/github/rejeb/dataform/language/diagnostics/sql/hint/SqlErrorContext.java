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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import com.intellij.openapi.util.TextRange;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryError;
import org.jetbrains.annotations.NotNull;

/**
 * A SQL error placed in a SQLX file: what it says, the host range it covers, the text of the file
 * and what the query can read there.
 */
public record SqlErrorContext(@NotNull BigQueryError error,
                              @NotNull TextRange range,
                              @NotNull CharSequence text,
                              @NotNull SqlScope scope) {

    /**
     * The text the error covers.
     */
    public @NotNull String rangeText() {
        return range.subSequence(text).toString();
    }
}
