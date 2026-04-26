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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTextField;
import java.time.Duration;
import java.time.Instant;

public final class RunConfigUiUtils {

    private RunConfigUiUtils() {
    }

    @NotNull
    public static JTextField selectableValue(@Nullable String text) {
        JTextField field = new JTextField(text != null ? text : "—");
        field.setEditable(false);
        field.setBorder(null);
        field.setBackground(UIUtil.getPanelBackground());
        field.setForeground(UIUtil.getLabelForeground());
        field.setFont(UIUtil.getLabelFont());
        return field;
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
