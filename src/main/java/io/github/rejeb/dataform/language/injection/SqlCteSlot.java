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
package io.github.rejeb.dataform.language.injection;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells whether a template hole stands where a common table expression is expected: right after
 * {@code WITH}, or after the comma closing another definition of the same {@code WITH} list.
 *
 * <p>Includes often write a whole definition, {@code WITH ${helper(ref, "x")} SELECT ...}. Filling
 * such a hole with an expression leaves {@code WITH NULL SELECT ...}, which no parser reads as a
 * query: the main select list is lost, and with it every column the file declares.</p>
 */
final class SqlCteSlot {

    private static final String WITH = "WITH";
    private static final String RECURSIVE = "RECURSIVE";
    private static final String SELECT = "SELECT";
    private static final String COMMA = ",";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String OPAQUE = "?";

    private SqlCteSlot() {
    }

    /**
     * Whether a hole preceded by this SQL text opens a common table expression.
     *
     * @param sqlBefore the text of the SQL block before the hole, earlier holes included as written
     * @return whether the hole stands for a definition of a {@code WITH} list
     */
    static boolean opensACte(@NotNull CharSequence sqlBefore) {
        List<String> tokens = tokens(sqlBefore);
        if (tokens.isEmpty()) return false;
        String last = tokens.get(tokens.size() - 1);
        if (last.equalsIgnoreCase(WITH)) return true;
        if (last.equalsIgnoreCase(RECURSIVE)) {
            return tokens.size() > 1 && tokens.get(tokens.size() - 2).equalsIgnoreCase(WITH);
        }
        return last.equals(COMMA) && commaSeparatesCtes(tokens);
    }

    private static boolean commaSeparatesCtes(@NotNull List<String> tokens) {
        int depth = 0;
        for (int i = tokens.size() - 2; i >= 0; i--) {
            String token = tokens.get(i);
            if (token.equals(CLOSE)) {
                depth++;
            } else if (token.equals(OPEN)) {
                if (--depth < 0) return false;
            } else if (depth == 0 && token.equalsIgnoreCase(SELECT)) {
                return false;
            } else if (depth == 0 && token.equalsIgnoreCase(WITH)) {
                return true;
            }
        }
        return false;
    }

    private static @NotNull List<String> tokens(@NotNull CharSequence text) {
        List<String> tokens = new ArrayList<>();
        int at = 0;
        int length = text.length();
        while (at < length) {
            char current = text.charAt(at);
            if (Character.isWhitespace(current)) {
                at++;
            } else if (current == '#' || startsWith(text, at, "--")) {
                at = lineEnd(text, at);
            } else if (startsWith(text, at, "/*")) {
                at = blockEnd(text, at);
            } else if (startsWith(text, at, "${")) {
                at = templateEnd(text, at);
                tokens.add(OPAQUE);
            } else if (current == '\'' || current == '"' || current == '`') {
                at = quotedEnd(text, at, current);
                tokens.add(OPAQUE);
            } else if (Character.isLetterOrDigit(current) || current == '_') {
                int start = at;
                while (at < length && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) at++;
                tokens.add(text.subSequence(start, at).toString());
            } else {
                tokens.add(String.valueOf(current));
                at++;
            }
        }
        return tokens;
    }

    private static boolean startsWith(@NotNull CharSequence text, int at, @NotNull String prefix) {
        if (at + prefix.length() > text.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (text.charAt(at + i) != prefix.charAt(i)) return false;
        }
        return true;
    }

    private static int lineEnd(@NotNull CharSequence text, int at) {
        while (at < text.length() && text.charAt(at) != '\n') at++;
        return at;
    }

    private static int blockEnd(@NotNull CharSequence text, int at) {
        for (int i = at + 2; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') return i + 2;
        }
        return text.length();
    }

    private static int templateEnd(@NotNull CharSequence text, int at) {
        int depth = 0;
        for (int i = at + 1; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '{') depth++;
            if (current == '}' && --depth == 0) return i + 1;
        }
        return text.length();
    }

    private static int quotedEnd(@NotNull CharSequence text, int at, char quote) {
        for (int i = at + 1; i < text.length(); i++) {
            if (text.charAt(i) == '\\') {
                i++;
            } else if (text.charAt(i) == quote) {
                return i + 1;
            }
        }
        return text.length();
    }
}
