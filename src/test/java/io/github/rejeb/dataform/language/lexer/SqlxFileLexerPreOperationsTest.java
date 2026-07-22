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
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlxFileLexerPreOperationsTest {

    private record Tok(IElementType type, String text) {}

    private List<Tok> tokenize(String input) {
        SqlxFileLexer lexer = new SqlxFileLexer();
        lexer.start(input, 0, input.length(), 0);
        List<Tok> tokens = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            tokens.add(new Tok(lexer.getTokenType(),
                    input.substring(lexer.getTokenStart(), lexer.getTokenEnd())));
            lexer.advance();
        }
        return tokens;
    }

    @Test
    void trailingWhitespaceBeforeClosingBraceIsSplitIntoWhitespaceToken() {
        String input = "pre_operations {\n"
                + "declare countries ARRAY<STRING>;\n"
                + "set countries = (select 1)\n"
                + "  }\n";

        List<Tok> tokens = tokenize(input);

        int rbraceIdx = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type() == SharedTokenTypes.RBRACE) {
                rbraceIdx = i;
                break;
            }
        }
        assertTrue(rbraceIdx > 0, "RBRACE token not found");

        Tok beforeBrace = tokens.get(rbraceIdx - 1);
        assertEquals(TokenType.WHITE_SPACE, beforeBrace.type(),
                "Closing brace must be preceded by a WHITE_SPACE token");
        assertEquals("\n  ", beforeBrace.text());

        Tok content = tokens.get(rbraceIdx - 2);
        assertEquals(SharedTokenTypes.PRE_OPERATIONS_CONTENT, content.type());
        assertFalse(Character.isWhitespace(content.text().charAt(content.text().length() - 1)),
                "Content token must not end with whitespace");
    }

    @Test
    void closingBraceWithoutTrailingWhitespaceStillTokenizes() {
        String input = "pre_operations {\nselect 1}\n";

        List<Tok> tokens = tokenize(input);

        boolean hasRbrace = tokens.stream().anyMatch(t -> t.type() == SharedTokenTypes.RBRACE);
        assertTrue(hasRbrace, "RBRACE token not found");
    }
}
