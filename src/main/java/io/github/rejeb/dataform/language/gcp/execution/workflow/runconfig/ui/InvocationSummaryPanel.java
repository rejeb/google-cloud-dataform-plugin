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
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import javax.swing.*;

public class InvocationSummaryPanel extends JPanel {

    private static final String CARD_LOADING = "loading";
    private static final String CARD_CONTENT = "content";
    private static final String CARD_ERROR = "error";


    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private final JLabel urlLabel = new JBLabel();
    private final JLabel sourceLabel = new JBLabel();
    private final JBTextField startTimeLabel = RunConfigUiUtils.selectableValue("");
    private final JBTextField statusLabel = RunConfigUiUtils.selectableValue("");
    private final JBTextField durationLabel = RunConfigUiUtils.selectableValue("—");
    private final JBTextField compilationLabel = RunConfigUiUtils.selectableValue("");
    private final JBTextField sourceTypeLabel = RunConfigUiUtils.selectableValue("");
    private final JBTextField contentsLabel = RunConfigUiUtils.selectableValue("");
    private final JBTextArea errorText = RunConfigUiUtils.selectableTextArea("");

    public InvocationSummaryPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8, 12));
        setBackground(UIUtil.getPanelBackground());

        cards.add(buildLoadingCard(), CARD_LOADING);
        cards.add(buildContentCard(), CARD_CONTENT);
        cards.add(buildErrorCard(), CARD_ERROR);
        cardLayout.show(cards, CARD_LOADING);

        add(cards, BorderLayout.CENTER);
    }

    private JPanel buildErrorCard() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(UIUtil.getPanelBackground());

        JBLabel title = new JBLabel("Workflow execution failed");
        title.setForeground(UIUtil.getErrorForeground());
        title.setFont(title.getFont().deriveFont(Font.BOLD));

        panel.add(title, BorderLayout.NORTH);
        panel.add(ScrollPaneFactory.createScrollPane(errorText, true), BorderLayout.CENTER);
        return panel;
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

        RunConfigUiUtils.addKeyValueRows(panel, keys, valueLabels, true);

        return panel;
    }

    /**
     * Refreshes all fields from the given progress. Must be called on the EDT.
     * Shows a loading spinner until the first non-null summary is received.
     */
    public void update(@NotNull WorkflowInvocationProgress progress) {
        InvocationSummary summary = progress.summary();
        if (summary == null) {
            if (progress.errorMessage() != null) {
                errorText.setText(progress.errorMessage());
                errorText.setCaretPosition(0);
                cardLayout.show(cards, CARD_ERROR);
            }
            return;
        }

        cardLayout.show(cards, CARD_CONTENT);

        RunConfigUiUtils.setLink(urlLabel, RunConfigUiUtils.shortName(summary.invocationName()), summary.gcpConsoleUrl());
        startTimeLabel.setText(RunConfigUiUtils.DATE_TIME.format(summary.startTime()));
        statusLabel.setText(progress.state().name());
        Instant endTime = summary.endTime() != null ? summary.endTime() : Instant.now();
        durationLabel.setText(RunConfigUiUtils.formatDuration(Duration.between(summary.startTime(), endTime)));

        compilationLabel.setText(summary.compilationResultId());
        sourceTypeLabel.setText(summary.sourceType());

        String wsUrl = summary.workspaceConsoleUrl();
        if (wsUrl != null && summary.sourceWorkspaceName() != null) {
            RunConfigUiUtils.setLink(sourceLabel, RunConfigUiUtils.shortName(summary.sourceWorkspaceName()), wsUrl);
        } else {
            sourceLabel.setText(summary.sourceWorkspaceName() != null
                    ? RunConfigUiUtils.shortName(summary.sourceWorkspaceName()) : "—");
        }

        contentsLabel.setText(summary.contents() != null ? summary.contents() : "Full workflow");
    }
}
