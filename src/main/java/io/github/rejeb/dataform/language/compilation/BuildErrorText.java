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

import io.github.rejeb.dataform.language.compilation.model.CompilationError;
import io.github.rejeb.dataform.language.util.DataformPaths;
import io.github.rejeb.dataform.language.util.TextWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a compilation error into the two texts the Build tool window needs: the one-line title of
 * the tree node, and the full description shown in the details panel next to it. Dataform reports
 * failures as whole stderr dumps, which the tree node cannot render, so the message is only readable
 * once the details panel receives it wrapped over several lines.
 */
public final class BuildErrorText {

    public static final int MAX_LINE_LENGTH = 120;
    public static final int MAX_TITLE_LENGTH = 120;

    private static final String FALLBACK = "(no message)";
    private static final String ELLIPSIS = "…";
    private static final String FACT_INDENT = " ".repeat("Dataform ".length());

    private BuildErrorText() {
    }

    /**
     * One-line summary of the error, used as the label of the Build tree node. The node of its file
     * already groups the errors, so the action is not repeated.
     */
    @NotNull
    public static String title(@NotNull CompilationError error) {
        String firstLine = firstMeaningfulLine(reportedText(error));
        return firstLine == null ? FALLBACK : truncate(firstLine);
    }

    /**
     * Description of an error for the details panel: the message and its hint, where it is placed
     * with the lines around it, then what Dataform reported. An error that is not placed gets the
     * same text without the location and the code frame.
     */
    @NotNull
    public static String placedDetails(@NotNull CompilationError error, @Nullable PlacedError placed) {
        String message = firstMeaningfulLine(reportedText(error));
        List<String> lines = new ArrayList<>();
        lines.add(details(message != null ? message : FALLBACK));
        if (placed != null) {
            String hint = trimToNull(placed.hint());
            if (hint != null) {
                lines.add(details(hint));
            }
            lines.add("");
            if (placed.thrownInPath() != null) {
                lines.add("Not placed in " + fileName(placed.path()) + ": the error is thrown in an include.");
                lines.add("Thrown in " + location(placed.thrownInPath(), placed));
            } else {
                lines.add(location(placed.path(), placed));
            }
            String frame = CodeFrame.of(placed.fileText(), placed.line(), placed.column(), placed.length());
            if (!frame.isEmpty()) {
                lines.add(frame);
            }
        }
        List<String> facts = facts(error, message);
        if (!facts.isEmpty()) {
            lines.add("");
            lines.addAll(facts);
        }
        return String.join("\n", lines);
    }

    /**
     * Full text of the error, wrapped so that the details panel shows it without cutting it off.
     */
    @NotNull
    public static String details(@NotNull CompilationError error) {
        StringBuilder text = new StringBuilder();
        if (error.getActionName() != null && !error.getActionName().isBlank()) {
            text.append("Action: ").append(error.getActionName()).append('\n');
        }
        if (error.getFileName() != null && !error.getFileName().isBlank()) {
            text.append("File: ").append(error.getFileName()).append('\n');
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }

        String message = trimToNull(error.getMessage());
        String stack = trimToNull(error.getStack());
        if (message != null) {
            if (stack != null && stack.contains(message)) {
                text.append(stack);
            } else if (stack != null) {
                text.append(message).append("\n\n").append(stack);
            } else {
                text.append(message);
            }
        } else if (stack != null) {
            text.append(stack);
        } else {
            return FALLBACK;
        }

        return details(text.toString());
    }

    /**
     * Wraps a free-form message, for the failures that carry no compilation error of their own.
     */
    @NotNull
    public static String details(@NotNull String message) {
        return TextWrapper.wrapKeepingLineBreaks(message, MAX_LINE_LENGTH);
    }

    @NotNull
    private static List<String> facts(@NotNull CompilationError error, @Nullable String message) {
        List<String> facts = new ArrayList<>();
        String action = trimToNull(error.getActionName());
        if (action != null) {
            facts.add(fact("Action", action));
        }
        String raw = trimToNull(error.getMessage());
        if (raw != null) {
            facts.addAll(factLines("Dataform", raw));
        }
        String stack = trimToNull(error.getStack());
        if (stack != null) {
            for (String line : stack.split("\n")) {
                String frame = line.strip();
                if (frame.isEmpty() || (message != null && frame.contains(message))) {
                    continue;
                }
                facts.add(FACT_INDENT + frame);
            }
        }
        return facts;
    }

    @NotNull
    private static List<String> factLines(@NotNull String key, @NotNull String value) {
        List<String> lines = new ArrayList<>();
        for (String line : details(value).split("\n")) {
            lines.add(lines.isEmpty() ? fact(key, line) : FACT_INDENT + line);
        }
        return lines;
    }

    @NotNull
    private static String fact(@NotNull String key, @NotNull String value) {
        return key + " ".repeat(Math.max(1, FACT_INDENT.length() - key.length())) + value;
    }

    @NotNull
    private static String location(@NotNull String path, @NotNull PlacedError placed) {
        return path + ":" + (placed.line() + 1) + ":" + (placed.column() + 1);
    }

    @NotNull
    private static String fileName(@NotNull String path) {
        String normalized = DataformPaths.normalize(path);
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    @Nullable
    private static String reportedText(@NotNull CompilationError error) {
        String message = trimToNull(error.getMessage());
        return message != null ? message : trimToNull(error.getStack());
    }

    @Nullable
    private static String firstMeaningfulLine(@Nullable String text) {
        if (text == null) {
            return null;
        }
        for (String line : text.split("\n")) {
            String collapsed = line.replaceAll("\\s+", " ").trim();
            if (!collapsed.isEmpty()) {
                return collapsed;
            }
        }
        return null;
    }

    @NotNull
    private static String truncate(@NotNull String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, MAX_TITLE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    @Nullable
    private static String trimToNull(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
