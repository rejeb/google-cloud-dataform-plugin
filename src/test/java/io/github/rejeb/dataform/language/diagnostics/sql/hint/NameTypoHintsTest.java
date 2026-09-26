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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NameTypoHintsTest {

    static SqlErrorContext context(String message, String text, String token, SqlScope scope) {
        int start = text.indexOf(token);
        return new SqlErrorContext(BigQueryErrorParser.parse(message), TextRange.from(start, token.length()), text, scope);
    }

    @Test
    public void bigQueryOwnSuggestionComesFirstAndIsAFix() {
        SqlHint hint = SqlHints.hintFor(context("Unrecognized name: custmer_id; Did you mean customer_id? at [1:8]",
                "SELECT custmer_id FROM o", "custmer_id",
                FakeScope.columns(Map.of("o", List.of("customer_id", "order_id")))));

        assertEquals("Did you mean 'customer_id'?", hint.text());
        assertEquals(List.of(new SqlFix("Replace with 'customer_id'", new TextRange(7, 17), "customer_id")), hint.fixes());
    }

    @Test
    public void withoutASuggestionTheClosestColumnsInScopeAreOffered() {
        SqlHint hint = SqlHints.hintFor(context("Unrecognized name: ordr_id at [1:8]", "SELECT ordr_id FROM o", "ordr_id",
                FakeScope.columns(Map.of("o", List.of("order_id", "customer_id", "order_ts")))));

        assertEquals("Did you mean 'order_id'?", hint.text());
    }

    @Test
    public void aNameReadFromASourceIsLookedForInThatSource() {
        SqlHint hint = SqlHints.hintFor(context("Name order_tss not found inside o at [1:10]",
                "SELECT o.order_tss FROM t o", "order_tss",
                FakeScope.columns(Map.of("o", List.of("order_ts", "order_id"), "c", List.of("order_tsx")))));

        assertEquals("Did you mean 'order_ts'?", hint.text());
        assertEquals(new TextRange(9, 18), hint.fixes().getFirst().range());
    }

    @Test
    public void aMissingStructFieldIsLookedForInTheStruct() {
        SqlHint hint = SqlHints.hintFor(context("Field name cty does not exist in STRUCT<city STRING, zip STRING> at [1:16]",
                "SELECT address.cty FROM t", "cty", SqlScope.EMPTY));

        assertEquals("Did you mean 'city'?", hint.text());
    }

    @Test
    public void anUnknownFunctionIsLookedForAmongBigQueryFunctions() {
        SqlHint hint = SqlHints.hintFor(context("Function not found: DATE_DIF at [1:8]", "SELECT DATE_DIF(a, b, DAY) FROM t",
                "DATE_DIF", new FakeScope(Map.of(), List.of("DATE_DIFF", "DATE_ADD"), List.of(), -1, List.of())));

        assertEquals("Did you mean 'DATE_DIFF'?", hint.text());
    }

    @Test
    public void aSelectAliasUsedInWhereIsExplained() {
        String sql = "SELECT amount * 2 AS total FROM t WHERE total > 1";
        SqlScope scope = new FakeScope(Map.of("t", List.of("amount", "totals")), List.of(), List.of(), -1,
                List.of(new SqlScope.SelectItem(TextRange.from(7, "amount * 2 AS total".length()),
                        TextRange.from(sql.indexOf("total"), 5))));
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse("Unrecognized name: total at [1:41]"),
                TextRange.from(sql.lastIndexOf("total"), 5), sql, scope));

        assertTrue(hint.text().startsWith("'total' is a SELECT alias"));
        assertEquals(List.of(), hint.fixes());
    }

    @Test
    public void theAsOfACastIsNotTakenForASelectAlias() {
        String sql = "SELECT CAST(created_at AS DATE) AS created_date FROM t WHERE date > '2024-01-01'";
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse("Unrecognized name: date at [1:62]"),
                TextRange.from(sql.lastIndexOf("date"), 4), sql, FakeScope.columns(Map.of("t", List.of("created_at", "dates")))));

        assertEquals("Did you mean 'dates'?", hint.text());
    }

    @Test
    public void aNameMissingFromTheItemItNamesIsNotTakenForItsAlias() {
        String sql = "SELECT totl AS totl FROM t";
        SqlScope scope = new FakeScope(Map.of("t", List.of("total")), List.of(), List.of(), -1,
                List.of(new SqlScope.SelectItem(TextRange.from(7, "totl AS totl".length()),
                        TextRange.from(sql.lastIndexOf("totl"), 4))));
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse("Unrecognized name: totl at [1:8]"),
                TextRange.from(7, 4), sql, scope));

        assertEquals("Did you mean 'total'?", hint.text());
    }

    @Test
    public void aNameWithNothingCloseGetsAGeneralHint() {
        SqlHint hint = SqlHints.hintFor(context("Unrecognized name: zzz at [1:8]", "SELECT zzz FROM o", "zzz",
                FakeScope.columns(Map.of("o", List.of("order_id")))));

        assertEquals("No column of that name in the tables the query reads: check the spelling, or the table it comes from.",
                hint.text());
        assertEquals(List.of(), hint.fixes());
    }
}
