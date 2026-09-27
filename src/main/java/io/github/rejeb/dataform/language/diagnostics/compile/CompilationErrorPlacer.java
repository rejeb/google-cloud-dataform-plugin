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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import io.github.rejeb.dataform.language.diagnostics.sql.mapping.SqlTokenLocator;
import io.github.rejeb.dataform.language.psi.SqlxFile;
import io.github.rejeb.dataform.language.util.DataformPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Places a Dataform compilation error in the file it is about, as the file reads now.
 *
 * <p>A JavaScript file is run as written, so the frame of the file gives the line and column,
 * checked against the name the message gives. A SQLX file is turned into other JavaScript before it
 * runs, so the frame only tells which section ran; the error is found by what it names, where the
 * compiler evaluates that section, in the order it does. Nothing is placed when what the message
 * names is no longer written, since the file has changed since the compilation.</p>
 *
 * <p>A syntax error quotes the line it stopped at. In a JavaScript file the line the stack gives
 * is the line of the file, taken while it still reads as quoted; in a SQLX file it counts the lines
 * of the generated JavaScript, so the quoted line is looked for and taken only when the file has it
 * once.</p>
 */
final class CompilationErrorPlacer {

    private CompilationErrorPlacer() {
    }

    static @Nullable TextRange place(@NotNull PsiFile file, @NotNull String filePath,
                                     @NotNull ParsedCompilationError error) {
        String text = file.getText();
        SourceSnippet snippet = error.snippet();
        if (snippet != null) {
            return DataformPaths.pointsTo(filePath, snippet.path())
                    ? placeSnippet(text, snippet, !(file instanceof SqlxFile))
                    : null;
        }
        if (!(file instanceof SqlxFile)) return placeInScript(text, error.frameIn(filePath), error);
        String name = error.name();
        if (name == null) return null;
        SqlxRegions regions = SqlxRegions.of(file);
        return switch (error.kind()) {
            case UNRESOLVED_REF -> first(quoted(text, regions.allJavaScript(), lastSegment(name)));
            case UNKNOWN_ACTION_TYPE -> regions.config() == null ? null : first(quoted(text, List.of(regions.config()), name));
            case UNEXPECTED_CONFIG_PROPERTY -> regions.config() == null ? null : first(keys(text, regions.config(), name));
            case UNDEFINED_NAME, UNDEFINED_PROPERTY, NOT_A_FUNCTION -> placeRuntimeError(text, filePath, regions, error, name);
            default -> null;
        };
    }

    static @Nullable TextRange placeRaised(@NotNull PsiFile file, @NotNull String filePath,
                                           @NotNull ParsedCompilationError error) {
        StackFrame frame = error.frameIn(filePath);
        return frame == null ? null : placeInScript(file.getText(), frame, error);
    }

    private static @Nullable TextRange placeRuntimeError(@NotNull String text, @NotNull String filePath,
                                                         @NotNull SqlxRegions regions,
                                                         @NotNull ParsedCompilationError error, @NotNull String name) {
        int own = error.indexOfFrameIn(filePath);
        List<TextRange> ranges = regions.evaluationOrder(SqlxRegions.sectionOf(error, filePath));
        if (own > 0) {
            String callee = error.frames().get(own - 1).functionName();
            return callee == null ? null : first(words(text, ranges, callee));
        }
        if (error.kind() == CompilationErrorKind.UNDEFINED_PROPERTY) {
            return first(words(text, ranges, name).stream()
                    .filter(range -> range.getStartOffset() > 0 && text.charAt(range.getStartOffset() - 1) == '.')
                    .toList());
        }
        return first(words(text, ranges, name));
    }

    private static @Nullable TextRange placeInScript(@NotNull String text, @Nullable StackFrame frame,
                                                     @NotNull ParsedCompilationError error) {
        String expected = expectedName(error);
        if (frame == null) {
            if (expected == null) return null;
            List<TextRange> found = SqlTokenLocator.occurrences(text, expected, 0, text.length());
            return found.size() == 1 ? found.getFirst() : null;
        }
        int offset = offsetOf(text, frame.line(), frame.column());
        return offset < 0 ? null : SqlTokenLocator.locate(text, offset, expected);
    }

    private static @Nullable TextRange placeSnippet(@NotNull String text, @NotNull SourceSnippet snippet,
                                                    boolean lineIsExact) {
        List<TextRange> quoting = new ArrayList<>();
        int lineStart = 0;
        for (int line = 1; lineStart <= text.length(); line++) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = text.length();
            int contentEnd = lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r' ? lineEnd - 1 : lineEnd;
            if (text.substring(lineStart, contentEnd).equals(snippet.text())) {
                TextRange content = new TextRange(lineStart, contentEnd);
                if (lineIsExact && line == snippet.line()) return caretIn(content, snippet);
                quoting.add(content);
            }
            if (lineEnd >= text.length()) break;
            lineStart = lineEnd + 1;
        }
        return quoting.size() == 1 ? caretIn(quoting.getFirst(), snippet) : null;
    }

    private static @NotNull TextRange caretIn(@NotNull TextRange line, @NotNull SourceSnippet snippet) {
        int start = line.getStartOffset() + Math.min(snippet.caretStart(), line.getLength());
        int end = Math.min(start + Math.max(snippet.caretLength(), 1), line.getEndOffset());
        return new TextRange(start, Math.max(start, end));
    }

    private static @Nullable String expectedName(@NotNull ParsedCompilationError error) {
        String name = error.name();
        if (name == null) return null;
        return switch (error.kind()) {
            case UNDEFINED_NAME, UNDEFINED_PROPERTY, NOT_A_FUNCTION -> name;
            case UNRESOLVED_REF -> lastSegment(name);
            default -> null;
        };
    }

    private static @NotNull List<TextRange> words(@NotNull String text, @NotNull List<TextRange> ranges,
                                                  @NotNull String name) {
        List<TextRange> found = new ArrayList<>();
        for (TextRange range : ranges) {
            found.addAll(SqlTokenLocator.occurrences(text, name, range.getStartOffset(), range.getEndOffset()));
        }
        return found;
    }

    private static @NotNull List<TextRange> quoted(@NotNull String text, @NotNull List<TextRange> ranges,
                                                   @NotNull String name) {
        Pattern pattern = Pattern.compile("([\"'`])(" + Pattern.quote(name) + ")\\1");
        List<TextRange> found = new ArrayList<>();
        for (TextRange range : ranges) {
            Matcher matcher = pattern.matcher(text).region(range.getStartOffset(), range.getEndOffset());
            while (matcher.find()) found.add(new TextRange(matcher.start(2), matcher.end(2)));
        }
        return found;
    }

    private static @NotNull List<TextRange> keys(@NotNull String text, @NotNull TextRange config, @NotNull String name) {
        Pattern pattern = Pattern.compile("(?<![\\w$])([\"']?)(" + Pattern.quote(name) + ")\\1\\s*:");
        Matcher matcher = pattern.matcher(text).region(config.getStartOffset(), config.getEndOffset());
        List<TextRange> found = new ArrayList<>();
        while (matcher.find()) found.add(new TextRange(matcher.start(2), matcher.end(2)));
        return found;
    }

    private static int offsetOf(@NotNull String text, int line, int column) {
        if (line < 1 || column < 1) return -1;
        int at = 0;
        for (int current = 1; current < line; current++) {
            int end = text.indexOf('\n', at);
            if (end < 0) return -1;
            at = end + 1;
        }
        int lineEnd = text.indexOf('\n', at);
        if (lineEnd < 0) lineEnd = text.length();
        return Math.min(at + column - 1, lineEnd);
    }

    private static @NotNull String lastSegment(@NotNull String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static @Nullable TextRange first(@NotNull List<TextRange> ranges) {
        return ranges.isEmpty() ? null : ranges.getFirst();
    }
}
