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

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Word-wraps a banner message to a pixel width and keeps at most a fixed number of lines, ending
 * the last kept line with an ellipsis when the message does not fit.
 */
public final class BannerTextClamp {

    static final String ELLIPSIS = "...";

    private BannerTextClamp() {
    }

    /**
     * The visible lines of a clamped message, joined by line breaks, and whether text was cut.
     */
    public record Clamped(@NotNull String text, boolean truncated) {
    }

    /**
     * Wraps the text to the given width, as measured by {@code measure}, and keeps at most
     * {@code maxLines} lines. A single line joins the paragraphs of the text so that it shows as
     * much of the message as fits. A non-positive width or line count leaves the text untouched.
     */
    @NotNull
    public static Clamped clamp(@NotNull String text,
                                int maxLines,
                                int width,
                                @NotNull ToIntFunction<String> measure) {
        if (width <= 0 || maxLines <= 0) {
            return new Clamped(text, false);
        }
        String source = maxLines == 1 ? text.replace('\n', ' ') : text;
        List<String> lines = wrap(source, width, measure, maxLines + 1);
        if (lines.size() <= maxLines) {
            return new Clamped(String.join("\n", lines), false);
        }
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        kept.set(maxLines - 1, ellipsize(kept.get(maxLines - 1), width, measure));
        return new Clamped(String.join("\n", kept), true);
    }

    @NotNull
    private static List<String> wrap(@NotNull String text,
                                     int width,
                                     @NotNull ToIntFunction<String> measure,
                                     int limit) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String current = "";
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (measure.applyAsInt(candidate) <= width) {
                    current = candidate;
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current);
                }
                current = word;
                while (measure.applyAsInt(current) > width && current.length() > 1) {
                    int cut = longestFittingPrefix(current, width, measure);
                    lines.add(current.substring(0, cut));
                    current = current.substring(cut);
                }
                if (lines.size() >= limit) {
                    return lines;
                }
            }
            lines.add(current);
            if (lines.size() >= limit) {
                return lines;
            }
        }
        return lines;
    }

    private static int longestFittingPrefix(@NotNull String word, int width, @NotNull ToIntFunction<String> measure) {
        int cut = 1;
        while (cut < word.length() && measure.applyAsInt(word.substring(0, cut + 1)) <= width) {
            cut++;
        }
        return cut;
    }

    @NotNull
    private static String ellipsize(@NotNull String line, int width, @NotNull ToIntFunction<String> measure) {
        String kept = line.stripTrailing();
        while (!kept.isEmpty() && measure.applyAsInt(kept + ELLIPSIS) > width) {
            kept = kept.substring(0, kept.length() - 1).stripTrailing();
        }
        return kept + ELLIPSIS;
    }
}
