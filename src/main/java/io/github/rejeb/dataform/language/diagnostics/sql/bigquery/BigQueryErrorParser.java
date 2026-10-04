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
package io.github.rejeb.dataform.language.diagnostics.sql.bigquery;

import io.github.rejeb.dataform.language.diagnostics.MessageRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.rejeb.dataform.language.diagnostics.sql.bigquery.BigQueryErrorKind.*;

/**
 * Takes apart the messages BigQuery rejects a query with. The analyzer writes them in a small
 * number of fixed shapes, ending with the position of the error in the query sent, and often with
 * a suggestion of the name that was meant; a message of any other shape is kept whole.
 */
public final class BigQueryErrorParser {

    private static final Pattern POSITION = Pattern.compile("\\s*at \\[(\\d+):(\\d+)]$");
    private static final Pattern SUGGESTION = Pattern.compile(";\\s*Did you mean (.+?)\\?$");
    private static final String QUERY_ERROR_PREFIX = "Query error: ";
    private static final String SYNTAX_PREFIX = "Syntax error: ";
    private static final String TOKEN = "(?:keyword (?<keyword>\\S+)"
            + "|identifier \"?(?<identifier>[^\"]+?)\"?"
            + "|\"(?<punctuation>.+)\""
            + "|(?<end>end of (?:script|statement|input))"
            + "|(?<other>.+))";

    private static final List<MessageRule<BigQueryErrorKind>> SYNTAX_RULES = List.of(
            MessageRule.of(UNEXPECTED_END, "^Unexpected end of (?:script|statement|input)$"),
            MessageRule.of(UNCLOSED_STRING, "^Unclosed (?:triple-quoted )?(?:string|bytes) literal$"),
            MessageRule.of(ILLEGAL_CHARACTER, "^Illegal input character \"(?<other>.+)\"$"),
            MessageRule.of(EXPECTED_END_OF_INPUT, "^Expected end of input but got " + TOKEN + "$"),
            MessageRule.of(UNCLOSED_PARENTHESIS, "^Expected \"\\)\"(?: or .+?)? but got " + TOKEN + "$"),
            MessageRule.of(UNEXPECTED_TOKEN, "^Expected (?<qualifier>.+?) but got " + TOKEN + "$"),
            MessageRule.of(UNEXPECTED_TOKEN, "^Unexpected " + TOKEN + "$"));

    private static final List<MessageRule<BigQueryErrorKind>> RULES = List.of(
            MessageRule.of(UNRECOGNIZED_NAME, "^Unrecognized name: (?<subject>.+)$"),
            MessageRule.of(NAME_NOT_FOUND_INSIDE, "^Name (?<subject>\\S+) not found inside (?<qualifier>\\S+)$"),
            MessageRule.of(FIELD_NOT_FOUND, "^Field name (?<subject>\\S+) does not exist in (?<qualifier>.+)$"),
            MessageRule.of(FUNCTION_NOT_FOUND, "^Function not found: (?<subject>.+)$"),
            MessageRule.of(TABLE_NOT_FOUND, "^Not found: (?:Table|View|Dataset) (?<subject>\\S+?)"
                    + "(?: was not found in location (?<qualifier>\\S+?))?\\.?$"),
            MessageRule.of(TABLE_NOT_FOUND, "^Table not found: (?<subject>\\S+)$"),
            MessageRule.of(MISSING_DATASET, "^Table name \"?(?<subject>[^\"\\s]+)\"? missing dataset "
                    + "while no default dataset is set in the request\\.?$"),
            MessageRule.of(AMBIGUOUS_COLUMN, "^Column (?:name )?(?<subject>\\S+) is ambiguous$"),
            MessageRule.of(NOT_GROUPED_OR_AGGREGATED, "^(?<qualifier>.+?) expression references (?:column )?"
                    + "(?<subject>\\S+) which is neither grouped nor aggregated$"),
            MessageRule.of(AGGREGATE_NOT_ALLOWED, "^Aggregate function (?<subject>\\S+) not allowed in "
                    + "(?<qualifier>.+?)(?: clause)?$"),
            MessageRule.of(ANALYTIC_NOT_ALLOWED, "^Analytic function not allowed in (?<qualifier>.+?)(?: clause)?$"),
            MessageRule.of(DUPLICATE_COLUMN, "^Duplicate column names in the result are not supported\\. "
                    + "Found duplicate\\(s\\): (?<subject>[^,\\s]+).*$"),
            MessageRule.of(FIELD_ACCESS_ON_ARRAY, "^Cannot access field (?<subject>\\S+) on a value with type "
                    + "(?<qualifier>.+)$"),
            MessageRule.of(NO_MATCHING_SIGNATURE, "^No matching signature for (?:aggregate |analytic )?"
                    + "(?:function|operator) (?<subject>\\S+?)"
                    + "(?<qualifier>(?: for argument types?:| Argument types?:).*)?$"),
            MessageRule.of(ACCESS_DENIED, "^Access Denied: (?<subject>.+)$"));

    private BigQueryErrorParser() {
    }

    /**
     * Takes a BigQuery error message apart. Whitespace is collapsed first, as the analyzer spreads
     * some messages over several lines.
     */
    public static @NotNull BigQueryError parse(@NotNull String rawMessage) {
        String message = rawMessage.replaceAll("\\s+", " ").trim();
        if (message.startsWith(QUERY_ERROR_PREFIX)) message = message.substring(QUERY_ERROR_PREFIX.length());
        int line = 0;
        int column = 0;
        Matcher position = POSITION.matcher(message);
        if (position.find()) {
            line = Integer.parseInt(position.group(1));
            column = Integer.parseInt(position.group(2));
            message = message.substring(0, position.start()).trim();
        }
        String suggestion = null;
        Matcher suggested = SUGGESTION.matcher(message);
        if (suggested.find()) {
            suggestion = unquote(suggested.group(1));
            message = message.substring(0, suggested.start()).trim();
        }
        boolean syntax = message.startsWith(SYNTAX_PREFIX);
        String body = syntax ? message.substring(SYNTAX_PREFIX.length()) : message;
        MessageRule.Match<BigQueryErrorKind> match = MessageRule.firstMatch(syntax ? SYNTAX_RULES : RULES, body);
        if (match == null) {
            return new BigQueryError(syntax ? SYNTAX_ERROR : OTHER, message, null, null, suggestion, line, column);
        }
        return new BigQueryError(match.kind(), message, subjectOf(match.kind(), match.matcher()),
                qualifierOf(match.matcher()), suggestion, line, column);
    }

    private static @Nullable String subjectOf(@NotNull BigQueryErrorKind kind, @NotNull Matcher matcher) {
        String subject = firstGroup(matcher, "subject", "keyword", "identifier", "punctuation", "other");
        if (subject == null) return null;
        String unquoted = unquote(subject);
        return kind == TABLE_NOT_FOUND ? unquoted.replaceFirst(":", ".") : unquoted;
    }

    private static @Nullable String qualifierOf(@NotNull Matcher matcher) {
        String qualifier = group(matcher, "qualifier");
        return qualifier == null || qualifier.isBlank() ? null : qualifier.trim();
    }

    private static @Nullable String firstGroup(@NotNull Matcher matcher, @NotNull String... names) {
        for (String name : names) {
            String value = group(matcher, name);
            if (value != null) return value;
        }
        return null;
    }

    private static @Nullable String group(@NotNull Matcher matcher, @NotNull String name) {
        try {
            return matcher.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Strips the backticks or quotes around a name quoted in a BigQuery message.
     *
     * @param text the name as the message writes it
     * @return the name, trimmed and without its quotes
     */
    public static @NotNull String unquote(@NotNull String text) {
        String trimmed = text.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if (first == last && (first == '`' || first == '"' || first == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }
}
