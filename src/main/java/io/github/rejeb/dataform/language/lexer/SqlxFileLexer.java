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

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SqlxFileLexer extends LexerBase {

    private static final int YYINITIAL = 0;
    private static final int CONFIG_BLOCK = 1;
    private static final int JS_BLOCK = 2;
    private static final int SQL_CONTENT = 3;
    private static final int PRE_OPERATIONS_BLOCK = 4;
    private static final int POST_OPERATIONS_BLOCK = 5;

    private static final String CONFIG_KEYWORD = "config";
    private static final String JS_KEYWORD = "js";
    private static final String PRE_OPERATIONS_KEYWORD = "pre_operations";
    private static final String POST_OPERATIONS_KEYWORD = "post_operations";

    private CharSequence buffer;
    private int endOffset;
    private int currentPosition;
    private IElementType currentTokenType;
    private int currentTokenStart;
    private int currentTokenEnd;
    private int state;
    private int braceDepth;
    private boolean afterOpenBrace;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.currentPosition = startOffset;
        this.state = initialState;
        this.braceDepth = 0;
        this.afterOpenBrace = false;
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
        switch (state) {
            case YYINITIAL:
                locateTokenInitial();
                break;
            case CONFIG_BLOCK:
                locateConfigOrJsBlock(SharedTokenTypes.CONFIG_CONTENT);
                break;
            case JS_BLOCK:
                locateConfigOrJsBlock(SharedTokenTypes.JS_CONTENT);
                break;
            case PRE_OPERATIONS_BLOCK:
                locateOperationsBlock(SharedTokenTypes.PRE_OPERATIONS_CONTENT);
                break;
            case POST_OPERATIONS_BLOCK:
                locateOperationsBlock(SharedTokenTypes.POST_OPERATIONS_CONTENT);
                break;
            case SQL_CONTENT:
                locateTokenSqlContent();
                break;
            default:
                currentTokenType = TokenType.BAD_CHARACTER;
                currentTokenEnd = ++currentPosition;
                break;
        }
    }

    private void locateTokenInitial() {
        if (Character.isWhitespace(buffer.charAt(currentPosition))) {
            skipWhitespace();
            currentTokenType = TokenType.WHITE_SPACE;
            currentTokenEnd = currentPosition;
            return;
        }

        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenEnd = endOffset;
            return;
        }

        if (matchKeyword(CONFIG_KEYWORD)) {
            continueInKeywordBlock(CONFIG_KEYWORD, SharedTokenTypes.CONFIG_KEYWORD, CONFIG_BLOCK);
            return;
        }
        if (matchKeyword(JS_KEYWORD)) {
            continueInKeywordBlock(JS_KEYWORD, SharedTokenTypes.JS_KEYWORD, JS_BLOCK);
            return;
        }
        if (matchKeyword(PRE_OPERATIONS_KEYWORD)) {
            continueInKeywordBlock(PRE_OPERATIONS_KEYWORD, SharedTokenTypes.PRE_OPERATIONS_KEYWORD, PRE_OPERATIONS_BLOCK);
            return;
        }
        if (matchKeyword(POST_OPERATIONS_KEYWORD)) {
            continueInKeywordBlock(POST_OPERATIONS_KEYWORD, SharedTokenTypes.POST_OPERATIONS_KEYWORD, POST_OPERATIONS_BLOCK);
            return;
        }

        state = SQL_CONTENT;
        locateTokenSqlContent();
    }

    private void continueInKeywordBlock(String keyword, IElementType tokenType, int state) {
        int endPos = currentPosition + keyword.length();
        currentPosition = endPos;
        currentTokenType = tokenType;
        currentTokenEnd = currentPosition;
        braceDepth = 0;
        this.state = state;
    }

    private void locateConfigOrJsBlock(IElementType tokenType) {
        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenEnd = endOffset;
            return;
        }

        char first = buffer.charAt(currentPosition);

        if (afterOpenBrace) {
            afterOpenBrace = false;
            if (Character.isWhitespace(first)) {
                skipWhitespace();
                currentTokenType = TokenType.WHITE_SPACE;
                currentTokenEnd = currentPosition;
                return;
            }
        }

        if (braceDepth == 0 && Character.isWhitespace(first)) {
            skipWhitespace();
            currentTokenType = TokenType.WHITE_SPACE;
            currentTokenEnd = currentPosition;
            return;
        }

        if (braceDepth == 0 && first == '{') {
            braceDepth = 1;
            currentPosition++;
            currentTokenType = SharedTokenTypes.LBRACE;
            currentTokenEnd = currentPosition;
            afterOpenBrace = true;
            return;
        }

        int start = currentPosition;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '{') {
                braceDepth++;
                currentPosition++;
            } else if (c == '}') {
                if (braceDepth == 1) {
                    if (currentPosition > start) {
                        currentTokenType = tokenType;
                        currentTokenEnd = currentPosition;
                        return;
                    }
                    braceDepth = 0;
                    currentPosition++;
                    currentTokenType = SharedTokenTypes.RBRACE;
                    currentTokenEnd = currentPosition;
                    state = YYINITIAL;
                    return;
                }
                braceDepth--;
                currentPosition++;
            } else {
                currentPosition++;
            }
        }

        if (currentPosition > start) {
            currentTokenType = tokenType;
            currentTokenEnd = currentPosition;
        } else {
            currentTokenType = null;
            currentTokenEnd = endOffset;
        }
    }

    private void locateOperationsBlock(IElementType tokenType) {
        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenEnd = endOffset;
            return;
        }

        char first = buffer.charAt(currentPosition);

        if (afterOpenBrace) {
            afterOpenBrace = false;
            if (Character.isWhitespace(first)) {
                skipWhitespace();
                currentTokenType = TokenType.WHITE_SPACE;
                currentTokenEnd = currentPosition;
                return;
            }
        }

        if (braceDepth == 0 && Character.isWhitespace(first)) {
            skipWhitespace();
            currentTokenType = TokenType.WHITE_SPACE;
            currentTokenEnd = currentPosition;
            return;
        }

        if (braceDepth == 0 && first == '{') {
            braceDepth = 1;
            currentPosition++;
            currentTokenType = SharedTokenTypes.LBRACE;
            currentTokenEnd = currentPosition;
            afterOpenBrace = true;
            return;
        }

        // Handle ${...} template expression at current position
        if (tryConsumeTemplateExpression()) {
            return;
        }

        int start = currentPosition;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '$' && currentPosition + 1 < endOffset && buffer.charAt(currentPosition + 1) == '{') {
                if (currentPosition > start) {
                    currentTokenType = tokenType;
                    currentTokenEnd = currentPosition;
                    return;
                }
                break;
            }

            if (c == '{') {
                braceDepth++;
                currentPosition++;
            } else if (c == '}') {
                if (braceDepth == 1) {
                    if (currentPosition > start) {
                        currentTokenType = tokenType;
                        currentTokenEnd = currentPosition;
                        return;
                    }
                    braceDepth = 0;
                    currentPosition++;
                    currentTokenType = SharedTokenTypes.RBRACE;
                    currentTokenEnd = currentPosition;
                    state = YYINITIAL;
                    return;
                }
                braceDepth--;
                currentPosition++;
            } else {
                currentPosition++;
            }
        }

        if (currentPosition > start) {
            currentTokenType = tokenType;
            currentTokenEnd = currentPosition;
        } else {
            currentTokenType = null;
            currentTokenEnd = endOffset;
        }
    }

    private void locateTokenSqlContent() {
        if (currentPosition >= endOffset) {
            currentTokenType = null;
            currentTokenEnd = endOffset;
            return;
        }

        if (isStartOfLine()) {
            int savedPos = currentPosition;

            // Skip non-newline leading whitespace to look ahead for a block keyword
            while (currentPosition < endOffset &&
                    Character.isWhitespace(buffer.charAt(currentPosition)) &&
                    buffer.charAt(currentPosition) != '\n') {
                currentPosition++;
            }

            if (currentPosition < endOffset && matchesAnyBlockKeyword()) {
                if (currentPosition > savedPos) {
                    // Emit the leading whitespace before transitioning to YYINITIAL
                    currentTokenType = TokenType.WHITE_SPACE;
                    currentTokenEnd = currentPosition;
                    return;
                }
                // No leading whitespace: transition directly
                state = YYINITIAL;
                locateTokenInitial();
                return;
            }

            // No keyword found: reset and continue as SQL content
            currentPosition = savedPos;
        }

        // Handle ${...} template expression at current position
        if (tryConsumeTemplateExpression()) {
            return;
        }

        int start = currentPosition;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '$') {
                if (currentPosition + 1 < endOffset && buffer.charAt(currentPosition + 1) == '{') {
                    break;
                }
                currentPosition++;
            } else if (c == '\n') {
                currentPosition++;
                if (currentPosition < endOffset) {
                    int savedPos = currentPosition;

                    // Skip non-newline whitespace after newline to peek at keyword
                    while (currentPosition < endOffset &&
                            Character.isWhitespace(buffer.charAt(currentPosition)) &&
                            buffer.charAt(currentPosition) != '\n') {
                        currentPosition++;
                    }

                    if (matchesAnyBlockKeyword()) {
                        currentPosition = savedPos;
                        break;
                    }

                    currentPosition = savedPos;
                }
            } else {
                currentPosition++;
            }
        }

        if (currentPosition > start) {
            currentTokenType = SharedTokenTypes.SQL_CONTENT;
            currentTokenEnd = currentPosition;
        } else {
            currentTokenType = TokenType.BAD_CHARACTER;
            currentTokenEnd = ++currentPosition;
        }
    }

    /**
     * Attempts to consume a ${...} template expression at the current position.
     * On success, sets currentTokenType/currentTokenEnd and advances currentPosition.
     *
     * @return true if a template expression was consumed
     */
    private boolean tryConsumeTemplateExpression() {
        if (currentPosition + 1 >= endOffset) return false;
        if (buffer.charAt(currentPosition) != '$') return false;
        if (buffer.charAt(currentPosition + 1) != '{') return false;

        currentPosition += 2;
        int depth = 1;
        while (currentPosition < endOffset && depth > 0) {
            char c = buffer.charAt(currentPosition);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            currentPosition++;
        }
        currentTokenType = SharedTokenTypes.TEMPLATE_EXPRESSION;
        currentTokenEnd = currentPosition;
        return true;
    }

    /**
     * Returns true if the current position matches any SQLX top-level block keyword.
     */
    private boolean matchesAnyBlockKeyword() {
        return matchKeyword(CONFIG_KEYWORD)
                || matchKeyword(JS_KEYWORD)
                || matchKeyword(PRE_OPERATIONS_KEYWORD)
                || matchKeyword(POST_OPERATIONS_KEYWORD);
    }

    private boolean matchKeyword(String keyword) {
        if (currentPosition + keyword.length() > endOffset) {
            return false;
        }

        for (int i = 0; i < keyword.length(); i++) {
            if (buffer.charAt(currentPosition + i) != keyword.charAt(i)) {
                return false;
            }
        }

        int nextPos = currentPosition + keyword.length();
        if (nextPos < endOffset) {
            char nextChar = buffer.charAt(nextPos);
            if (Character.isLetterOrDigit(nextChar) || nextChar == '_') {
                return false;
            }
        }

        return true;
    }

    private boolean isStartOfLine() {
        if (currentPosition == 0) {
            return true;
        }

        int pos = currentPosition - 1;
        while (pos >= 0) {
            char c = buffer.charAt(pos);
            if (c == '\n') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
            pos--;
        }

        return true;
    }

    private void skipWhitespace() {
        while (currentPosition < endOffset &&
                Character.isWhitespace(buffer.charAt(currentPosition))) {
            currentPosition++;
        }
    }
}
