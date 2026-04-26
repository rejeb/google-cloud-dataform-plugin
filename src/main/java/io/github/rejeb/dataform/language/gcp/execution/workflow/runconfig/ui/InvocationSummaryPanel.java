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

import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class InvocationSummaryPanel extends JPanel {

    private static final String CARD_LOADING = "loading";
    private static final String CARD_CONTENT = "content";

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private final JLabel urlLabel = new JBLabel();
    private final JLabel sourceLabel = new JBLabel();
    private final JTextField startTimeLabel = RunConfigUiUtils.selectableValue("");
    private final JTextField statusLabel = RunConfigUiUtils.selectableValue("");
    private final JTextField durationLabel = RunConfigUiUtils.selectableValue("—");
    private final JTextField compilationLabel = RunConfigUiUtils.selectableValue("");
    private final JTextField sourceTypeLabel = RunConfigUiUtils.selectableValue("");
    private final JTextField contentsLabel = RunConfigUiUtils.selectableValue("");

    public InvocationSummaryPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8, 12));
        setBackground(UIUtil.getPanelBackground());

        cards.add(buildLoadingCard(), CARD_LOADING);
        cards.add(buildContentCard(), CARD_CONTENT);
        cardLayout.show(cards, CARD_LOADING);

        add(cards, BorderLayout.CENTER);
    }

    private JPanel buildLoadingCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());
        JBLabel spinner = new JBLabel(AnimatedIcon.Big.INSTANCE);
        spinner.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(spinner);
        return panel;
    }

    private JPanel buildContentCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIUtil.getPanelBackground());

        String[] keys = {"Execution URL", "Start time", "Status", "Duration",
                "Compilation ID", "Source type", "Source", "Contents"};
        Component[] valueLabels = {urlLabel, startTimeLabel, statusLabel, durationLabel,
                compilationLabel, sourceTypeLabel, sourceLabel, contentsLabel};

        GridBagConstraints kc = new GridBagConstraints();
        kc.anchor = GridBagConstraints.NORTHWEST;
        kc.insets = JBUI.insets(2, 0, 2, 12);
        kc.fill = GridBagConstraints.NONE;

        GridBagConstraints vc = new GridBagConstraints();
        vc.anchor = GridBagConstraints.NORTHWEST;
        vc.insets = JBUI.insets(2, 0, 2, 0);
        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.weightx = 1.0;
        vc.gridwidth = GridBagConstraints.REMAINDER;

        for (int i = 0; i < keys.length; i++) {
            kc.gridy = i;
            vc.gridy = i;
            kc.gridx = 0;
            vc.gridx = 1;

            JLabel key = new JBLabel(keys[i] + ":");
            key.setForeground(UIUtil.getLabelDisabledForeground());
            panel.add(key, kc);
            panel.add(valueLabels[i], vc);
        }

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = keys.length;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;
        panel.add(new JPanel(), filler);

        return panel;
    }

    /**
     * Refreshes all fields from the given progress. Must be called on the EDT.
     * Shows a loading spinner until the first non-null summary is received.
     */
    public void update(@NotNull WorkflowInvocationProgress progress) {
        InvocationSummary summary = progress.summary();
        if (summary == null) return;

        cardLayout.show(cards, CARD_CONTENT);

        setLink(urlLabel, shortInvocationId(summary.invocationName()), summary.gcpConsoleUrl());
        startTimeLabel.setText(TIME_FMT.format(summary.startTime()));
        statusLabel.setText(progress.state().name());
        Instant endTime = summary.endTime() != null ? summary.endTime() : Instant.now();
        durationLabel.setText(RunConfigUiUtils.formatDuration(Duration.between(summary.startTime(), endTime)));


        compilationLabel.setText(summary.compilationResultId());
        sourceTypeLabel.setText(summary.sourceType());

        String wsUrl = summary.workspaceConsoleUrl();
        if (wsUrl != null && summary.sourceWorkspaceName() != null) {
            setLink(sourceLabel, shortName(summary.sourceWorkspaceName()), wsUrl);
        } else {
            sourceLabel.setText(summary.sourceWorkspaceName() != null
                    ? shortName(summary.sourceWorkspaceName()) : "—");
        }

        contentsLabel.setText(summary.contents() != null ? summary.contents() : "Full workflow");
    }

    private void setLink(@NotNull JLabel label, @NotNull String text, @NotNull String url) {
        label.setText("<html><a href=''>" + text + "</a></html>");
        for (MouseAdapter ma : getMouseAdapters(label)) {
            label.removeMouseListener(ma);
        }
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openUrl(url);
            }
        });
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static MouseAdapter[] getMouseAdapters(@NotNull JLabel label) {
        return java.util.Arrays.stream(label.getMouseListeners())
                .filter(l -> l instanceof MouseAdapter)
                .map(l -> (MouseAdapter) l)
                .toArray(MouseAdapter[]::new);
    }

    private static void openUrl(@NotNull String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
        }
    }

    @NotNull
    private static String shortName(@NotNull String fullName) {
        int idx = fullName.lastIndexOf('/');
        return idx >= 0 ? fullName.substring(idx + 1) : fullName;
    }

    @NotNull
    private static String shortInvocationId(@NotNull String fullName) {
        return shortName(fullName);
    }

}