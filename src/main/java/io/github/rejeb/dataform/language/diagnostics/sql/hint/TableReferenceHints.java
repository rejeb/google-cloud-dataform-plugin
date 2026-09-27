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

import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryError;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tables BigQuery could not find. A table of the project is read through {@code ${ref()}}, which
 * both fills in its dataset and makes Dataform build it first, so that is what is offered.
 */
final class TableReferenceHints implements SqlHintProvider {

    @Override
    public @NotNull Set<BigQueryErrorKind> kinds() {
        return Set.of(BigQueryErrorKind.TABLE_NOT_FOUND, BigQueryErrorKind.MISSING_DATASET);
    }

    @Override
    public @NotNull SqlHint hint(@NotNull SqlErrorContext context) {
        BigQueryError error = context.error();
        String subject = error.subject();
        if (subject == null) return SqlHint.NONE;
        if (context.rangeText().startsWith("${")) {
            return new SqlHint("The table this ref() names does not exist in BigQuery yet: run its action, "
                    + "or fix its own dry-run first.", List.of());
        }
        String table = subject.substring(subject.lastIndexOf('.') + 1);
        Optional<String> action = context.scope().actionNames().stream()
                .filter(name -> name.equalsIgnoreCase(table))
                .findFirst();
        if (action.isPresent()) {
            return new SqlHint("'" + action.get() + "' is built by this project: reference it with ${ref(\""
                    + action.get() + "\")} so that Dataform builds it first and fills in its dataset.",
                    List.of(refFix(context, action.get())));
        }
        List<String> close = NameSuggester.closest(table, context.scope().actionNames(), 2);
        if (!close.isEmpty()) {
            return new SqlHint("Did you mean " + close.stream().map(name -> "${ref(\"" + name + "\")}")
                    .collect(Collectors.joining(" or ")) + "?",
                    close.stream().map(name -> refFix(context, name)).toList());
        }
        if (error.kind() == BigQueryErrorKind.MISSING_DATASET) {
            return new SqlHint("Qualify the table with its dataset (dataset.table), or reference an action "
                    + "of this project with ${ref(\"name\")}.", List.of());
        }
        String location = error.qualifier() == null ? "" : " in location " + error.qualifier();
        return new SqlHint("The table does not exist" + location + ": check its dataset and name, or run the "
                + "action that creates it.", List.of());
    }

    private static @NotNull SqlFix refFix(@NotNull SqlErrorContext context, @NotNull String action) {
        String ref = "${ref(\"" + action + "\")}";
        return new SqlFix("Replace with " + ref, context.range(), ref);
    }
}
