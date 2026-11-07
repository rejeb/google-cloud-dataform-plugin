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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The time range the statements of a script job are drawn on: where it starts, how long it lasts and
 * the ticks of its scale.
 *
 * @param origin the instant drawn at the left of the track
 * @param span   the time the track covers, never under a millisecond
 * @param ticks  the scale marks, from {@code 0 s}
 */
record StatementTimeline(@NotNull Instant origin, @NotNull Duration span, @NotNull List<Tick> ticks) {

    private static final long[] STEPS = {1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 14400};
    private static final int MAX_STEPS_IN_SPAN = 4;
    private static final Duration SHORTEST_SPAN = Duration.ofMillis(1);
    private static final double NANOS_PER_SECOND = 1e9;

    /**
     * The range of a script job: its own start and end, or those of its statements when the script's
     * are unknown.
     */
    @NotNull
    static StatementTimeline of(@Nullable Instant scriptStart, @Nullable Instant scriptEnd,
                                @NotNull List<BigQueryChildJob> jobs) {
        Instant origin = scriptStart != null ? scriptStart : jobs.stream()
                .map(BigQueryChildJob::startTime).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
        Instant end = scriptEnd != null ? scriptEnd : jobs.stream()
                .map(job -> job.endTime() != null ? job.endTime() : job.startTime())
                .filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        if (origin == null) origin = end != null ? end : Instant.EPOCH;
        if (end == null) end = origin;
        Duration span = Duration.between(origin, end);
        if (span.compareTo(SHORTEST_SPAN) < 0) span = SHORTEST_SPAN;
        return new StatementTimeline(origin, span, ticks(span));
    }

    /**
     * Where a statement's bar starts and ends, as fractions of the track; {@code null} for a statement
     * that never started. A running statement runs to the end of the track.
     */
    @Nullable
    Bar bar(@NotNull BigQueryChildJob job) {
        if (job.startTime() == null) return null;
        double from = fraction(job.startTime());
        double to = job.endTime() != null ? fraction(job.endTime()) : 1.0;
        return new Bar(from, Math.max(from, to));
    }

    private double fraction(@NotNull Instant instant) {
        double at = Duration.between(origin, instant).toNanos() / (double) span.toNanos();
        return Math.min(1.0, Math.max(0.0, at));
    }

    @NotNull
    private static List<Tick> ticks(@NotNull Duration span) {
        double seconds = span.toNanos() / NANOS_PER_SECOND;
        long step = STEPS[STEPS.length - 1];
        for (long candidate : STEPS) {
            if (Math.floor(seconds / candidate) <= MAX_STEPS_IN_SPAN) {
                step = candidate;
                break;
            }
        }
        List<Tick> ticks = new ArrayList<>();
        for (long at = 0; at <= seconds; at += step) {
            ticks.add(new Tick(at / seconds, RunText.tick(at)));
        }
        return List.copyOf(ticks);
    }

    /**
     * A mark of the time scale.
     *
     * @param at    where it stands, as a fraction of the track
     * @param label the time it marks, such as {@code 5 s} or {@code 2 m}
     */
    record Tick(double at, @NotNull String label) {
    }

    /**
     * The part of the track a statement ran in, as fractions of the track.
     *
     * @param from where the statement started
     * @param to   where it ended
     */
    record Bar(double from, double to) {
    }
}
