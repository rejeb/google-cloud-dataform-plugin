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
package io.github.rejeb.dataform.language.lexer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlxFileLexerInputBlockTest {

    private static List<String> tokens(String input) {
        SqlxFileLexer lexer = new SqlxFileLexer();
        lexer.start(input, 0, input.length(), 0);
        List<String> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            tokens.add(lexer.getTokenType() + ":" + input.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            lexer.advance();
        }
        return tokens;
    }

    private static List<String> from(List<String> tokens, String first) {
        int index = tokens.indexOf(first);
        assertTrue(index >= 0, "no " + first + " in " + tokens);
        return tokens.subList(index, tokens.size());
    }

    @Test
    void testAnInputBlockBetweenConfigAndExpectedOutput() {
        String input = "config { type: \"test\", dataset: \"orders\" }\n"
                + "input \"src\" {\n  SELECT 1 AS id\n}\n"
                + "SELECT 1 AS id\n";

        assertEquals(List.of(
                "INPUT_KEYWORD:input", "WHITE_SPACE: ", "INPUT_NAME:\"src\"", "WHITE_SPACE: ",
                "LBRACE:{", "WHITE_SPACE:\n  ", "INPUT_CONTENT:SELECT 1 AS id", "WHITE_SPACE:\n",
                "RBRACE:}", "WHITE_SPACE:\n", "SQL_CONTENT:SELECT 1 AS id\n"),
                from(tokens(input), "INPUT_KEYWORD:input"));
    }

    @Test
    void testAMultiPartLabel() {
        assertEquals(List.of(
                "INPUT_KEYWORD:input", "WHITE_SPACE: ", "INPUT_NAME:\"raw\"", "INPUT_NAME_SEPARATOR:,",
                "WHITE_SPACE: ", "INPUT_NAME:\"src\"", "WHITE_SPACE: ", "LBRACE:{",
                "INPUT_CONTENT:SELECT 1", "RBRACE:}"),
                tokens("input \"raw\", \"src\" {SELECT 1}"));
    }

    @Test
    void testTemplateHolesInsideTheBody() {
        List<String> tokens = tokens("input \"src\" {\n  SELECT ${n} AS id\n}\n");

        assertTrue(tokens.containsAll(List.of(
                "INPUT_CONTENT:SELECT ", "TEMPLATE_EXPRESSION:${n}", "INPUT_CONTENT: AS id")), tokens.toString());
    }

    @Test
    void testAColumnNamedInputStaysSql() {
        List<String> tokens = tokens("config { type: \"table\" }\nSELECT\ninput,\n  other\nFROM t\ninput\n\"x\"\n");

        assertFalse(tokens.stream().anyMatch(t -> t.startsWith("INPUT_")), tokens.toString());
    }

    @Test
    void testAHeaderWithoutBraceGivesTheRestBackToSql() {
        assertEquals(List.of(
                "INPUT_KEYWORD:input", "WHITE_SPACE: ", "INPUT_NAME:\"src\"", "WHITE_SPACE:\n",
                "SQL_CONTENT:SELECT 1\n"),
                tokens("input \"src\"\nSELECT 1\n"));
    }

    @Test
    void testAnUnterminatedNameStopsAtTheLineEnd() {
        assertEquals(List.of(
                "INPUT_KEYWORD:input", "WHITE_SPACE: ", "INPUT_NAME:\"sr", "WHITE_SPACE:\n",
                "SQL_CONTENT:SELECT 1\n"),
                tokens("input \"sr\nSELECT 1\n"));
    }

    @Test
    void testTheBraceMayOpenOnTheNextLine() {
        List<String> tokens = tokens("input \"src\"\n{\n  SELECT 1\n}\n");

        assertTrue(tokens.contains("INPUT_CONTENT:SELECT 1"), tokens.toString());
        assertTrue(tokens.contains("RBRACE:}"), tokens.toString());
    }

    @Test
    void testBalancedBracesInsideRowsStayInTheBody() {
        List<String> tokens = tokens("input \"src\" {\n  SELECT '{\"a\": 1}' AS payload\n}\nSELECT 1\n");

        assertTrue(tokens.contains("INPUT_CONTENT:SELECT '{\"a\": 1}' AS payload"), tokens.toString());
        assertTrue(tokens.contains("SQL_CONTENT:SELECT 1\n"), tokens.toString());
    }

    @Test
    void testCrlfLineEndings() {
        List<String> tokens = tokens("input \"src\" {\r\n  SELECT 1\r\n}\r\nSELECT 2\r\n");

        assertTrue(tokens.contains("INPUT_CONTENT:SELECT 1"), tokens.toString());
        assertTrue(tokens.contains("RBRACE:}"), tokens.toString());
        assertTrue(tokens.contains("SQL_CONTENT:SELECT 2\r\n"), tokens.toString());
    }
}
