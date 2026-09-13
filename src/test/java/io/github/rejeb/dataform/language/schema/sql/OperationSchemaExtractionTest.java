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

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledOperation;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An operation declaring an output is read from the table it builds, not from whatever statement
 * it happens to end with.
 */
class OperationSchemaExtractionTest {

    private static final String FQN = "proj.silver.merge_customer_history";
    private static final String TABLE_QUERY = "SELECT * FROM `" + FQN + "` LIMIT 0";
    private static final String UPDATE = "UPDATE `" + FQN + "` SET is_current = FALSE WHERE TRUE";
    private static final String SELECT = "SELECT 1 AS one";

    private static final List<ColumnInfo> TABLE_COLUMNS =
            List.of(new ColumnInfo("customer_key", "STRING", "NULLABLE", null));
    private static final List<ColumnInfo> SELECT_COLUMNS =
            List.of(new ColumnInfo("one", "INT64", "NULLABLE", null));

    private final List<String> sent = new ArrayList<>();

    private ExtractionContext context(Function<String, DryRunResult> answers) {
        return new ExtractionContext("proj", "EU", (projectId, location, query) -> {
            sent.add(query);
            return answers.apply(query);
        });
    }

    private static CompiledOperation operation(String... queries) {
        StringBuilder json = new StringBuilder("{\"target\":{\"database\":\"proj\",")
                .append("\"schema\":\"silver\",\"name\":\"merge_customer_history\"},")
                .append("\"hasOutput\":true,\"queries\":[");
        for (int i = 0; i < queries.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(queries[i].replace("\"", "\\\"")).append('"');
        }
        json.append("]}");
        return new Gson().fromJson(json.toString(), CompiledOperation.class);
    }

    @Test
    void theOutputTableIsReadBeforeAnyStatement() {
        DryRunResult result = OperationSchemaExtraction.extract(operation("CREATE TABLE x", UPDATE),
                context(query -> query.equals(TABLE_QUERY)
                        ? DryRunResult.success(TABLE_COLUMNS)
                        : DryRunResult.success(List.of())));

        assertEquals(TABLE_COLUMNS, result.columns());
        assertEquals(List.of(TABLE_QUERY), sent,
                "a table that exists answers on its own, the statements are never sent");
    }

    @Test
    void theLastStatementIsTriedWhenTheTableDoesNotExistYet() {
        DryRunResult result = OperationSchemaExtraction.extract(operation("CREATE TABLE x", SELECT),
                context(query -> query.equals(TABLE_QUERY)
                        ? DryRunResult.failure("Not found: Table " + FQN)
                        : DryRunResult.success(SELECT_COLUMNS)));

        assertEquals(SELECT_COLUMNS, result.columns());
        assertFalse(result.hasError());
        assertEquals(List.of(TABLE_QUERY, SELECT), sent);
    }

    @Test
    void aMissingTableBehindAStatementWithoutRowsIsReportedAsTheReason() {
        DryRunResult result = OperationSchemaExtraction.extract(operation(UPDATE),
                context(query -> query.equals(TABLE_QUERY)
                        ? DryRunResult.failure("Not found: Table " + FQN)
                        : DryRunResult.success(List.of())));

        assertTrue(result.columns().isEmpty());
        assertTrue(result.hasError());
        assertEquals("Not found: Table " + FQN, result.errorMessage());
    }

    @Test
    void nothingToReadFromIsAnErrorRatherThanSilence() {
        DryRunResult result = OperationSchemaExtraction.extract(operation(UPDATE),
                context(query -> DryRunResult.success(List.of())));

        assertTrue(result.hasError(), "an unresolved table must carry a reason");
        assertEquals(OperationSchemaExtraction.NO_RESULT_SET, result.errorMessage());
    }

    @Test
    void anOperationWithoutStatementsStillReadsItsTable() {
        DryRunResult result = OperationSchemaExtraction.extract(operation(),
                context(query -> DryRunResult.success(TABLE_COLUMNS)));

        assertEquals(TABLE_COLUMNS, result.columns());
        assertEquals(List.of(TABLE_QUERY), sent);
    }
}
