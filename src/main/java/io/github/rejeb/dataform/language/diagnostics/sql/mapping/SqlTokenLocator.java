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
package io.github.rejeb.dataform.language.diagnostics.sql.mapping;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.text.CharArrayUtil;
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds the token an error points at in SQL text.
 *
 * <p>BigQuery reports a position, not a range, and the column it counts is not always the one
 * computed on this side, as the analyzer may count bytes where the editor counts characters. So
 * when the error names the token it is about, the token found at the position is checked against
 * that name, and the name is looked for elsewhere when it does not match.</p>
 */
public final class SqlTokenLocator {

    private SqlTokenLocator() {
    }

    /**
     * The range of the token an error points at. When a name is expected and the token there is
     * another one, the name is looked for on the same line, the occurrence nearest the offset
     * winning, then in the whole text when it occurs there exactly once.
     *
     * @return the range, empty for an error pointing past the last token, or {@code null} when the
     *         expected name is nowhere to be found
     */
    public static @Nullable TextRange locate(@NotNull CharSequence text, int offset, @Nullable String expected) {
        if (offset < 0 || offset > text.length()) return null;
        TextRange token = tokenAt(text, offset);
        if (expected == null || expected.isBlank()) return token;
        String name = BigQueryErrorParser.unquote(expected);
        TextRange match = matching(text, token, name);
        if (match != null) return match;
        TextRange onLine = nearest(occurrences(text, name, lineStart(text, offset), CharArrayUtil.shiftForwardUntil(text, offset, "\n\r")), offset);
        if (onLine != null) return onLine;
        List<TextRange> anywhere = occurrences(text, name, 0, text.length());
        return anywhere.size() == 1 ? anywhere.getFirst() : null;
    }

    /**
     * Every place a name is written as a whole word between two offsets, ignoring case. A name
     * written between backticks is returned with them.
     */
    public static @NotNull List<TextRange> occurrences(@NotNull CharSequence text,
                                                       @NotNull String name,
                                                       int from,
                                                       int to) {
        List<TextRange> found = new ArrayList<>();
        if (name.isEmpty()) return found;
        String window = text.subSequence(from, to).toString().toLowerCase(Locale.ROOT);
        String target = name.toLowerCase(Locale.ROOT);
        int at = window.indexOf(target);
        while (at >= 0) {
            int start = from + at;
            int end = start + target.length();
            boolean backticked = start > 0 && end < text.length()
                    && text.charAt(start - 1) == '`' && text.charAt(end) == '`';
            if (backticked) {
                found.add(new TextRange(start - 1, end + 1));
            } else if (isBoundary(text, start - 1) && isBoundary(text, end)) {
                found.add(new TextRange(start, end));
            }
            at = window.indexOf(target, at + 1);
        }
        return found;
    }

    static @NotNull TextRange tokenAt(@NotNull CharSequence text, int offset) {
        if (offset >= text.length() || Character.isWhitespace(text.charAt(offset))) {
            int back = Math.min(offset, text.length());
            while (back > 0 && Character.isWhitespace(text.charAt(back - 1))) back--;
            return TextRange.from(back, 0);
        }
        char current = text.charAt(offset);
        if (current == '`' || current == '\'' || current == '"') return quoted(text, offset, current);
        if (isWordChar(current)) {
            int start = offset;
            while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
            int end = offset;
            while (end < text.length() && isWordChar(text.charAt(end))) end++;
            return new TextRange(start, end);
        }
        return TextRange.from(offset, 1);
    }

    private static @NotNull TextRange quoted(@NotNull CharSequence text, int offset, char quote) {
        int end = offset + 1;
        while (end < text.length() && text.charAt(end) != quote && text.charAt(end) != '\n') end++;
        boolean closed = end < text.length() && text.charAt(end) == quote;
        return new TextRange(offset, closed ? end + 1 : end);
    }

    private static @Nullable TextRange matching(@NotNull CharSequence text,
                                                @NotNull TextRange token,
                                                @NotNull String name) {
        if (token.isEmpty()) return null;
        if (name.indexOf('.') < 0) {
            return BigQueryErrorParser.unquote(token.subSequence(text).toString()).equalsIgnoreCase(name) ? token : null;
        }
        TextRange path = pathFrom(text, token.getStartOffset());
        String written = path.subSequence(text).toString().replace("`", "");
        return written.equalsIgnoreCase(name) ? path : null;
    }

    private static @NotNull TextRange pathFrom(@NotNull CharSequence text, int start) {
        int end = start;
        while (end < text.length()) {
            TextRange part = tokenAt(text, end);
            if (part.isEmpty() || part.getStartOffset() != end) break;
            char first = text.charAt(end);
            if (!isWordChar(first) && first != '`') break;
            end = part.getEndOffset();
            boolean dotted = end + 1 < text.length() && text.charAt(end) == '.'
                    && (isWordChar(text.charAt(end + 1)) || text.charAt(end + 1) == '`');
            if (!dotted) break;
            end++;
        }
        return new TextRange(start, end);
    }

    private static @Nullable TextRange nearest(@NotNull List<TextRange> ranges, int offset) {
        return ranges.stream()
                .min(Comparator.comparingInt(range -> Math.abs(range.getStartOffset() - offset)))
                .orElse(null);
    }

    private static int lineStart(@NotNull CharSequence text, int offset) {
        int at = Math.min(offset, text.length());
        while (at > 0 && text.charAt(at - 1) != '\n' && text.charAt(at - 1) != '\r') at--;
        return at;
    }

    private static boolean isBoundary(@NotNull CharSequence text, int index) {
        return index < 0 || index >= text.length() || !isWordChar(text.charAt(index));
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
