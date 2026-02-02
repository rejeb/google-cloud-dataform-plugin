/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import com.intellij.json.JsonElementTypes;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SqlxConfigLexer extends LexerBase {
    private static final int STATE_INITIAL = 0;
    private static final int STATE_IN_VALUE = 1;
    private static final int STATE_IN_DOUBLE_STRING = 2;
    private static final int STATE_IN_SINGLE_STRING = 3;

    private CharSequence buffer;
    private int endOffset;
    private int currentPosition;
    private IElementType currentTokenType;
    private int currentTokenStart;
    private int currentTokenEnd;
    private int state;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.currentPosition = startOffset;
        this.state = initialState;
        advance();
    }

    @Override
    public void advance() {
        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenStart = endOffset;
            currentTokenEnd = endOffset;
            return;
        }

        currentTokenStart = currentPosition;
        locateToken();
    }

    @Override
    public int getState() {
        return state;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return currentTokenType;
    }

    @Override
    public int getTokenStart() {
        return currentTokenStart;
    }

    @Override
    public int getTokenEnd() {
        return currentTokenEnd;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private void locateToken() {
        if (state == STATE_IN_DOUBLE_STRING) {
            continueStringContent('"');
            return;
        }
        if (state == STATE_IN_SINGLE_STRING) {
            continueStringContent('\'');
            return;
        }

        skipWhitespace();

        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenEnd = endOffset;
            return;
        }

        char c = buffer.charAt(currentPosition);

        switch (c) {
            case '{':
                currentTokenType = JsonElementTypes.L_CURLY;
                currentTokenEnd = ++currentPosition;
                state = STATE_INITIAL;
                break;
            case '}':
                currentTokenType = JsonElementTypes.R_CURLY;
                currentTokenEnd = ++currentPosition;
                state = STATE_INITIAL;
                break;
            case '[':
                currentTokenType = JsonElementTypes.L_BRACKET;
                currentTokenEnd = ++currentPosition;
                state = STATE_INITIAL;
                break;
            case ']':
                currentTokenType = JsonElementTypes.R_BRACKET;
                currentTokenEnd = ++currentPosition;
                state = STATE_INITIAL;
                break;
            case ',':
                currentTokenType = JsonElementTypes.COMMA;
                currentTokenEnd = ++currentPosition;
                state = STATE_INITIAL;
                break;
            case ':':
                currentTokenType = JsonElementTypes.COLON;
                currentTokenEnd = ++currentPosition;
                state = STATE_IN_VALUE;
                break;
            case '"':
                lexDoubleQuotedString();
                state = (state == STATE_IN_DOUBLE_STRING) ? state : STATE_INITIAL;
                break;
            case '\'':
                lexSingleQuotedString();
                state = (state == STATE_IN_SINGLE_STRING) ? state : STATE_INITIAL;
                break;
            case '$':
                if (state == STATE_IN_VALUE) {
                    lexJsReference(null);
                } else {
                    lexIdentifier();
                }
                state = STATE_INITIAL;
                break;
            default:
                if (state == STATE_IN_VALUE) {
                    lexValue();
                    state = STATE_INITIAL;
                } else if (Character.isLetter(c)) {
                    lexIdentifier();
                } else {
                    currentTokenType = TokenType.BAD_CHARACTER;
                    currentTokenEnd = ++currentPosition;
                }
                break;
        }
    }

    private void lexIdentifier() {
        while (currentPosition < endOffset &&
                Character.isLetterOrDigit(buffer.charAt(currentPosition))) {
            currentPosition++;
        }
        currentTokenType = JsonElementTypes.IDENTIFIER;
        currentTokenEnd = currentPosition;
    }

    private void lexValue() {
        int start = currentPosition;
        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);
            if (c == ',' || c == '}' || c == ']' || c == '{' || c == '[' || Character.isWhitespace(c)) {
                break;
            }
            currentPosition++;
        }

        String tokenText = buffer.subSequence(start, currentPosition).toString();

        if ("true".equals(tokenText) || "false".equals(tokenText)) {
            currentTokenType = JsonElementTypes.BOOLEAN_LITERAL;
        } else if ("null".equals(tokenText)) {
            currentTokenType = JsonElementTypes.NULL;
        } else if (isNumeric(tokenText)) {
            currentTokenType = JsonElementTypes.NUMBER_LITERAL;
        } else {
            currentTokenType = SharedTokenTypes.JS_LITTERAL;
        }

        currentTokenEnd = currentPosition;
    }

    private void lexDoubleQuotedString() {
        currentPosition++;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '"') {

                currentPosition++;
                currentTokenType = JsonElementTypes.DOUBLE_QUOTED_STRING;
                currentTokenEnd = currentPosition;
                return;
            }

            if (c == '\\' && currentPosition + 1 < endOffset) {

                currentPosition += 2;
                continue;
            }

            if (c == '$' && currentPosition + 1 < endOffset) {
                char next = buffer.charAt(currentPosition + 1);

                if (next == '{' || Character.isLetterOrDigit(next)) {


                    currentTokenType = JsonElementTypes.DOUBLE_QUOTED_STRING;
                    currentTokenEnd = currentPosition;
                    state = STATE_IN_DOUBLE_STRING;
                    return;
                }
            }

            currentPosition++;
        }


        currentTokenType = JsonElementTypes.DOUBLE_QUOTED_STRING;
        currentTokenEnd = currentPosition;
    }

    private void lexSingleQuotedString() {
        currentPosition++;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '\'') {
                currentPosition++;
                currentTokenType = JsonElementTypes.SINGLE_QUOTED_STRING;
                currentTokenEnd = currentPosition;
                return;
            }

            if (c == '\\' && currentPosition + 1 < endOffset) {
                currentPosition += 2;
                continue;
            }

            if (c == '$' && currentPosition + 1 < endOffset) {
                char next = buffer.charAt(currentPosition + 1);

                if (next == '{' || Character.isLetterOrDigit(next)) {
                    currentTokenType = JsonElementTypes.SINGLE_QUOTED_STRING;
                    currentTokenEnd = currentPosition;
                    state = STATE_IN_SINGLE_STRING;
                    return;
                }
            }

            currentPosition++;
        }

        currentTokenType = JsonElementTypes.SINGLE_QUOTED_STRING;
        currentTokenEnd = currentPosition;
    }

    private void continueStringContent(char quoteChar) {
        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenEnd = endOffset;
            state = STATE_INITIAL;
            return;
        }

        if (buffer.charAt(currentPosition) == '$' && currentPosition + 1 < endOffset) {
            char next = buffer.charAt(currentPosition + 1);
            if (next == '{' || Character.isLetterOrDigit(next)) {
                lexJsReference(quoteChar);
                return;
            }
        }

        int fragmentStart = currentPosition;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == quoteChar) {
                // Fin de la string - inclure le guillemet fermant
                currentPosition++;
                currentTokenType = (quoteChar == '"')
                        ? JsonElementTypes.DOUBLE_QUOTED_STRING
                        : JsonElementTypes.SINGLE_QUOTED_STRING;
                currentTokenEnd = currentPosition;
                state = STATE_INITIAL;
                return;
            }

            if (c == '\\' && currentPosition + 1 < endOffset) {
                currentPosition += 2;
                continue;
            }

            if (c == '$' && currentPosition + 1 < endOffset) {
                char next = buffer.charAt(currentPosition + 1);

                if (next == '{' || Character.isLetterOrDigit(next)) {
                    // Émettre le fragment de texte accumulé
                    if (currentPosition > fragmentStart) {
                        currentTokenType = (quoteChar == '"')
                                ? JsonElementTypes.DOUBLE_QUOTED_STRING
                                : JsonElementTypes.SINGLE_QUOTED_STRING;
                        currentTokenEnd = currentPosition;
                        return;
                    }
                    lexJsReference(quoteChar);
                    return;
                }
            }

            currentPosition++;
        }

        if (currentPosition > fragmentStart) {
            currentTokenType = (quoteChar == '"')
                    ? JsonElementTypes.DOUBLE_QUOTED_STRING
                    : JsonElementTypes.SINGLE_QUOTED_STRING;
        } else {
            currentTokenType = null;
        }
        currentTokenEnd = currentPosition;
        state = STATE_INITIAL;
    }

    private void lexJsReference(Character quoteChar) {
        currentPosition++;

        if (currentPosition >= endOffset) {
            currentTokenType = TokenType.BAD_CHARACTER;
            currentTokenEnd = currentPosition;
            return;
        }

        char c = buffer.charAt(currentPosition);

        if (c == '{') {
            int bracketCount = 1;
            currentPosition++;
            while (currentPosition < endOffset && bracketCount > 0) {
                char ch = buffer.charAt(currentPosition);
                if (ch == '{') {
                    bracketCount++;
                } else if (ch == '}') {
                    bracketCount--;
                }
                currentPosition++;
            }
            currentTokenType = SharedTokenTypes.TEMPLATE_EXPRESSION;
            currentTokenEnd = currentPosition;
        } else if (Character.isLetterOrDigit(c)) {
            while (currentPosition < endOffset &&
                    notReferenceExpressionEnd(quoteChar, buffer.charAt(currentPosition))) {
                currentPosition++;
            }
            currentTokenType = SharedTokenTypes.REFERENCE_EXPRESSION;
            currentTokenEnd = currentPosition;
        } else {
            currentTokenType = TokenType.BAD_CHARACTER;
            currentTokenEnd = currentPosition;
        }
        if (quoteChar != null) {
            state = (quoteChar == '"') ? STATE_IN_DOUBLE_STRING : STATE_IN_SINGLE_STRING;
        }
    }

    private boolean notReferenceExpressionEnd(Character quoteChar, char currentChar) {
        if (quoteChar != null) {
            return currentChar != quoteChar;
        } else {
            return currentChar != ',';
        }
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        int i = 0;
        if (str.charAt(0) == '-' || str.charAt(0) == '+') {
            if (str.length() == 1) {
                return false;
            }
            i = 1;
        }

        boolean hasDecimal = false;
        boolean hasExponent = false;

        for (; i < str.length(); i++) {
            char c = str.charAt(i);

            if (Character.isDigit(c)) {
                continue;
            } else if (c == '.' && !hasDecimal && !hasExponent) {
                hasDecimal = true;
            } else if ((c == 'e' || c == 'E') && !hasExponent) {
                hasExponent = true;
                // Après 'e', on peut avoir un signe
                if (i + 1 < str.length() && (str.charAt(i + 1) == '+' || str.charAt(i + 1) == '-')) {
                    i++;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    private void skipWhitespace() {
        while (currentPosition < endOffset &&
                Character.isWhitespace(buffer.charAt(currentPosition))) {
            currentPosition++;
        }
        currentTokenStart = currentPosition;
    }

}
