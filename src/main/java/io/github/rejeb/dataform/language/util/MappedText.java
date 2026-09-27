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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A text assembled from pieces of other texts, which remembers where each of its characters came
 * from. The query the plugin sends to BigQuery is built this way, so that a position BigQuery
 * reports in it can be traced back to the compiled query it was made from.
 */
public final class MappedText {

    private final String text;
    private final List<Segment> segments;
    private final Map<String, String> sources;

    private MappedText(@NotNull String text,
                       @NotNull List<Segment> segments,
                       @NotNull Map<String, String> sources) {
        this.text = text;
        this.segments = List.copyOf(segments);
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
    }

    /**
     * How a piece of the text relates to the source it was taken from.
     */
    public enum Origin {
        COPIED,
        REPLACED,
        SYNTHETIC
    }

    private record Segment(int start,
                           int end,
                           @Nullable String source,
                           int sourceStart,
                           int sourceEnd,
                           @NotNull Origin origin) {

        Segment shiftedBy(int delta) {
            return new Segment(start + delta, end + delta, source, sourceStart, sourceEnd, origin);
        }
    }

    /**
     * Where an offset of the text came from.
     *
     * @param source the name of the source text
     * @param offset the offset in that source
     * @param exact  whether the character was copied; when it was written in place of a piece of
     *               the source, the offset is the start of that piece
     */
    public record SourcePosition(@NotNull String source, int offset, boolean exact) {
    }

    /**
     * A text taken whole from a single source.
     */
    public static @NotNull MappedText identity(@NotNull String source, @NotNull String text) {
        return builder().source(source, text).copy(source, 0, text.length()).build();
    }

    /**
     * Starts an empty text.
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * The text itself.
     */
    public @NotNull String text() {
        return text;
    }

    /**
     * The text of a source this text was built from, or {@code null} when it was not built from it.
     */
    public @Nullable String sourceText(@NotNull String source) {
        return sources.get(source);
    }

    /**
     * Where the character at an offset came from, or {@code null} when the builder wrote it. The
     * end of the text is traced as the end of the last piece.
     */
    public @Nullable SourcePosition toSource(int offset) {
        for (Segment segment : segments) {
            boolean inside = offset >= segment.start() && offset < segment.end();
            boolean atEnd = offset == text.length() && segment.end() == offset;
            if (!inside && !atEnd) continue;
            return switch (segment.origin()) {
                case COPIED -> new SourcePosition(segment.source(),
                        segment.sourceStart() + offset - segment.start(), true);
                case REPLACED -> new SourcePosition(segment.source(), segment.sourceStart(), false);
                case SYNTHETIC -> null;
            };
        }
        return null;
    }

    /**
     * The piece of this text between two offsets, still traced to its sources.
     */
    public @NotNull MappedText subText(int start, int end) {
        Builder builder = builder();
        sources.forEach(builder::source);
        for (Segment segment : segments) {
            int from = Math.max(start, segment.start());
            int to = Math.min(end, segment.end());
            if (from >= to) continue;
            switch (segment.origin()) {
                case COPIED -> builder.copy(segment.source(),
                        segment.sourceStart() + from - segment.start(),
                        segment.sourceStart() + to - segment.start());
                case REPLACED -> builder.replaceWith(text.substring(from, to), segment.source(),
                        segment.sourceStart(), segment.sourceEnd());
                case SYNTHETIC -> builder.synthetic(text.substring(from, to));
            }
        }
        return builder.build();
    }

    /**
     * Assembles a {@link MappedText} piece by piece.
     */
    public static final class Builder {

        private final StringBuilder text = new StringBuilder();
        private final List<Segment> segments = new ArrayList<>();
        private final Map<String, String> sources = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Declares a text pieces can be copied from.
         */
        public @NotNull Builder source(@NotNull String name, @NotNull String sourceText) {
            String known = sources.putIfAbsent(name, sourceText);
            if (known != null && !known.equals(sourceText)) {
                throw new IllegalArgumentException("Source " + name + " is already declared with another text");
            }
            return this;
        }

        /**
         * Appends a piece of a declared source, character for character.
         */
        public @NotNull Builder copy(@NotNull String source, int start, int end) {
            String sourceText = sources.get(source);
            if (sourceText == null) throw new IllegalArgumentException("Unknown source " + source);
            return add(sourceText.substring(start, end), source, start, end, Origin.COPIED);
        }

        /**
         * Appends a text written in place of a piece of a declared source.
         */
        public @NotNull Builder replaceWith(@NotNull String replacement,
                                            @NotNull String source,
                                            int start,
                                            int end) {
            if (!sources.containsKey(source)) throw new IllegalArgumentException("Unknown source " + source);
            return add(replacement, source, start, end, Origin.REPLACED);
        }

        /**
         * Appends a text standing for nothing of any source.
         */
        public @NotNull Builder synthetic(@NotNull String piece) {
            return add(piece, null, 0, 0, Origin.SYNTHETIC);
        }

        /**
         * Appends another mapped text, keeping its trace.
         */
        public @NotNull Builder append(@NotNull MappedText other) {
            other.sources.forEach(this::source);
            int delta = text.length();
            text.append(other.text);
            for (Segment segment : other.segments) {
                segments.add(segment.shiftedBy(delta));
            }
            return this;
        }

        /**
         * The text assembled so far.
         */
        public @NotNull MappedText build() {
            return new MappedText(text.toString(), segments, sources);
        }

        private @NotNull Builder add(@NotNull String piece,
                                     @Nullable String source,
                                     int sourceStart,
                                     int sourceEnd,
                                     @NotNull Origin origin) {
            if (piece.isEmpty()) return this;
            int start = text.length();
            text.append(piece);
            segments.add(new Segment(start, text.length(), source, sourceStart, sourceEnd, origin));
            return this;
        }
    }
}
