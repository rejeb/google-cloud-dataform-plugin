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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.lineage.dataplex.Freshness;
import io.github.rejeb.dataform.language.lineage.dataplex.TimeRange;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text of the freshness tooltip of a node: when its table was last written, by what, and how
 * far the recorded lineage can lag.
 */
public final class FreshnessText {

    static final String LAG = "Knowledge Catalog can lag by up to 24 hours";
    private static final String VIEW_NOTE = "A view reads its sources live: this is not the age of its data";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    private FreshnessText() {
    }

    /**
     * @param freshness what the recorded lineage tells about the last write
     * @param range     how far back the lineage was read
     * @param clock     the current time and the zone the times are shown in
     * @return the lines of the tooltip, separated by line breaks
     */
    static @NotNull String tooltip(@NotNull Freshness freshness, @NotNull TimeRange range, @NotNull Clock clock) {
        Instant lastWrite = freshness.lastWrite();
        if (lastWrite == null) {
            return "No write recorded in the " + range.label().toLowerCase(Locale.ROOT) + "\n" + LAG;
        }
        List<String> lines = new ArrayList<>();
        lines.add((freshness.view() ? "Created or replaced: " : "Last write: ") + when(lastWrite, clock)
                + " · " + ago(lastWrite, clock));
        if (freshness.writtenBy() != null && !freshness.writtenBy().isBlank()) {
            lines.add("Written by: " + freshness.writtenBy());
        }
        if (freshness.view()) lines.add(VIEW_NOTE);
        lines.add(LAG);
        return String.join("\n", lines);
    }

    /**
     * @return the last write as one value, such as "Today 06:15 · 9 hours ago", or that none was
     * recorded in the range
     */
    static @NotNull String value(@NotNull Freshness freshness, @NotNull TimeRange range, @NotNull Clock clock) {
        Instant lastWrite = freshness.lastWrite();
        if (lastWrite == null) return "Not recorded in the " + range.label().toLowerCase(Locale.ROOT);
        return whenAndAge(lastWrite, clock);
    }

    /**
     * @return a time with its age, such as "Today 06:15 · 9 hours ago"
     */
    static @NotNull String whenAndAge(@NotNull Instant instant, @NotNull Clock clock) {
        return when(instant, clock) + " · " + ago(instant, clock);
    }

    /**
     * @return a time as "Today 06:15", "Yesterday 06:15" or "9 Nov 06:15"
     */
    public static @NotNull String when(@NotNull Instant instant, @NotNull Clock clock) {
        ZonedDateTime time = instant.atZone(clock.getZone());
        LocalDate today = LocalDate.now(clock);
        if (time.toLocalDate().equals(today)) return "Today " + TIME.format(time);
        if (time.toLocalDate().equals(today.minusDays(1))) return "Yesterday " + TIME.format(time);
        return DAY_AND_TIME.format(time);
    }

    private static @NotNull String ago(@NotNull Instant instant, @NotNull Clock clock) {
        Duration elapsed = Duration.between(instant, clock.instant());
        long minutes = Math.max(1, elapsed.toMinutes());
        if (minutes < 60) return count(minutes, "minute");
        if (elapsed.toHours() < 24) return count(Math.round(minutes / 60.0), "hour");
        long days = ChronoUnit.DAYS.between(instant.atZone(clock.getZone()).toLocalDate(), LocalDate.now(clock));
        return count(Math.max(1, days), "day");
    }

    private static @NotNull String count(long value, @NotNull String unit) {
        return value + " " + unit + (value == 1 ? "" : "s") + " ago";
    }
}
