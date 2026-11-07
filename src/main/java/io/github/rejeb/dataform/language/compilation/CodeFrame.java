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
package io.github.rejeb.dataform.language.compilation;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines around a position of a text, as a compiler prints them under an error: the previous
 * line, the line itself marked with {@code > } and a caret line under the range, then the next line.
 */
public final class CodeFrame {

    private static final String CURRENT = "> ";
    private static final String OTHER = "  ";
    private static final String SEPARATOR = " | ";

    private CodeFrame() {
    }

    /**
     * Frames the given range of a text.
     *
     * @param text   the whole text
     * @param line   the zero-based line of the range
     * @param column the zero-based column the range starts at
     * @param length the length of the range; an empty range shows one caret
     * @return the framed lines, or an empty string when the line is not in the text
     */
    public static @NotNull String of(@NotNull String text, int line, int column, int length) {
        String[] lines = text.split("\r?\n", -1);
        if (line < 0 || line >= lines.length) return "";
        int width = String.valueOf(Math.min(lines.length, line + 2)).length();
        List<String> out = new ArrayList<>();
        for (int i = Math.max(0, line - 1); i <= Math.min(lines.length - 1, line + 1); i++) {
            String number = String.format("%" + width + "d", i + 1);
            out.add((i == line ? CURRENT : OTHER) + number + SEPARATOR + lines[i]);
            if (i == line) {
                out.add(OTHER + " ".repeat(width) + SEPARATOR + padding(lines[i], column) + "^".repeat(Math.max(1, length)));
            }
        }
        return String.join("\n", out);
    }

    private static @NotNull String padding(@NotNull String line, int column) {
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < Math.max(0, column); i++) {
            padding.append(i < line.length() && line.charAt(i) == '\t' ? '\t' : ' ');
        }
        return padding.toString();
    }
}
