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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.ui.EmptyStates;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Insets;
import java.time.ZoneId;
import java.util.List;

/**
 * The Jobs tab of an action of a workflow run: a line summing up the BigQuery script job, the
 * timeline of its statements and, below a divider, the SQL of the selected statement with its
 * header. A failed statement opens selected, its error marked in its SQL when BigQuery's position
 * can be placed on it. A job that ran a single statement points to the Executed SQL tab instead.
 */
public class ActionDetailsTabPanel extends JPanel implements Disposable {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_SINGLE = "single";
    private static final String CARD_CONTENT = "content";
    private static final String SQL = "sql";
    private static final String NO_SQL = "no sql";
    private static final String FAILED = "FAILED";
    private static final float INITIAL_PROPORTION = 0.3f;
    private static final float MIN_PROPORTION = 0.1f;
    private static final float MAX_PROPORTION = 0.6f;
    private static final int GAP = 10;
    private static final int SCRIPT_LINE_GAP = 8;

    private final Runnable showExecutedSql;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final CardLayout sqlLayout = new CardLayout();
    private final JPanel sqlCards = new JPanel(sqlLayout);
    private final JBLoadingPanel loadingPanel;
    private final JPanel scriptLine = new JPanel(new HorizontalLayout(JBUI.scale(SCRIPT_LINE_GAP), SwingConstants.CENTER));
    private final JBLabel scriptKind = grayed();
    private final JBLabel scriptSummary = grayed();
    private final ChildJobsTimeline timeline = new ChildJobsTimeline();
    private final JScrollPane timelineScroll = ScrollPaneFactory.createScrollPane(timeline, true);
    private final StatementHeader header = new StatementHeader();
    private final SqlViewer viewer;
    private final OnePixelSplitter splitter;
    private @Nullable InvocationActionResult action;
    private @Nullable BigQueryJobDetails details;
    private String shownCard = CARD_EMPTY;
    private String shownSqlCard = SQL;
    private boolean fitting;
    private boolean userMoved;

    /**
     * @param showExecutedSql selects the Executed SQL tab, for a job that ran a single statement
     */
    public ActionDetailsTabPanel(@NotNull Project project, @NotNull Runnable showExecutedSql) {
        super(new BorderLayout());
        this.showExecutedSql = showExecutedSql;
        setOpaque(false);
        viewer = new SqlViewer(project, SqlViewerOptions.fill());
        viewer.setHeader(header);
        splitter = new OnePixelSplitter(true, INITIAL_PROPORTION) {
            @Override
            public void doLayout() {
                fitTimeline();
                super.doLayout();
            }
        };
        splitter.addPropertyChangeListener(Splitter.PROP_PROPORTION, event -> {
            if (!fitting) userMoved = true;
        });
        splitter.setFirstComponent(timelineScroll);
        splitter.setSecondComponent(sqlArea());

        scriptLine.setOpaque(false);
        scriptLine.setBorder(JBUI.Borders.emptyBottom(GAP));

        JBPanelWithEmptyText single = new JBPanelWithEmptyText();
        single.setOpaque(false);
        EmptyStates.show(single.getEmptyText(), "This job ran a single statement.", "Show Executed SQL",
                this::openExecutedSql);
        cards.setOpaque(false);
        cards.add(new JBPanelWithEmptyText().withEmptyText("No BigQuery jobs for this action"), CARD_EMPTY);
        cards.add(single, CARD_SINGLE);
        cards.add(splitter, CARD_CONTENT);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(JBUI.Borders.empty(12, 0, 16, 0));
        body.add(scriptLine, BorderLayout.NORTH);
        body.add(cards, BorderLayout.CENTER);
        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.setOpaque(false);
        loadingPanel.add(body, BorderLayout.CENTER);
        add(loadingPanel, BorderLayout.CENTER);

        timeline.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showSelectedStatement();
        });
        showCard(CARD_EMPTY);
    }

    /**
     * Shows the loading card while the job details of the selected action are fetched.
     */
    public void showLoading() {
        loadingPanel.startLoading();
    }

    /**
     * Shows the BigQuery job of an action, or the empty card when it has none. Must be called on the
     * EDT.
     */
    public void showDetails(@NotNull InvocationActionResult shownAction, @Nullable BigQueryJobDetails shownDetails) {
        loadingPanel.stopLoading();
        action = shownAction;
        details = shownDetails;
        if (shownDetails == null) {
            showCard(CARD_EMPTY);
            return;
        }
        showScriptLine(shownDetails);
        if (shownDetails.childJobs().isEmpty()) {
            showCard(CARD_SINGLE);
            return;
        }
        timeline.setJobs(shownDetails);
        showCard(CARD_CONTENT);
        fitTimeline();
    }

    @Override
    public void dispose() {
        viewer.dispose();
    }

    @NotNull
    String shownCard() {
        return shownCard;
    }

    @NotNull
    ChildJobsTimeline timeline() {
        return timeline;
    }

    @NotNull
    SqlViewer viewer() {
        return viewer;
    }

    @NotNull
    StatementHeader header() {
        return header;
    }

    @NotNull
    OnePixelSplitter splitter() {
        return splitter;
    }

    @NotNull
    String scriptLineText() {
        return ShownText.of(scriptLine);
    }

    boolean showsNoSqlText() {
        return NO_SQL.equals(shownSqlCard);
    }

    void openExecutedSql() {
        showExecutedSql.run();
    }

    @NotNull
    private JComponent sqlArea() {
        JBPanelWithEmptyText noSql = new JBPanelWithEmptyText().withEmptyText("BigQuery returned no SQL for this statement.");
        noSql.setOpaque(false);
        sqlCards.setOpaque(false);
        sqlCards.setBorder(JBUI.Borders.emptyTop(GAP));
        sqlCards.add(viewer, SQL);
        sqlCards.add(noSql, NO_SQL);
        return sqlCards;
    }

    private void showScriptLine(@NotNull BigQueryJobDetails shown) {
        scriptKind.setText(shown.childJobs().isEmpty() ? "Job" : "Script job");
        BrowserLink jobLink = IdLinks.create(shown.jobId(),
                RunConfigUiUtils.bigQueryJobUrl(shown.project(), shown.location(), shown.jobId()),
                "Open the job in the BigQuery console");
        String summary = RunText.scriptSummary(shown);
        scriptSummary.setText(summary.isEmpty() ? "" : "· " + summary);
        scriptLine.removeAll();
        scriptLine.add(scriptKind);
        scriptLine.add(jobLink);
        if (!summary.isEmpty()) scriptLine.add(scriptSummary);
        scriptLine.revalidate();
        scriptLine.repaint();
    }

    private void showSelectedStatement() {
        BigQueryChildJob job = timeline.selectedJob();
        BigQueryJobDetails shown = details;
        if (job == null || shown == null) return;
        header.show(job, shown.project(), shown.location(), ZoneId.systemDefault());
        if (job.query() == null || job.query().isBlank()) {
            viewer.setText(null);
            showSqlCard(NO_SQL);
            return;
        }
        viewer.setText(job.query());
        showSqlCard(SQL);
        if (FAILED.equals(job.status())) markError(shown, timeline.getSelectedRow());
    }

    private void markError(@NotNull BigQueryJobDetails shown, int index) {
        InvocationActionResult shownAction = action;
        if (shownAction == null) return;
        String message = shown.errorMessage() != null && !shown.errorMessage().isBlank()
                ? shown.errorMessage() : shownAction.failureReason();
        if (message == null) return;
        List<String> queries = shown.childJobs().stream().map(BigQueryChildJob::query).toList();
        StatementErrorLocator.Position position =
                StatementErrorLocator.locate(message, shownAction.sqlScript(), queries, index);
        if (position != null) {
            viewer.markError(position.line(), position.column(), StatementErrorLocator.withoutPosition(message));
        }
    }

    private void fitTimeline() {
        int available = splitter.getHeight() - splitter.getDividerWidth();
        if (userMoved || available <= 0 || !CARD_CONTENT.equals(shownCard)) return;
        Insets insets = timelineScroll.getInsets();
        int wanted = timeline.fittedHeight(ChildJobsTimeline.FITTED_ROWS) + insets.top + insets.bottom;
        float proportion = Math.clamp((float) wanted / available, MIN_PROPORTION, MAX_PROPORTION);
        fitting = true;
        try {
            splitter.setProportion(proportion);
        } finally {
            fitting = false;
        }
    }

    private void showCard(@NotNull String card) {
        shownCard = card;
        cardLayout.show(cards, card);
        scriptLine.setVisible(!CARD_EMPTY.equals(card));
        revalidate();
        repaint();
    }

    private void showSqlCard(@NotNull String card) {
        shownSqlCard = card;
        sqlLayout.show(sqlCards, card);
    }

    @NotNull
    private static JBLabel grayed() {
        JBLabel label = new JBLabel();
        label.setForeground(SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
        return label;
    }
}
