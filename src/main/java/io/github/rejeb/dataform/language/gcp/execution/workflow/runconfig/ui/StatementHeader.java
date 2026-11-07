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
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.time.ZoneId;

/**
 * The header of the SQL of the selected statement: its kind in bold, the full target grayed, its job
 * id as a link to the BigQuery console and the local times it ran between.
 */
final class StatementHeader extends JPanel {

    private static final int GAP = 8;

    private final SimpleColoredComponent kindAndTarget = new SimpleColoredComponent();
    private final JBLabel timeRange = new JBLabel();

    /**
     * Creates an empty header.
     */
    StatementHeader() {
        super(new HorizontalLayout(JBUI.scale(GAP), SwingConstants.CENTER));
        setOpaque(false);
        kindAndTarget.setOpaque(false);
        kindAndTarget.setIpad(JBUI.emptyInsets());
        timeRange.setForeground(SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
    }

    /**
     * Describes the given statement of a job of the given project and location, its times in the
     * given zone.
     */
    void show(@NotNull BigQueryChildJob job, @NotNull String project, @Nullable String location, @NotNull ZoneId zone) {
        kindAndTarget.clear();
        kindAndTarget.append(RunText.statementKind(job.statementType(), job.query()),
                SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        if (job.target() != null) kindAndTarget.append("  " + job.target(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        BrowserLink jobLink = IdLinks.create(job.jobId(), RunConfigUiUtils.bigQueryJobUrl(project, location, job.jobId()),
                "Open the job in the BigQuery console");
        timeRange.setText(RunText.timeRange(job.startTime(), job.endTime(), zone));
        removeAll();
        add(kindAndTarget);
        add(jobLink);
        add(timeRange);
        revalidate();
        repaint();
    }

    /**
     * The texts shown, in the order they are laid out, joined by single spaces.
     */
    @NotNull
    String text() {
        return ShownText.of(this);
    }
}
