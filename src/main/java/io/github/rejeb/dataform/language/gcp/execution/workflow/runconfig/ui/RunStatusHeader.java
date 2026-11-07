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

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import io.github.rejeb.dataform.language.ui.UiText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The line above the action tree of a workflow run, as in the prototype: while the run goes on,
 * "Running 3 of 10" with the time so far and a progress bar; once it ended, its state in bold then
 * how many actions succeeded, failed (in red), were skipped or cancelled, and how long it took.
 */
final class RunStatusHeader extends JPanel {

    /**
     * The attributes of the number of failed actions.
     */
    static final SimpleTextAttributes FAILED_COUNT =
            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getErrorForeground());

    private static final int GAP = 6;

    private final SimpleColoredComponent line = new SimpleColoredComponent();
    private final JProgressBar progressBar = new JProgressBar();

    RunStatusHeader() {
        super(new BorderLayout(0, JBUI.scale(GAP)));
        setOpaque(false);
        setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Separator.color()),
                JBUI.Borders.empty(8, 10)));
        line.setOpaque(false);
        line.setIpad(JBUI.emptyInsets());
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        add(line, BorderLayout.NORTH);
        add(progressBar, BorderLayout.SOUTH);
    }

    /**
     * Shows the given progress. Must be called on the EDT.
     */
    void update(@NotNull WorkflowInvocationProgress progress) {
        line.clear();
        line.setIcon(null);
        boolean running = progress.state() == WorkflowInvocationState.RUNNING;
        Duration elapsed = RunText.elapsed(progress.summary());
        ActionCounts counts = ActionCounts.of(progress.actions());
        if (running && progress.summary() == null && progress.actions().isEmpty()) {
            line.append(progress.invocationName() + "…", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        } else if (running) {
            line.append("Running " + counts.finished() + " of " + counts.total(),
                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            if (elapsed != null) line.append("  " + RunText.duration(elapsed), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else {
            line.setIcon(ActionStateIcons.of(progress.state()));
            line.append(RunText.state(progress.state()), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            appendOutcomes(counts, elapsed);
        }
        progressBar.setVisible(running);
        revalidate();
        repaint();
    }

    private void appendOutcomes(@NotNull ActionCounts counts, Duration elapsed) {
        List<Runnable> parts = new ArrayList<>();
        if (counts.succeeded() > 0) parts.add(grayed(counts.succeeded() + " succeeded"));
        if (counts.failed() > 0) parts.add(() -> line.append(counts.failed() + " failed", FAILED_COUNT));
        if (counts.skipped() > 0) parts.add(grayed(counts.skipped() + " skipped"));
        if (counts.cancelled() > 0) parts.add(grayed(counts.cancelled() + " cancelled"));
        if (elapsed != null) parts.add(grayed(RunText.duration(elapsed)));
        for (int i = 0; i < parts.size(); i++) {
            line.append(i == 0 ? "  " : UiText.SEPARATOR, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            parts.get(i).run();
        }
    }

    @NotNull
    private Runnable grayed(@NotNull String text) {
        return () -> line.append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    @NotNull
    SimpleColoredComponent line() {
        return line;
    }

    @NotNull
    JProgressBar progressBar() {
        return progressBar;
    }
}
