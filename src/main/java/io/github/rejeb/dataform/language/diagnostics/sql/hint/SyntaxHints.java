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
import io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorKind;
import io.github.rejeb.dataform.language.util.BigQueryKeywords;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Syntax errors. BigQuery names the token it did not expect; what was meant is read from the text
 * just before it: a comma too many before a clause, a comma missing between two expressions, a
 * parenthesis never closed, a typographic quote pasted from a document.
 */
final class SyntaxHints implements SqlHintProvider {

    private static final Set<String> CLAUSE_KEYWORDS = Set.of("FROM", "WHERE", "GROUP", "HAVING", "QUALIFY",
            "WINDOW", "ORDER", "LIMIT", "UNION", "INTERSECT", "EXCEPT", "JOIN", "LEFT", "RIGHT", "INNER", "FULL",
            "CROSS", "ON", "USING", ")");
    private static final Set<String> STATEMENT_KEYWORDS = Set.of("SELECT", "WITH", "INSERT", "UPDATE", "DELETE",
            "MERGE", "CREATE", "DECLARE", "SET");

    @Override
    public @NotNull Set<BigQueryErrorKind> kinds() {
        return Set.of(BigQueryErrorKind.UNEXPECTED_TOKEN, BigQueryErrorKind.EXPECTED_END_OF_INPUT,
                BigQueryErrorKind.UNCLOSED_PARENTHESIS, BigQueryErrorKind.UNCLOSED_STRING,
                BigQueryErrorKind.UNEXPECTED_END, BigQueryErrorKind.ILLEGAL_CHARACTER);
    }

    @Override
    public @NotNull SqlHint hint(@NotNull SqlErrorContext context) {
        return switch (context.error().kind()) {
            case UNEXPECTED_TOKEN, EXPECTED_END_OF_INPUT -> unexpected(context);
            case UNCLOSED_PARENTHESIS -> unclosed(context);
            case UNCLOSED_STRING -> new SqlHint("Close the string on the same line, or use a triple-quoted "
                    + "string ('''…''') for text spanning lines.", List.of());
            case UNEXPECTED_END -> new SqlHint("The query ends too early: an expression, a clause or a "
                    + "closing ')' is missing.", List.of());
            case ILLEGAL_CHARACTER -> illegal(context);
            default -> SqlHint.NONE;
        };
    }

    private static @NotNull SqlHint unexpected(@NotNull SqlErrorContext context) {
        String token = context.rangeText();
        if (token.isEmpty()) return SqlHint.NONE;
        String upper = token.toUpperCase(Locale.ROOT);
        int previous = previousNonWhitespace(context.text(), context.range().getStartOffset());
        char before = previous < 0 ? 0 : context.text().charAt(previous);
        if (before == ',' && CLAUSE_KEYWORDS.contains(upper)) {
            return new SqlHint("Remove the ',' before " + upper + ".",
                    List.of(new SqlFix("Remove the extra ','", TextRange.from(previous, 1), "")));
        }
        if (context.error().kind() == BigQueryErrorKind.EXPECTED_END_OF_INPUT && STATEMENT_KEYWORDS.contains(upper)) {
            return new SqlHint("A second statement starts here, but a table's query must be a single statement: "
                    + "combine the queries with UNION ALL, or remove one.", List.of());
        }
        if (context.error().qualifier() != null) {
            return new SqlHint("BigQuery expected " + context.error().qualifier() + " before '" + token + "'.", List.of());
        }
        if (startsAnExpression(token) && endsAnExpression(before) && !BigQueryKeywords.isReserved(token)) {
            return new SqlHint("A ',' may be missing before '" + token + "'.",
                    List.of(new SqlFix("Insert ',' before '" + token + "'", TextRange.from(previous + 1, 0), ",")));
        }
        if (upper.equals(")")) {
            return new SqlHint("This ')' closes nothing: remove it, or add the '(' it belongs to.", List.of());
        }
        return SqlHint.NONE;
    }

    private static @NotNull SqlHint unclosed(@NotNull SqlErrorContext context) {
        int open = unmatchedParenthesis(context.text(), context.scope().sqlStart(), context.range().getStartOffset());
        if (open < 0) return new SqlHint("A ')' is missing.", List.of());
        String token = context.rangeText();
        String before = token.isEmpty() ? "" : " before " + token;
        List<SqlFix> fixes = token.isEmpty()
                ? List.of()
                : List.of(new SqlFix("Insert ')'" + before, TextRange.from(context.range().getStartOffset(), 0), ")"));
        return new SqlHint("The '(' opened on line " + lineOf(context.text(), open) + " is not closed" + before + ".", fixes);
    }

    private static @NotNull SqlHint illegal(@NotNull SqlErrorContext context) {
        String character = context.rangeText();
        String plain = switch (character) {
            case "\u201C", "\u201D" -> "\"";
            case "\u2018", "\u2019" -> "'";
            default -> null;
        };
        if (plain == null) {
            return new SqlHint("Remove this character, or put it inside a string.", List.of());
        }
        return new SqlHint("Typographic quotes are not SQL quotes.",
                List.of(new SqlFix("Replace with a plain quote", context.range(), plain)));
    }

    private static int previousNonWhitespace(@NotNull CharSequence text, int before) {
        int at = before - 1;
        while (at >= 0 && Character.isWhitespace(text.charAt(at))) at--;
        return at;
    }

    private static boolean startsAnExpression(@NotNull String token) {
        char first = token.charAt(0);
        return Character.isLetterOrDigit(first) || first == '_' || first == '`' || first == '\'' || first == '"';
    }

    private static boolean endsAnExpression(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ')' || c == '`' || c == '\'' || c == '"' || c == ']';
    }

    private static int unmatchedParenthesis(@NotNull CharSequence text, int from, int before) {
        String sql = text.toString();
        int end = Math.min(before, sql.length());
        Deque<Integer> open = new ArrayDeque<>();
        char quote = 0;
        for (int i = Math.max(0, from); i < end; i++) {
            char c = sql.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '#' || (c == '-' && sql.startsWith("--", i))) {
                int lineEnd = sql.indexOf('\n', i);
                i = lineEnd < 0 || lineEnd > end ? end : lineEnd;
            } else if (c == '/' && sql.startsWith("/*", i)) {
                int commentEnd = sql.indexOf("*/", i + 2);
                i = commentEnd < 0 || commentEnd > end ? end : commentEnd + 1;
            } else if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '(') {
                open.push(i);
            } else if (c == ')' && !open.isEmpty()) {
                open.pop();
            }
        }
        return open.isEmpty() ? -1 : open.peek();
    }

    private static int lineOf(@NotNull CharSequence text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }
}
