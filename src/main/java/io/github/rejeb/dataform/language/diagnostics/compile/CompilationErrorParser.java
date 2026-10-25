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
package io.github.rejeb.dataform.language.diagnostics.compile;

import io.github.rejeb.dataform.language.diagnostics.MessageRule;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.rejeb.dataform.language.diagnostics.compile.CompilationErrorKind.*;

/**
 * Takes apart the errors the Dataform compiler reports.
 *
 * <p>The position of an error in a SQLX file is one of the JavaScript the compiler generates from
 * it, so what places the error is what the message names, the frames of the stack and, for a
 * syntax error, the line the stack quotes. Frames are read relative to the project the compiler
 * ran on, which may be a temporary copy of it.</p>
 */
public final class CompilationErrorParser {

    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at (?:(?<function>.+?) \\()?(?<path>(?:[A-Za-z]:)?[^():]+?):(?<line>\\d+):(?<column>\\d+)\\)?\\s*$");
    private static final Pattern SNIPPET_HEADER = Pattern.compile("^(?<path>(?:[A-Za-z]:)?[^():]+?):(?<line>\\d+)$");
    private static final Pattern CARETS = Pattern.compile("^(?<pad>\\s*)(?<carets>\\^+)\\s*$");
    private static final Pattern ERROR_LINE = Pattern.compile("^\\w*Error: (?<message>.*)$");
    private static final Pattern DOCUMENTATION_LINK = Pattern.compile("\\.?\\s*See https?://\\S+.*$");
    private static final Pattern DEPENDENCY_NAME = Pattern.compile("\"name\"\\s*:\\s*\"(?<name>[^\"]+)\"");
    private static final String FALLBACK = "Compilation error";
    private static final String[] PROJECT_ANCHORS = {"/definitions/", "/includes/"};

    private static final List<MessageRule<CompilationErrorKind>> RULES = List.of(
            MessageRule.of(UNDEFINED_NAME, "^(?<name>[\\w$]+) is not defined$"),
            MessageRule.of(UNDEFINED_PROPERTY, "^Cannot read propert(?:y|ies) of (?:undefined|null) \\(reading '(?<name>[^']+)'\\)$"),
            MessageRule.of(NOT_A_FUNCTION, "^(?<name>[\\w$.]+) is not a function$"),
            MessageRule.of(UNRESOLVED_REF, "^Could not resolve \"(?<name>[^\"]+)\"$"),
            MessageRule.of(UNRESOLVED_REF, "^Missing dependency detected: .* depends on \"(?<name>.+)\" which does not exist\\.?$"),
            MessageRule.of(UNKNOWN_ACTION_TYPE, "^Unrecognized action type: (?<name>\\S+)$"),
            MessageRule.of(UNEXPECTED_CONFIG_PROPERTY, "^Unexpected property \"(?<name>[^\"]+)\".*$"));

    private CompilationErrorParser() {
    }

    /**
     * Takes a compilation error apart.
     *
     * @param message          the message the compiler reported, or {@code null}
     * @param stack            the stack it reported, or {@code null}
     * @param reportedFileName the project-relative file the error was reported for, or {@code null}
     */
    public static @NotNull ParsedCompilationError parse(@Nullable String message,
                                                        @Nullable String stack,
                                                        @Nullable String reportedFileName) {
        List<String> lines = stack == null ? List.of() : stack.lines().toList();
        SourceSnippet rawSnippet = snippetOf(lines);
        List<StackFrame> rawFrames = framesOf(lines);
        String root = projectRoot(rawSnippet, rawFrames, reportedFileName);
        List<StackFrame> frames = rawFrames.stream()
                .map(frame -> new StackFrame(frame.function(), relative(frame.path(), root), frame.line(), frame.column()))
                .toList();
        SourceSnippet snippet = rawSnippet == null ? null : new SourceSnippet(relative(rawSnippet.path(), root),
                rawSnippet.line(), rawSnippet.text(), rawSnippet.caretStart(), rawSnippet.caretLength());
        String text = summaryOf(message, lines);
        if (snippet != null) return new ParsedCompilationError(SYNTAX_ERROR, text, null, frames, snippet);
        MessageRule.Match<CompilationErrorKind> match = MessageRule.firstMatch(RULES, text);
        return match == null
                ? new ParsedCompilationError(OTHER, text, null, frames, null)
                : new ParsedCompilationError(match.kind(), text, nameOf(match.matcher().group("name")), frames, null);
    }

    private static @NotNull String summaryOf(@Nullable String message, @NotNull List<String> stack) {
        String text = message;
        if (text == null || text.isBlank()) {
            text = stack.stream()
                    .map(ERROR_LINE::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group("message"))
                    .findFirst()
                    .orElse(FALLBACK);
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        String summary = DOCUMENTATION_LINK.matcher(collapsed).replaceFirst("").trim();
        return summary.isEmpty() ? FALLBACK : summary;
    }

    private static @NotNull String nameOf(@NotNull String raw) {
        Matcher dependency = DEPENDENCY_NAME.matcher(raw);
        return dependency.find() ? dependency.group("name") : raw;
    }

    private static @Nullable SourceSnippet snippetOf(@NotNull List<String> lines) {
        if (lines.size() < 3) return null;
        Matcher header = SNIPPET_HEADER.matcher(lines.get(0));
        Matcher carets = CARETS.matcher(lines.get(2));
        if (!header.matches() || !carets.matches()) return null;
        String text = lines.get(1).endsWith("\r") ? lines.get(1).substring(0, lines.get(1).length() - 1) : lines.get(1);
        return new SourceSnippet(header.group("path"), Integer.parseInt(header.group("line")), text,
                carets.group("pad").length(), carets.group("carets").length());
    }

    private static @NotNull List<StackFrame> framesOf(@NotNull List<String> lines) {
        List<StackFrame> frames = new ArrayList<>();
        for (String line : lines) {
            Matcher frame = FRAME.matcher(line);
            if (frame.matches()) {
                frames.add(new StackFrame(frame.group("function"), frame.group("path"),
                        Integer.parseInt(frame.group("line")), Integer.parseInt(frame.group("column"))));
            }
        }
        return frames;
    }

    private static @Nullable String projectRoot(@Nullable SourceSnippet snippet,
                                                @NotNull List<StackFrame> frames,
                                                @Nullable String reportedFileName) {
        if (reportedFileName == null || reportedFileName.isBlank()) return null;
        String reported = DataformPaths.normalize(reportedFileName);
        List<String> paths = new ArrayList<>();
        if (snippet != null) paths.add(snippet.path());
        frames.forEach(frame -> paths.add(frame.path()));
        for (String path : paths) {
            String normalized = DataformPaths.normalize(path);
            if (normalized.endsWith("/" + reported)) {
                return normalized.substring(0, normalized.length() - reported.length());
            }
        }
        return null;
    }

    private static @NotNull String relative(@NotNull String path, @Nullable String root) {
        String normalized = DataformPaths.normalize(path);
        if (root != null && normalized.startsWith(root)) return normalized.substring(root.length());
        for (String anchor : PROJECT_ANCHORS) {
            int at = normalized.lastIndexOf(anchor);
            if (at >= 0) return normalized.substring(at + 1);
        }
        return normalized;
    }
}
