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
package io.github.rejeb.dataform.language.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays text out on lines of a bounded width. Used by the UI surfaces that render compiler messages
 * in components without soft wrapping, where a long message would otherwise be cut off.
 */
public final class TextWrapper {

    private TextWrapper() {
    }

    /**
     * Splits the text into lines of at most {@code maxLineLength} characters, breaking on word
     * boundaries and hard-splitting words that do not fit on a line of their own. The first line
     * starts with the given prefix, which counts towards its length.
     */
    @NotNull
    public static List<String> wrap(@NotNull String text, int maxLineLength, @NotNull String prefix) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder(prefix);
        int lineStartLength = prefix.length();

        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (current.length() > lineStartLength
                    && current.length() + 1 + word.length() > maxLineLength) {
                lines.add(current.toString());
                current = new StringBuilder();
                lineStartLength = 0;
            }
            if (!current.isEmpty() && current.charAt(current.length() - 1) != ' ') {
                current.append(' ');
            }
            appendWord(lines, current, word, maxLineLength);
        }
        if (!current.isEmpty() && !current.toString().isBlank()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * Wraps every line of the text to {@code maxLineLength} characters, keeping the existing line
     * breaks and the leading indentation of each line.
     */
    @NotNull
    public static String wrapKeepingLineBreaks(@NotNull String text, int maxLineLength) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String trimmed = line.stripTrailing();
            if (trimmed.length() <= maxLineLength) {
                lines.add(trimmed);
                continue;
            }
            String indent = trimmed.substring(0, trimmed.length() - trimmed.stripLeading().length());
            List<String> wrapped = wrap(trimmed.stripLeading(), maxLineLength, indent);
            lines.addAll(wrapped.isEmpty() ? List.of(trimmed) : wrapped);
        }
        return String.join("\n", lines);
    }

    private static void appendWord(@NotNull List<String> lines,
                                   @NotNull StringBuilder current,
                                   @NotNull String word,
                                   int maxLineLength) {
        String remaining = word;
        while (current.length() + remaining.length() > maxLineLength) {
            int room = maxLineLength - current.length();
            if (room <= 0) {
                lines.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(remaining, 0, room);
            lines.add(current.toString());
            current.setLength(0);
            remaining = remaining.substring(room);
        }
        current.append(remaining);
    }
}
