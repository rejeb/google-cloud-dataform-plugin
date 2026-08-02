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
package io.github.rejeb.dataform.language.diagnostics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a compilation error message into the lines rendered inside an editor chip.
 */
public final class CompilationErrorChipText {

    public static final int MAX_LINE_LENGTH = 80;

    private static final String PREFIX = "⚠ ";
    private static final String FALLBACK = "Compilation error";

    private CompilationErrorChipText() {
    }

    /**
     * Splits the message into chip lines of at most {@link #MAX_LINE_LENGTH} characters, breaking
     * on word boundaries where possible. The first line carries the warning prefix.
     */
    public static @NotNull List<String> wrap(@Nullable String message) {
        int maxLineLength = MAX_LINE_LENGTH;
        String prefix = PREFIX;
        String normalized = normalize(message);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder(prefix);

        for (String word : normalized.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (current.length() > prefix.length()
                    && current.length() + 1 + word.length() > maxLineLength) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0 && current.charAt(current.length() - 1) != ' ') {
                current.append(' ');
            }
            appendWord(lines, current, word, maxLineLength);
        }
        if (current.length() > 0 && !current.toString().isBlank()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of(prefix + FALLBACK) : lines;
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

    private static String normalize(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return FALLBACK;
        }
        String collapsed = message.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? FALLBACK : collapsed;
    }
}
