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

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

public final class RunConfigUiUtils {

    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final Color WARNING_FOREGROUND = JBColor.namedColor("Label.warningForeground", JBColor.ORANGE);

    private RunConfigUiUtils() {
    }

    /**
     * The last segment of a GCP resource name.
     *
     * @param fullName a resource name such as {@code projects/p/locations/l/repositories/r}
     * @return the text after the last {@code /}, or the whole name when it has none
     */
    @NotNull
    public static String shortName(@NotNull String fullName) {
        return fullName.substring(fullName.lastIndexOf('/') + 1);
    }

    /**
     * The BigQuery console page of a job.
     *
     * @param project  the project the job ran in
     * @param location the location of the job, {@code US} when unknown
     * @param jobId    the id of the job
     * @return the console URL
     */
    @NotNull
    public static String bigQueryJobUrl(@NotNull String project, @Nullable String location, @NotNull String jobId) {
        return "https://console.cloud.google.com/bigquery?project=" + project
                + "&j=bq:" + (location != null ? location : "US") + ":" + jobId + "&page=queryresults";
    }

    /**
     * Lays out a cost value with its gray price note on one row.
     */
    @NotNull
    public static JPanel costRow(@NotNull JBTextField value, @NotNull JBLabel note) {
        JPanel row = new JPanel(new BorderLayout(JBUIScale.scale(12), 0));
        row.setOpaque(false);
        row.add(value, BorderLayout.WEST);
        row.add(note, BorderLayout.CENTER);
        return row;
    }

    /**
     * Shows a cost in a row built by {@link #costRow}, with its price explained on hover; an
     * unreliable price turns the note to the warning color.
     */
    public static void showCost(@NotNull JBTextField value, @NotNull JBLabel note, @NotNull CostText cost) {
        String tooltip = cost.tooltip().isEmpty() ? null : cost.tooltip();
        value.setText(cost.value());
        value.setToolTipText(tooltip);
        note.setText(cost.note());
        note.setToolTipText(tooltip);
        note.setForeground(cost.reliable() ? UIUtil.getContextHelpForeground() : WARNING_FOREGROUND);
        value.revalidate();
    }

    @NotNull
    public static String formatDuration(@NotNull Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }

    @NotNull
    public static String formatDuration(@Nullable Instant start, @Nullable Instant end) {
        if (start == null || end == null) return "—";
        return formatDuration(Duration.between(start, end));
    }
}
