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
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TableReferenceHintsTest {

    private static final SqlScope ACTIONS =
            new FakeScope(Map.of(), List.of(), List.of("orders", "customers"), -1, List.of());

    private static SqlHint hint(String message, String sql, String token, SqlScope scope) {
        return SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse(message),
                TextRange.from(sql.indexOf(token), token.length()), sql, scope));
    }

    @Test
    public void aHandWrittenNameOfAProjectTableIsReplacedByItsRef() {
        SqlHint hint = hint("Not found: Table proj:ds.orders was not found in location US",
                "SELECT 1 FROM proj.ds.orders", "proj.ds.orders", ACTIONS);

        assertEquals("'orders' is built by this project: reference it with ${ref(\"orders\")} so that Dataform "
                + "builds it first and fills in its dataset.", hint.text());
        assertEquals(List.of(new SqlFix("Replace with ${ref(\"orders\")}", new TextRange(14, 28), "${ref(\"orders\")}")),
                hint.fixes());
    }

    @Test
    public void aMisspelledTableSuggestsTheClosestAction() {
        SqlHint hint = hint("Not found: Table proj:ds.orderz was not found in location US",
                "SELECT 1 FROM proj.ds.orderz", "proj.ds.orderz", ACTIONS);

        assertEquals("Did you mean ${ref(\"orders\")}?", hint.text());
        assertEquals("Replace with ${ref(\"orders\")}", hint.fixes().getFirst().label());
    }

    @Test
    public void aRefWhoseTableIsMissingPointsAtTheActionBehindIt() {
        SqlHint hint = hint("Not found: Table proj:ds.orders was not found in location US",
                "SELECT 1 FROM ${ref(\"orders\")}", "${ref(\"orders\")}", ACTIONS);

        assertEquals("The table this ref() names does not exist in BigQuery yet: run its action, or fix its own "
                + "dry-run first.", hint.text());
        assertEquals(List.of(), hint.fixes());
    }

    @Test
    public void anUnknownTableGetsAGeneralHint() {
        SqlHint hint = hint("Not found: Table p:d.zzz was not found in location EU", "SELECT 1 FROM p.d.zzz", "p.d.zzz", ACTIONS);

        assertEquals("The table does not exist in location EU: check its dataset and name, or run the action "
                + "that creates it.", hint.text());
    }

    @Test
    public void aTableWithoutDatasetIsReplacedByItsRef() {
        SqlHint hint = hint("Table name \"orders\" missing dataset while no default dataset is set in the request.",
                "SELECT 1 FROM orders", "orders", ACTIONS);

        assertEquals("Replace with ${ref(\"orders\")}", hint.fixes().getFirst().label());
    }
}
