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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyntaxHintsTest {

    private static SqlHint hint(String message, String sql, int start, int length) {
        return SqlHints.hintFor(new SqlErrorContext(BigQueryErrorParser.parse(message),
                TextRange.from(start, length), sql, SqlScope.EMPTY));
    }

    @Test
    public void aCommaBeforeAClauseKeywordIsRemoved() {
        String sql = "SELECT a, b,\nFROM t";
        SqlHint hint = hint("Syntax error: Unexpected keyword FROM at [2:1]", sql, sql.indexOf("FROM"), 4);

        assertEquals("Remove the ',' before FROM.", hint.text());
        assertEquals(List.of(new SqlFix("Remove the extra ','", TextRange.from(11, 1), "")), hint.fixes());
    }

    @Test
    public void aMissingCommaBetweenSelectItemsIsInserted() {
        String sql = "SELECT a b c FROM t";
        SqlHint hint = hint("Syntax error: Expected end of input but got identifier \"c\" at [1:12]", sql, 11, 1);

        assertEquals("A ',' may be missing before 'c'.", hint.text());
        assertEquals(List.of(new SqlFix("Insert ',' before 'c'", TextRange.from(10, 0), ",")), hint.fixes());
    }

    @Test
    public void aSecondStatementIsExplained() {
        String sql = "SELECT 1\nSELECT 2";
        SqlHint hint = hint("Syntax error: Expected end of input but got keyword SELECT at [2:1]", sql, 9, 6);

        assertTrue(hint.text().startsWith("A second statement starts here"));
        assertEquals(List.of(), hint.fixes());
    }

    @Test
    public void anUnclosedParenthesisPointsAtWhereItWasOpened() {
        String sql = "SELECT COUNT(a\nFROM t";
        SqlHint hint = hint("Syntax error: Expected \")\" but got keyword FROM at [2:1]", sql, sql.indexOf("FROM"), 4);

        assertEquals("The '(' opened on line 1 is not closed before FROM.", hint.text());
        assertEquals(List.of(new SqlFix("Insert ')' before FROM", TextRange.from(sql.indexOf("FROM"), 0), ")")), hint.fixes());
    }

    @Test
    public void aParenthesisInACommentIsNotCounted() {
        String sql = "SELECT\n  COUNT(a -- b)\nFROM t";
        SqlHint hint = hint("Syntax error: Expected \")\" but got keyword FROM at [3:1]", sql, sql.indexOf("FROM"), 4);

        assertEquals("The '(' opened on line 2 is not closed before FROM.", hint.text());
    }

    @Test
    public void anApostropheInACommentDoesNotHideTheOpenParenthesis() {
        String sql = "SELECT\n  -- customer's orders\n  COUNT(order_id\nFROM t";
        SqlHint hint = hint("Syntax error: Expected \")\" but got keyword FROM at [4:1]", sql, sql.indexOf("FROM"), 4);

        assertEquals("The '(' opened on line 3 is not closed before FROM.", hint.text());
    }

    @Test
    public void theJavaScriptBeforeTheQueryIsNotSearchedForTheParenthesis() {
        String text = "js {\n  const re = /\\(/;\n}\n\nSELECT ${cols}\nFROM t";
        SqlHint hint = SqlHints.hintFor(new SqlErrorContext(
                BigQueryErrorParser.parse("Syntax error: Expected \")\" but got keyword FROM at [2:1]"),
                TextRange.from(text.indexOf("FROM"), 4), text, new StartingAt(text.indexOf("SELECT"))));

        assertEquals("A ')' is missing.", hint.text());
    }

    private record StartingAt(int start) implements SqlScope {

        @Override
        public Map<String, List<String>> columnsBySource() {
            return Map.of();
        }

        @Override
        public Collection<String> functions() {
            return List.of();
        }

        @Override
        public Collection<String> actionNames() {
            return List.of();
        }

        @Override
        public int groupByEnd() {
            return -1;
        }

        @Override
        public List<SelectItem> selectItemsNamed(String name) {
            return List.of();
        }

        @Override
        public int sqlStart() {
            return start;
        }
    }

    @Test
    public void typographicQuotesAreReplaced() {
        String sql = "SELECT \u201Ca\u201D";
        SqlHint hint = hint("Syntax error: Illegal input character \"\u201C\" at [1:8]", sql, 7, 1);

        assertEquals(List.of(new SqlFix("Replace with a plain quote", TextRange.from(7, 1), "\"")), hint.fixes());
    }

    @Test
    public void anExpectedKeywordIsNamed() {
        String sql = "SELECT a FROM t ORDER a";
        SqlHint hint = hint("Syntax error: Expected keyword BY but got identifier \"a\" at [1:23]", sql, 22, 1);

        assertEquals("BigQuery expected keyword BY before 'a'.", hint.text());
        assertEquals(List.of(), hint.fixes());
    }

    @Test
    public void unclosedStringsAndEarlyEndsAreExplained() {
        assertTrue(hint("Syntax error: Unclosed string literal at [1:8]", "SELECT 'a", 7, 2).text().contains("'''"));
        assertTrue(hint("Syntax error: Unexpected end of script at [1:15]", "SELECT a FROM (", 15, 0).text()
                .startsWith("The query ends too early"));
    }

    @Test
    public void aKeywordIsNotTakenForAMissingComma() {
        String sql = "SELECT a FROM t JOIN u WHERE x = 1";
        SqlHint hint = hint("Syntax error: Unexpected keyword WHERE at [1:24]", sql, sql.indexOf("WHERE"), 5);

        assertEquals(List.of(), hint.fixes());
    }
}
