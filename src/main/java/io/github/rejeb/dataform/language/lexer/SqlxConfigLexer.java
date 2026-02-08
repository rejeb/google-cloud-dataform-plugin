/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

    // États du lexer
    private static final int STATE_INITIAL = 0;
    private static final int STATE_IN_VALUE = 1;

    // Caractères spéciaux
    public static final char DOUBLE_QUOTE = '"';
    public static final char SINGLE_QUOTE = '\'';
    public static final char BACKTICK = '`';

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

            case DOUBLE_QUOTE:
                lexString(DOUBLE_QUOTE, JsonElementTypes.DOUBLE_QUOTED_STRING);
                break;
            case SINGLE_QUOTE:
                lexString(SINGLE_QUOTE, JsonElementTypes.SINGLE_QUOTED_STRING);
                break;
            case BACKTICK:
                lexString(BACKTICK, SharedTokenTypes.JS_LITTERAL);
                break;

            case '$':
                if (state == STATE_IN_VALUE || canStartJsExpression()) {
                    lexJsReference(null, -1);
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

    private void lexJsReference(Character quoteChar, int returnState) {
        currentPosition++;

        if (currentPosition >= endOffset) {
            currentTokenType = TokenType.BAD_CHARACTER;
            currentTokenEnd = ++currentPosition;
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
        }else {
            currentTokenType = TokenType.BAD_CHARACTER;
            currentPosition++;
        }

        currentTokenEnd = currentPosition;

        if (quoteChar != null) {
            state = returnState;
        } else {
            state = STATE_INITIAL;
        }
    }

    private void lexString(char coteChar, IElementType contentType) {
        currentPosition++;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == coteChar) {
                currentPosition++;
                currentTokenType = contentType;
                currentTokenEnd = currentPosition;
                return;
            }

            if (c == '\\' && currentPosition + 1 < endOffset) {
                currentPosition += 2;
                continue;
            }

            currentPosition++;
        }

        currentTokenType = contentType;
        currentTokenEnd = currentPosition;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-' || str.charAt(0) == '+') {
            if (str.length() == 1) return false;
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

    private boolean canStartJsExpression() {
        if (currentPosition + 1 >= endOffset) return false;
        char next = buffer.charAt(currentPosition + 1);
        return next == '{';
    }
}
