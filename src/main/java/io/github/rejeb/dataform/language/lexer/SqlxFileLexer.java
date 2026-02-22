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

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.currentPosition = startOffset;
        this.state = initialState;
        this.braceDepth = 0;
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
        // Skip whitespace
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
        // Skip optional whitespace after keyword
        while (endPos < endOffset && Character.isWhitespace(buffer.charAt(endPos))) {
            endPos++;
        }
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

        int start = currentPosition;

        // Consume characters and track brace depth
        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '{') {
                braceDepth++;
                currentPosition++;
            } else if (c == '}') {
                braceDepth--;
                currentPosition++;

                if (braceDepth == 0) {
                    currentTokenType = tokenType;
                    currentTokenEnd = currentPosition;
                    state = YYINITIAL;
                    return;
                }
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

        int start = currentPosition;


        if (buffer.charAt(currentPosition) == '$' &&
                currentPosition + 1 < endOffset &&
                buffer.charAt(currentPosition + 1) == '{') {

            currentPosition += 2; // Skip ${

            while (currentPosition < endOffset && buffer.charAt(currentPosition) != '}') {
                currentPosition++;
            }

            if (currentPosition < endOffset) {
                currentPosition++; // Include closing }
            }

            currentTokenType = SharedTokenTypes.TEMPLATE_EXPRESSION;
            currentTokenEnd = currentPosition;
            return;
        }

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '$' && currentPosition + 1 < endOffset && buffer.charAt(currentPosition + 1) == '{') {
                // Return content before template expression if any
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
                braceDepth--;
                currentPosition++;

                if (braceDepth == 0) {
                    currentTokenType = tokenType;
                    currentTokenEnd = currentPosition;
                    state = YYINITIAL;
                    return;
                }
            } else {
                currentPosition++;
            }
        }

        // EOF reached
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

        // Check if we're at the start of a line
        boolean atStartOfLine = isStartOfLine();

        if (atStartOfLine) {
            int savedPos = currentPosition;
            int wsStart = currentPosition;

            // Skip whitespace
            while (currentPosition < endOffset &&
                    Character.isWhitespace(buffer.charAt(currentPosition)) &&
                    buffer.charAt(currentPosition) != '\n') {
                currentPosition++;
            }

            // Check for CONFIG keyword
            if (currentPosition < endOffset && (matchKeyword(CONFIG_KEYWORD) ||
                    matchKeyword(JS_KEYWORD) ||
                    matchKeyword(PRE_OPERATIONS_KEYWORD) ||
                    matchKeyword(POST_OPERATIONS_KEYWORD))) {
                // Return whitespace token if there was any
                if (wsStart < savedPos + (currentPosition - savedPos)) {
                    currentPosition = savedPos;
                    skipWhitespace();
                    if (currentPosition > savedPos) {
                        currentTokenType = TokenType.WHITE_SPACE;
                        currentTokenEnd = currentPosition;
                        return;
                    }
                }
                currentPosition = savedPos;
                state = YYINITIAL;
                locateTokenInitial();
                return;
            }

            // Restore position
            currentPosition = savedPos;
        }

        // Check for template expression ${...}
        if (buffer.charAt(currentPosition) == '$' &&
                currentPosition + 1 < endOffset &&
                buffer.charAt(currentPosition + 1) == '{') {

            currentPosition += 2; // Skip ${

            while (currentPosition < endOffset && buffer.charAt(currentPosition) != '}') {
                currentPosition++;
            }

            if (currentPosition < endOffset) {
                currentPosition++; // Include closing }
            }

            currentTokenType = SharedTokenTypes.TEMPLATE_EXPRESSION;
            currentTokenEnd = currentPosition;
            return;
        }

        // Consume SQL content until we hit $ or end of buffer
        int start = currentPosition;

        while (currentPosition < endOffset) {
            char c = buffer.charAt(currentPosition);

            if (c == '$') {
                // Check if it's part of a template expression
                if (currentPosition + 1 < endOffset && buffer.charAt(currentPosition + 1) == '{') {
                    break;
                }
                currentPosition++;
            } else if (c == '\n') {
                currentPosition++;
                // Check if next line starts with config or js keyword
                if (currentPosition < endOffset) {
                    int savedPos = currentPosition;

                    // Skip whitespace at start of line
                    while (currentPosition < endOffset &&
                            Character.isWhitespace(buffer.charAt(currentPosition)) &&
                            buffer.charAt(currentPosition) != '\n') {
                        currentPosition++;
                    }

                    if (matchKeyword(CONFIG_KEYWORD) || matchKeyword(JS_KEYWORD) ||
                            matchKeyword(PRE_OPERATIONS_KEYWORD) || matchKeyword(POST_OPERATIONS_KEYWORD)) {
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

    private boolean matchKeyword(String keyword) {
        if (currentPosition + keyword.length() > endOffset) {
            return false;
        }

        for (int i = 0; i < keyword.length(); i++) {
            if (buffer.charAt(currentPosition + i) != keyword.charAt(i)) {
                return false;
            }
        }

        // Check that keyword is not part of a larger identifier
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

        // Look back to see if we're at start of line
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
