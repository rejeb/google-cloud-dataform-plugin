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
package io.github.rejeb.dataform.language.evaluation;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a Dataform template expression into the parts whose value is deterministic.
 *
 * <p>Only the graph functions of the Dataform context ({@code ref}, {@code resolve}, {@code self},
 * {@code name}, {@code schema}, {@code database}) resolve to a fixed value. Anything depending on the
 * execution mode ({@code incremental}, {@code when}) or defining actions ({@code publish},
 * {@code operate}, {@code assert}, {@code declare}, {@code test}) has no single value, so such an
 * expression is never folded as a whole. Its nested {@code ${...}} substitutions are still folded
 * when they are deterministic on their own.</p>
 */
public final class DataformTemplateSyntax {

    private static final Pattern NON_DETERMINISTIC_CALL = Pattern.compile(
            "(?<![\\w$.])(?:ctx\\s*\\.\\s*)?(when|incremental|publish|operate|assert|assertions|declare|test|session)\\s*\\(");

    private static final String PREFIX = "${";
    private static final String SUFFIX = "}";

    private DataformTemplateSyntax() {
    }

    /**
     * Tells whether the whole expression resolves to a single deterministic value.
     */
    public static boolean isDeterministic(@NotNull String source) {
        return !NON_DETERMINISTIC_CALL.matcher(source).find();
    }

    /**
     * Returns the parts of a {@code ${...}} expression that should be folded, as ranges relative to
     * the given text.
     *
     * <p>The whole expression when it is deterministic, otherwise the deterministic substitutions
     * nested in it, at any depth.</p>
     */
    @NotNull
    public static List<TextRange> foldableRanges(@NotNull String templateText) {
        List<TextRange> ranges = new ArrayList<>();
        collectFoldable(templateText, 0, ranges);
        return ranges;
    }

    /**
     * Returns the expression of a {@code ${...}} text, without the delimiters.
     */
    @NotNull
    public static String sourceOf(@NotNull String templateText) {
        if (templateText.startsWith(PREFIX) && templateText.endsWith(SUFFIX)) {
            return templateText.substring(PREFIX.length(), templateText.length() - SUFFIX.length());
        }
        return templateText;
    }

    private static void collectFoldable(@NotNull String templateText, int offset, @NotNull List<TextRange> ranges) {
        String source = sourceOf(templateText);
        if (isDeterministic(source)) {
            ranges.add(new TextRange(offset, offset + templateText.length()));
            return;
        }
        for (TextRange nested : substitutionsIn(source, PREFIX.length())) {
            String nestedText = templateText.substring(nested.getStartOffset(), nested.getEndOffset());
            collectFoldable(nestedText, offset + nested.getStartOffset(), ranges);
        }
    }

    /**
     * Finds the {@code ${...}} substitutions of the template literals contained in an expression.
     */
    @NotNull
    private static List<TextRange> substitutionsIn(@NotNull String expression, int offset) {
        List<TextRange> ranges = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == '\'' || current == '"') {
                index = skipString(expression, index, current);
            } else if (current == '`') {
                index = scanTemplateLiteral(expression, index, offset, ranges);
            } else {
                index++;
            }
        }
        return ranges;
    }

    private static int scanTemplateLiteral(@NotNull String text, int start, int offset, @NotNull List<TextRange> ranges) {
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == '`') {
                return index + 1;
            } else if (current == '$' && index + 1 < text.length() && text.charAt(index + 1) == '{') {
                int end = matchingBrace(text, index + 1);
                if (end < 0) {
                    return text.length();
                }
                ranges.add(new TextRange(offset + index, offset + end));
                index = end;
            } else {
                index++;
            }
        }
        return index;
    }

    private static int matchingBrace(@NotNull String text, int open) {
        int depth = 0;
        int index = open;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == '\'' || current == '"') {
                index = skipString(text, index, current);
            } else if (current == '`') {
                index = skipTemplateLiteral(text, index);
            } else if (current == '{') {
                depth++;
                index++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
                index++;
            } else {
                index++;
            }
        }
        return -1;
    }

    private static int skipTemplateLiteral(@NotNull String text, int start) {
        return scanTemplateLiteral(text, start, 0, new ArrayList<>());
    }

    private static int skipString(@NotNull String text, int start, char quote) {
        int index = start + 1;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == quote) {
                return index + 1;
            } else {
                index++;
            }
        }
        return index;
    }
}
