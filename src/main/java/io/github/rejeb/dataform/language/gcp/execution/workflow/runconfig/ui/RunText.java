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

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import io.github.rejeb.dataform.language.ui.UiText;
import io.github.rejeb.dataform.language.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words and numbers of the workflow run console, written as the prototype writes them.
 */
final class RunText {

    private static final long MILLIS_PER_SECOND = 1000;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3600;
    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*([A-Za-z]+)");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String FAILED = "FAILED";
    private static final String RUNNING = "RUNNING";

    private RunText() {
    }

    /**
     * A duration such as {@code 5 s}, {@code 22.5 s}, {@code 1 m 5 s} or {@code 2 h 3 m}.
     */
    @NotNull
    static String duration(@NotNull Duration duration) {
        long millis = Math.max(0, duration.toMillis());
        if (millis < SECONDS_PER_MINUTE * MILLIS_PER_SECOND) {
            BigDecimal seconds = BigDecimal.valueOf(millis, 3).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
            return (seconds.signum() == 0 ? "0" : seconds.toPlainString()) + " s";
        }
        long seconds = Math.round(millis / (double) MILLIS_PER_SECOND);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / SECONDS_PER_MINUTE;
        if (hours > 0) return hours + " h " + minutes + " m";
        return minutes + " m " + (seconds % SECONDS_PER_MINUTE) + " s";
    }

    /**
     * The duration between two instants, or {@code null} when one of them is unknown.
     */
    @Nullable
    static String duration(@Nullable Instant start, @Nullable Instant end) {
        return start != null && end != null ? duration(Duration.between(start, end)) : null;
    }

    /**
     * The time an action took, or has taken so far while it runs; {@code null} before it starts.
     */
    @Nullable
    static String duration(@NotNull InvocationActionResult action) {
        if (action.startTime() == null) return null;
        if (action.endTime() != null) return duration(action.startTime(), action.endTime());
        if (action.state() != InvocationActionState.RUNNING) return null;
        return duration(Duration.between(action.startTime(), Instant.now()));
    }

    /**
     * The time an invocation has run: up to its end, or up to now while it runs.
     */
    @Nullable
    static Duration elapsed(@Nullable InvocationSummary summary) {
        if (summary == null) return null;
        Instant end = summary.endTime() != null ? summary.endTime() : Instant.now();
        return Duration.between(summary.startTime(), end);
    }

    /**
     * The state of an action as a word.
     */
    @NotNull
    static String state(@NotNull InvocationActionState state) {
        return switch (state) {
            case PENDING -> "Waiting";
            case RUNNING -> "Running";
            case SKIPPED, DISABLED -> "Skipped";
            case SUCCEEDED -> "Succeeded";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
            case UNKNOWN -> "Unknown";
        };
    }

    /**
     * The state of an invocation as a word.
     */
    @NotNull
    static String state(@NotNull WorkflowInvocationState state) {
        return switch (state) {
            case RUNNING -> "Running";
            case SUCCEEDED -> "Succeeded";
            case FAILED -> "Failed";
            case CANCELLED -> "Cancelled";
            case UNKNOWN -> "Unknown";
        };
    }

    /**
     * The name of the table of a target {@code database.schema.name}.
     */
    @NotNull
    static String name(@NotNull String target) {
        return target.substring(target.lastIndexOf('.') + 1);
    }

    /**
     * The schema of a target {@code database.schema.name}, {@code null} when the target has none.
     */
    @Nullable
    static String schema(@NotNull String target) {
        int last = target.lastIndexOf('.');
        if (last < 0) return null;
        return target.substring(target.lastIndexOf('.', last - 1) + 1, last);
    }

    /**
     * A target without its database: {@code schema.name}.
     */
    @NotNull
    static String schemaAndName(@NotNull String target) {
        String schema = schema(target);
        return schema != null ? schema + "." + name(target) : name(target);
    }

    /**
     * The kind of a statement, such as {@code CREATE SCHEMA} or {@code MERGE}: the BigQuery statement
     * type when known, else the first word of its SQL, else {@code Statement}.
     */
    @NotNull
    static String statementKind(@Nullable String statementType, @Nullable String query) {
        if (statementType != null && !statementType.isBlank()) return statementType.replace('_', ' ');
        if (query != null) {
            Matcher word = FIRST_WORD.matcher(query);
            if (word.find()) return word.group(1).toUpperCase(Locale.ROOT);
        }
        return "Statement";
    }

    /**
     * The table name of a statement target {@code project.dataset.table}, {@code null} without target.
     */
    @Nullable
    static String tableName(@Nullable String target) {
        return target != null ? name(target) : null;
    }

    /**
     * The label of a statement's bar: its duration, {@code failed after 6.1 s}, {@code running}, or
     * nothing when its times are unknown.
     */
    @NotNull
    static String barLabel(@NotNull BigQueryChildJob job) {
        if (RUNNING.equals(job.status())) return "running";
        String duration = duration(job.startTime(), job.endTime());
        if (duration == null) return "";
        return FAILED.equals(job.status()) ? "failed after " + duration : duration;
    }

    /**
     * The summary of a script job: how many statements ran, or where a failed script stopped, then
     * its duration and the bytes it processed.
     */
    @NotNull
    static String scriptSummary(@NotNull BigQueryJobDetails details) {
        String bytes = details.bytesProcessed() != null
                ? Utils.formatBytes(details.bytesProcessed()) + " processed" : null;
        return UiText.dot(statementCount(details.childJobs()),
                duration(details.startTime(), details.endTime()), bytes);
    }

    /**
     * The local clock times a job ran between, such as {@code 10:42:05 → 10:42:23}; only the start
     * while it runs, nothing before it starts.
     */
    @NotNull
    static String timeRange(@Nullable Instant start, @Nullable Instant end, @NotNull ZoneId zone) {
        if (start == null) return "";
        String from = CLOCK.format(start.atZone(zone));
        return end != null ? from + " → " + CLOCK.format(end.atZone(zone)) : from + " →";
    }

    /**
     * The label of a timeline tick: {@code N s} under a minute, {@code N m} under an hour, else
     * {@code N h}.
     */
    @NotNull
    static String tick(long seconds) {
        if (seconds < SECONDS_PER_MINUTE) return seconds + " s";
        if (seconds < SECONDS_PER_HOUR) return inUnits(seconds, SECONDS_PER_MINUTE) + " m";
        return inUnits(seconds, SECONDS_PER_HOUR) + " h";
    }

    @Nullable
    private static String statementCount(@NotNull List<BigQueryChildJob> children) {
        if (children.isEmpty()) return null;
        for (int i = 0; i < children.size(); i++) {
            if (FAILED.equals(children.get(i).status())) return "stopped at statement " + (i + 1);
        }
        return children.size() == 1 ? "1 statement" : children.size() + " statements";
    }

    @NotNull
    private static String inUnits(long seconds, long unit) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(unit), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
