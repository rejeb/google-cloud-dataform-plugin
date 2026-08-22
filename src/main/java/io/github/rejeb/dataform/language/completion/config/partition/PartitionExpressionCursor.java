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
package io.github.rejeb.dataform.language.completion.config.partition;

import com.intellij.codeInsight.completion.CompletionUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells what a caret placed in a {@code partitionBy} expression is editing: the expression as a
 * whole, the column a partitioning function takes, or its truncation unit.
 *
 * <p>The expression is read as text rather than parsed as SQL, the forms it can take being few and
 * fixed. Reading it this way also survives the half written state a caret in the middle of an
 * argument leaves it in.</p>
 *
 * @param target     what the caret is editing
 * @param prefix     the text already typed for it, which the proposals are matched against
 * @param tailLength the characters left after the caret that a proposal replaces
 * @param form       the form the caret edits an argument of, null when editing the whole expression
 */
public record PartitionExpressionCursor(@NotNull Target target,
                                        @NotNull String prefix,
                                        int tailLength,
                                        @Nullable PartitionForm form) {

    /**
     * What the caret is editing.
     */
    public enum Target {
        /** The expression as a whole, which the partitioning forms are proposed for. */
        FORM,
        /** The column argument of a partitioning function. */
        COLUMN,
        /** The truncation unit of a partitioning function. */
        GRANULARITY,
        /** The bounds a range partitioning function buckets its column into. */
        BOUNDARIES,
        /** An argument nothing can be proposed for, such as a range bound. */
        NONE
    }

    /**
     * The cursor of a caret editing an expression from its start, nothing being written yet.
     */
    @NotNull
    public static PartitionExpressionCursor empty() {
        return new PartitionExpressionCursor(Target.FORM, "", 0, null);
    }

    /**
     * Reads the expression held by a string literal of the config block at the given index of its
     * text, quotes included, dropping the identifier the completion framework wrote at the caret.
     */
    @NotNull
    public static PartitionExpressionCursor inLiteral(@NotNull String literalText, int caretInText) {
        int contentStart = literalText.isEmpty() || !isQuote(literalText.charAt(0)) ? 0 : 1;
        int contentEnd = literalText.length() > contentStart
                && isQuote(literalText.charAt(literalText.length() - 1))
                ? literalText.length() - 1
                : literalText.length();
        String content = literalText.substring(contentStart, Math.max(contentStart, contentEnd));
        int caret = Math.max(0, Math.min(caretInText - contentStart, content.length()));
        return at(withoutDummyIdentifier(content, caret), caret);
    }

    /**
     * Drops the identifier the completion framework wrote at the caret, trailing space included:
     * left in place, it would be read as an argument of its own and its space would be taken for a
     * character the caret has to overwrite.
     */
    @NotNull
    private static String withoutDummyIdentifier(@NotNull String content, int caret) {
        for (String dummy : List.of(CompletionUtilCore.DUMMY_IDENTIFIER,
                CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
            if (content.startsWith(dummy, caret)) {
                return content.substring(0, caret) + content.substring(caret + dummy.length());
            }
        }
        return content;
    }

    private static boolean isQuote(char character) {
        return character == '"' || character == '\'' || character == '`';
    }

    /**
     * Reads the given expression at the given caret index, both being free of the identifier the
     * completion framework inserts at the caret.
     */
    @NotNull
    public static PartitionExpressionCursor at(@NotNull String expression, int caret) {
        int position = Math.max(0, Math.min(caret, expression.length()));
        int open = expression.indexOf('(');
        PartitionForm form = open < 0
                ? null
                : PartitionForm.byFunction(expression.substring(0, open).trim());
        if (form == null || position <= open) {
            return whole(expression, position);
        }
        int close = closingParenthesis(expression, open);
        if (position > close) {
            return whole(expression, position);
        }
        return argument(expression, position, form, arguments(expression, open, close));
    }

    @NotNull
    private static PartitionExpressionCursor whole(@NotNull String expression, int caret) {
        return new PartitionExpressionCursor(
                Target.FORM, expression.substring(0, caret), expression.length() - caret, null);
    }

    @NotNull
    private static PartitionExpressionCursor argument(@NotNull String expression,
                                                      int caret,
                                                      @NotNull PartitionForm form,
                                                      @NotNull List<int[]> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            int[] range = arguments.get(index);
            if (caret < range[0] || caret > range[1]) {
                continue;
            }
            if (isNested(expression, range[0], caret)) {
                return new PartitionExpressionCursor(Target.NONE, "", 0, form);
            }
            int start = Math.min(caret, skipSpaces(expression, range[0], range[1]));
            int end = Math.max(caret, trimSpaces(expression, start, range[1]));
            return new PartitionExpressionCursor(targetOf(form, index),
                    expression.substring(start, caret), end - caret, form);
        }
        return new PartitionExpressionCursor(Target.NONE, "", 0, form);
    }

    @NotNull
    private static Target targetOf(@NotNull PartitionForm form, int argumentIndex) {
        if (argumentIndex == 0) {
            return Target.COLUMN;
        }
        if (argumentIndex != 1) {
            return Target.NONE;
        }
        if (!form.granularities().isEmpty()) {
            return Target.GRANULARITY;
        }
        return form.boundaries().isEmpty() ? Target.NONE : Target.BOUNDARIES;
    }

    /**
     * Tells whether the caret sits in a call nested in the argument, whose own arguments the
     * partitioning form says nothing about.
     */
    private static boolean isNested(@NotNull String expression, int from, int caret) {
        int depth = 0;
        for (int i = from; i < caret; i++) {
            char character = expression.charAt(i);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
        }
        return depth > 0;
    }

    /**
     * The arguments of the call, as ranges of the expression split on the commas that separate
     * them, the ones nested in a call of their own being left alone.
     */
    @NotNull
    private static List<int[]> arguments(@NotNull String expression, int open, int close) {
        List<int[]> ranges = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = start; i < close; i++) {
            char character = expression.charAt(i);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                ranges.add(new int[]{start, i});
                start = i + 1;
            }
        }
        ranges.add(new int[]{start, close});
        return ranges;
    }

    /**
     * The index of the parenthesis closing the one opened at the given index, the end of the
     * expression when it is left unclosed.
     */
    private static int closingParenthesis(@NotNull String expression, int open) {
        int depth = 0;
        for (int i = open; i < expression.length(); i++) {
            char character = expression.charAt(i);
            if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return i;
            }
        }
        return expression.length();
    }

    private static int skipSpaces(@NotNull String expression, int from, int to) {
        int index = from;
        while (index < to && Character.isWhitespace(expression.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int trimSpaces(@NotNull String expression, int from, int to) {
        int index = to;
        while (index > from && Character.isWhitespace(expression.charAt(index - 1))) {
            index--;
        }
        return index;
    }
}
