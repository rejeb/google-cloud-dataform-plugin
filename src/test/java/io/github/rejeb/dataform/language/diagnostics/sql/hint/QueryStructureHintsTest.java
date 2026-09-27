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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryStructureHintsTest {

    private static SqlHint hint(String message, String sql, String token, SqlScope scope) {
        return SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse(message),
                TextRange.from(sql.indexOf(token), token.length()), sql, scope));
    }

    @Test
    public void anAmbiguousColumnIsQualifiedWithEachSourceHoldingIt() {
        Map<String, List<String>> sources = new LinkedHashMap<>();
        sources.put("o", List.of("customer_id"));
        sources.put("c", List.of("customer_id", "name"));
        SqlHint hint = hint("Column name customer_id is ambiguous at [1:8]", "SELECT customer_id FROM o JOIN c ON true",
                "customer_id", FakeScope.columns(sources));

        assertEquals("'customer_id' exists in o and c: qualify it with the one it should come from.", hint.text());
        assertEquals(List.of(
                new SqlFix("Qualify as 'o.customer_id'", new TextRange(7, 18), "o.customer_id"),
                new SqlFix("Qualify as 'c.customer_id'", new TextRange(7, 18), "c.customer_id")), hint.fixes());
    }

    @Test
    public void aColumnNeitherGroupedNorAggregatedCanBeGroupedOrWrapped() {
        String sql = "SELECT customer_id, amount FROM t GROUP BY customer_id";
        SqlHint hint = hint("SELECT list expression references column amount which is neither grouped nor aggregated at [1:21]",
                sql, "amount", new FakeScope(Map.of(), List.of(), List.of(), sql.length(), List.of()));

        assertEquals("Add it to GROUP BY, or aggregate it (ANY_VALUE, MAX, …).", hint.text());
        assertEquals(List.of(
                new SqlFix("Add 'amount' to GROUP BY", TextRange.from(sql.length(), 0), ", amount"),
                new SqlFix("Wrap in ANY_VALUE()", new TextRange(20, 26), "ANY_VALUE(amount)")), hint.fixes());
    }

    @Test
    public void withoutGroupByOnlyWrappingIsOffered() {
        SqlHint hint = hint("SELECT list expression references column amount which is neither grouped nor aggregated at [1:21]",
                "SELECT COUNT(*), amount FROM t", "amount", SqlScope.EMPTY);

        assertEquals(List.of("Wrap in ANY_VALUE()"), hint.fixes().stream().map(SqlFix::label).toList());
    }

    @Test
    public void aggregatesAndWindowsInWhereAreExplained() {
        assertTrue(hint("Aggregate function SUM not allowed in WHERE clause at [1:23]", "SELECT a FROM t WHERE SUM(a) > 1",
                "SUM", SqlScope.EMPTY).text().contains("HAVING"));
        assertTrue(hint("Analytic function not allowed in WHERE clause at [1:23]", "SELECT a FROM t WHERE ROW_NUMBER() OVER () = 1",
                "ROW_NUMBER", SqlScope.EMPTY).text().contains("QUALIFY"));
    }

    @Test
    public void aDuplicateColumnIsGivenANameOfItsOwn() {
        String sql = "SELECT order_id, order_id FROM t";
        SqlScope scope = new FakeScope(Map.of(), List.of(), List.of(), -1,
                List.of(new SqlScope.SelectItem(new TextRange(7, 15), null), new SqlScope.SelectItem(new TextRange(17, 25), null)));
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse(
                "Duplicate column names in the result are not supported. Found duplicate(s): order_id"),
                new TextRange(17, 25), sql, scope));

        assertEquals(List.of(new SqlFix("Name it 'order_id_2'", TextRange.from(25, 0), " AS order_id_2")), hint.fixes());
    }

    @Test
    public void aDuplicateAliasIsRenamed() {
        String sql = "SELECT a AS id, b AS id FROM t";
        SqlScope scope = new FakeScope(Map.of(), List.of(), List.of(), -1,
                List.of(new SqlScope.SelectItem(new TextRange(7, 14), new TextRange(12, 14)),
                        new SqlScope.SelectItem(new TextRange(16, 23), new TextRange(21, 23))));
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse(
                "Duplicate column names in the result are not supported. Found duplicate(s): id"),
                new TextRange(16, 23), sql, scope));

        assertEquals(List.of(new SqlFix("Rename to 'id_2'", new TextRange(21, 23), "id_2")), hint.fixes());
    }

    @Test
    public void aSignatureMismatchShowsTheExpectedSignature() {
        SqlHint hint = hint("No matching signature for function DATE_DIFF for argument types: DATE, TIMESTAMP, DATE_TIME_PART. "
                        + "Supported signature: DATE_DIFF(DATE, DATE, DATE_TIME_PART) at [1:8]",
                "SELECT DATE_DIFF(a, b, DAY) FROM t", "DATE_DIFF", SqlScope.EMPTY);

        assertEquals("Expected: DATE_DIFF(DATE, DATE, DATE_TIME_PART). Convert the arguments with CAST or SAFE_CAST.",
                hint.text());
    }

    @Test
    public void aFieldReadFromAnArraySuggestsUnnest() {
        assertTrue(hint("Cannot access field city on a value with type ARRAY<STRUCT<city STRING>> at [1:8]",
                "SELECT addresses.city FROM t", "addresses.city", SqlScope.EMPTY).text().contains("UNNEST"));
    }
}
