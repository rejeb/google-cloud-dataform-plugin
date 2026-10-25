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

import com.intellij.ide.BrowserUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.ui.ReadOnlyTextFields;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.*;

public final class RunConfigUiUtils {

    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String LINK_LISTENER = "dataform.link.listener";

    private RunConfigUiUtils() {
    }

    /**
     * Shows a link on a label that opens a URL in the browser, replacing the link it showed before.
     *
     * @param label the label showing the link
     * @param text  the text of the link
     * @param url   the URL the link opens
     */
    public static void setLink(@NotNull JLabel label, @NotNull String text, @NotNull String url) {
        label.setText("<html><a href=''>" + text + "</a></html>");
        if (label.getClientProperty(LINK_LISTENER) instanceof MouseListener previous) {
            label.removeMouseListener(previous);
        }
        MouseListener listener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                BrowserUtil.browse(url);
            }
        };
        label.putClientProperty(LINK_LISTENER, listener);
        label.addMouseListener(listener);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Adds one row per key to a {@link GridBagLayout} panel: the key as a grey label, the value
     * filling the rest of the row.
     *
     * @param panel          the panel, laid out by a {@link GridBagLayout}
     * @param keys           the label of each row
     * @param values         the component of each row
     * @param verticalFiller whether an empty row below takes the remaining height
     */
    public static void addKeyValueRows(@NotNull JPanel panel, @NotNull String[] keys, @NotNull Component[] values,
                                       boolean verticalFiller) {
        GridBagConstraints kc = new GridBagConstraints();
        kc.anchor = GridBagConstraints.NORTHWEST;
        kc.insets = JBUI.insets(2, 0, 2, 12);
        kc.fill = GridBagConstraints.NONE;
        kc.gridx = 0;

        GridBagConstraints vc = new GridBagConstraints();
        vc.anchor = GridBagConstraints.NORTHWEST;
        vc.insets = JBUI.insets(2, 0);
        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.weightx = 1.0;
        vc.gridwidth = GridBagConstraints.REMAINDER;
        vc.gridx = 1;

        for (int i = 0; i < keys.length; i++) {
            kc.gridy = vc.gridy = i;
            JLabel key = new JBLabel(keys[i] + ":");
            key.setForeground(UIUtil.getLabelDisabledForeground());
            panel.add(key, kc);
            panel.add(values[i], vc);
        }
        if (verticalFiller) {
            GridBagConstraints filler = new GridBagConstraints();
            filler.gridy = keys.length;
            filler.weighty = 1.0;
            filler.fill = GridBagConstraints.VERTICAL;
            panel.add(new JPanel(), filler);
        }
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

    @NotNull
    public static JBTextField selectableValue(@Nullable String text) {
        return ReadOnlyTextFields.singleLine(text != null ? text : "—");
    }

    @NotNull
    public static JBTextArea selectableTextArea(@Nullable String text) {
        return ReadOnlyTextFields.multiLine(text);
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
