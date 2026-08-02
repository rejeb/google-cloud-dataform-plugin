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

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.fail;

class SqlxFileLexerRestartTest {

    private static final String[] CORPUS = {
            "config { type: \"table\" }\n\nSELECT 1\n",
            "config {\n  type: \"view\",\n  tags: [\"a\"]\n}\n\nSELECT * FROM ${ref(\"x\")}\n",
            "js {\n  const x = { a: 1 };\n}\n\nSELECT 1\n",
            "config { type: \"incremental\" }\npre_operations {\n  DECLARE d DATE;\n}\n"
                    + "SELECT 1\npost_operations {\n  GRANT SELECT ON ${self()} TO \"a\";\n}\n",
            "SELECT 1\n",
            "",
            "config",
            "config {",
            "config { }",
            "}\n",
            "{\n",
            "$",
            "${",
            "${ref(\"a\")}",
            "SELECT ${",
            "SELECT $",
            "SELECT 1 } EXTRA\n",
            "config { a: '}' }\nSELECT 1\n",
            "js { const s = \"}\"; }\nSELECT 1\n",
            "\n\n\n",
            "   ",
            "config{type:\"table\"}SELECT 1",
            "SELECT 1\nconfig { type: \"table\" }\n",
            "SELECT 1\n  js {\n  }\n",
            "pre_operations {\n}\n",
            "pre_operations {}\n",
            "config { } config { }\n",
            "-- comment\nSELECT 1\n",
            "SELECT 1 -- js\n",
            "pre_operations {\n  if (x) {\n    ${a}\n  }\n}\nSELECT 1\n",
            "config { type: \"table\" }\n\nSELECT\n  ${when(true, 'a', 'b')}\n",
    };

    private record Tok(IElementType type, int start, int end) {
    }

    @Test
    void tokensTileTheInputAndNeverProduceBadCharacters() {
        StringBuilder report = new StringBuilder();
        for (String input : CORPUS) {
            List<Tok> tokens = tokenize(input, 0, 0);
            int expected = 0;
            for (Tok token : tokens) {
                if (token.type() == TokenType.BAD_CHARACTER) {
                    report.append("BAD_CHARACTER at ").append(token.start())
                            .append(" in ").append(quote(input)).append('\n');
                }
                if (token.start() != expected) {
                    report.append("gap or overlap at ").append(token.start())
                            .append(", expected ").append(expected)
                            .append(" in ").append(quote(input)).append('\n');
                }
                expected = token.end();
            }
            if (expected != input.length()) {
                report.append("input truncated at ").append(expected).append('/')
                        .append(input.length()).append(" in ").append(quote(input)).append('\n');
            }
        }
        if (!report.isEmpty()) {
            fail("\n" + report);
        }
    }

    @Test
    void restartingAtAnyTokenWithItsStateReproducesTheSameTokens() {
        StringBuilder report = new StringBuilder();
        for (String input : CORPUS) {
            List<Tok> full = tokenize(input, 0, 0);
            SqlxFileLexer lexer = new SqlxFileLexer();
            lexer.start(input, 0, input.length(), 0);
            while (lexer.getTokenType() != null) {
                int offset = lexer.getTokenStart();
                List<Tok> tail = tokenize(input, offset, lexer.getState());
                List<Tok> reference = full.stream().filter(t -> t.start() >= offset).toList();
                if (!reference.equals(tail)) {
                    report.append("restart mismatch at ").append(offset)
                            .append(" state ").append(lexer.getState())
                            .append(" in ").append(quote(input))
                            .append("\n   expected: ").append(render(reference, input))
                            .append("\n   actual:   ").append(render(tail, input)).append('\n');
                }
                lexer.advance();
            }
        }
        if (!report.isEmpty()) {
            fail("\n" + report);
        }
    }

    @Test
    void anyInputAndAnyStateTerminatesWithoutBadCharacters() {
        String[] atoms = {"config", "js", "pre_operations", "post_operations", "{", "}", "\n",
                " ", "  ", "$", "${", "SELECT", "1", "\"", "'", "ref(\"a\")", "\t", ";", "//",
                "/*", "*/", "\\", "`", "%", "\u20ac", "\u0000", "a"};
        Random random = new Random(42);
        StringBuilder report = new StringBuilder();
        for (int iteration = 0; iteration < 20000 && report.length() < 2000; iteration++) {
            StringBuilder text = new StringBuilder();
            int atomCount = 1 + random.nextInt(10);
            for (int i = 0; i < atomCount; i++) {
                text.append(atoms[random.nextInt(atoms.length)]);
            }
            String input = text.toString();
            for (int state = 0; state <= 7; state++) {
                try {
                    for (Tok token : tokenize(input, 0, state)) {
                        if (token.type() == TokenType.BAD_CHARACTER) {
                            report.append("BAD_CHARACTER state=").append(state)
                                    .append(" at ").append(token.start())
                                    .append(" in ").append(quote(input)).append('\n');
                            break;
                        }
                    }
                } catch (RuntimeException e) {
                    report.append(e).append(" state=").append(state)
                            .append(" in ").append(quote(input)).append('\n');
                }
            }
        }
        if (!report.isEmpty()) {
            fail("\n" + report);
        }
    }

    private List<Tok> tokenize(String input, int from, int state) {
        SqlxFileLexer lexer = new SqlxFileLexer();
        lexer.start(input, from, input.length(), state);
        List<Tok> tokens = new ArrayList<>();
        int guard = 0;
        while (lexer.getTokenType() != null) {
            if (guard++ > 100000) {
                throw new IllegalStateException("lexer did not terminate");
            }
            tokens.add(new Tok(lexer.getTokenType(), lexer.getTokenStart(), lexer.getTokenEnd()));
            lexer.advance();
        }
        return tokens;
    }

    private static String render(List<Tok> tokens, String input) {
        StringBuilder sb = new StringBuilder();
        for (Tok token : tokens) {
            sb.append(token.type()).append(quote(input.substring(token.start(), token.end())))
                    .append(' ');
        }
        return sb.toString();
    }

    private static String quote(String text) {
        return "<<" + text.replace("\n", "\\n").replace("\t", "\\t") + ">>";
    }
}
