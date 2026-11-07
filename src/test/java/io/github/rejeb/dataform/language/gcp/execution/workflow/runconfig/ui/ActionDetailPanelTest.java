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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.JBTabbedPane;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;
import io.github.rejeb.dataform.language.ui.sqlviewer.SqlViewer;

import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.github.rejeb.dataform.language.util.Utils.formatSql;

public class ActionDetailPanelTest extends BasePlatformTestCase {

    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

    private final List<String> calls = new ArrayList<>();
    private ActionDetailPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new ActionDetailPanel(getProject(), new ActionJobDetailsCache(getProject()), new ActionNavigator() {
            @Override
            public void openSource(InvocationActionResult action) {
                calls.add("open " + action.target());
            }

            @Override
            public void run(InvocationActionResult action) {
                calls.add("run " + action.target());
            }

            @Override
            public void select(String target) {
                calls.add("select " + target);
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            panel.release();
        } finally {
            super.tearDown();
        }
    }

    public void testTheTabsAreSummaryExecutedSqlAndJobs() {
        assertEquals(List.of("Summary", "Executed SQL", "Jobs"), titles(panel.tabs()));
    }

    public void testAFailedActionShowsItsReasonInAnErrorBanner() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.FAILED, "Not found: Table p.ds.src"));

        InlineBanner banner = panel.banner();
        assertNotNull(banner);
        assertEquals(EditorNotificationPanel.Status.Error, panel.bannerStatus());
        assertTrue(banner.getToolTipText().contains("Not found: Table p.ds.src"));
        assertEquals(List.of("Show in editor", "Copy error"), links(banner));
    }

    public void testShowInEditorOpensTheSource() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.FAILED, "boom"));

        click(panel.banner(), "Show in editor");

        assertEquals(List.of("open p.ds.users"), calls);
    }

    public void testASkippedActionLinksTheFailedAction() {
        InvocationActionResult failed = action("p.ds.src", InvocationActionState.FAILED, "boom");
        InvocationActionResult skipped = action("p.ds.users", InvocationActionState.SKIPPED, null);
        panel.show(progress(failed, skipped), skipped);

        assertEquals(EditorNotificationPanel.Status.Info, panel.bannerStatus());
        assertEquals(List.of("ds.src"), links(panel.banner()));
        click(panel.banner(), "ds.src");
        assertEquals(List.of("select p.ds.src"), calls);
    }

    public void testASkippedActionWithoutFailureHasNoBanner() {
        InvocationActionResult skipped = action("p.ds.users", InvocationActionState.SKIPPED, null);
        panel.show(progress(skipped), skipped);

        assertNull(panel.banner());
    }

    public void testACancelledActionShowsAWarning() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.CANCELLED, null));

        assertEquals(EditorNotificationPanel.Status.Warning, panel.bannerStatus());
    }

    public void testASucceededActionHasNoBanner() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.SUCCEEDED, null));

        assertNull(panel.banner());
    }

    public void testTheHeaderDescribesTheAction() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.FAILED, "boom"));

        assertSame(DataformIcons.STATUS_FAILED, panel.header().icon());
        assertEquals("ds.users", panel.header().title());
        assertEquals("41 s", panel.header().detail());
    }

    public void testTheHeaderJumpsToTheSourceAndRunsTheAction() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.SUCCEEDED, null));

        List<String> texts = panel.header().actions().stream()
                .map(action -> action.getTemplatePresentation().getText()).toList();
        assertEquals(List.of("Jump to Source", "Run This Action"), texts);
    }

    public void testTheSummaryIsGroupedByExecutionAndBigQuery() {
        List<String> groups = new ArrayList<>();
        collect(panel.tabs().getComponentAt(0), groups);

        assertEquals(List.of("Execution", "BigQuery"), groups);
    }

    public void testTheSummaryRowsFollowThePrototype() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.FAILED, "boom"));

        ActionSummaryPanel summary = panel.summary();
        assertEquals("Failed", summary.value("Status"));
        assertEquals("p.ds.users", summary.value("Target"));
        assertEquals("41 s", summary.value("Duration"));
        assertEquals("EU", summary.value("Location"));
        assertNotNull(summary.value("Ended"));
    }

    public void testTheSummaryJobIdIsItsOnlyLinkInTheIdStyle() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.SUCCEEDED, null));

        List<BrowserLink> links = new ArrayList<>();
        collectBrowserLinks(panel.tabs().getComponentAt(0), links);

        assertEquals(List.of("—"), links.stream().map(BrowserLink::getText).toList());
        links.forEach(IdLinksTest::assertIdStyle);
    }

    public void testTheSlotTimeComesFromTheJob() {
        panel.summary().showJobDetails(new BigQueryJobDetails("job", "p", "EU", "DONE", null, 1L, 1L, START,
                START.plusSeconds(4), 1, List.of(), null, 13_000L));

        assertEquals("13 s", panel.summary().value("Slot time"));
    }

    public void testTheExecutedSqlScrollsWithoutAScrollbar() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.SUCCEEDED, null));

        List<JScrollPane> scrollPanes = new ArrayList<>();
        collectScrollPanes(panel.tabs().getComponentAt(1), scrollPanes);

        assertEquals(1, scrollPanes.size());
        JScrollPane sqlScrollPane = scrollPanes.getFirst();
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, sqlScrollPane.getVerticalScrollBarPolicy());
        assertEquals(0, sqlScrollPane.getVerticalScrollBar().getPreferredSize().width);
    }

    public void testTheExecutedSqlIsShownInTheSqlViewer() {
        panel.show(progress(), action("p.ds.users", InvocationActionState.SUCCEEDED, null));

        List<SqlViewer> viewers = new ArrayList<>();
        collectViewers(panel.tabs().getComponentAt(1), viewers);

        assertEquals(1, viewers.size());
        assertEquals(formatSql(getProject(), "SELECT 1"), viewers.getFirst().text());
    }

    public void testTheJobsTabCountsTheChildJobs() {
        BigQueryJobDetails details = new BigQueryJobDetails("job", "p", "EU", "DONE", null, 1L, 1L, START,
                START.plusSeconds(4), 2, List.of(child("a"), child("b")), null);

        panel.showJobs(action("p.ds.users", InvocationActionState.SUCCEEDED, null), details);

        assertEquals("Jobs (2)", panel.tabs().getTitleAt(2));
    }

    public void testTheSingleStatementLinkSelectsTheExecutedSqlTab() {
        BigQueryJobDetails details = new BigQueryJobDetails("job", "p", "EU", "DONE", null, 1L, 1L, START,
                START.plusSeconds(4), 1, List.of(), null);
        panel.showJobs(action("p.ds.users", InvocationActionState.SUCCEEDED, null), details);

        panel.jobs().openExecutedSql();

        assertEquals(1, panel.tabs().getSelectedIndex());
    }

    private static BigQueryJobDetails.BigQueryChildJob child(String id) {
        return new BigQueryJobDetails.BigQueryChildJob(id, "DONE", START, START.plusSeconds(1), "SELECT 1", 1L);
    }

    private static WorkflowInvocationProgress progress(InvocationActionResult... actions) {
        return new WorkflowInvocationProgress("projects/p/locations/EU/repositories/r/workflowInvocations/i",
                WorkflowInvocationState.FAILED, List.of(actions), null);
    }

    private static InvocationActionResult action(String target, InvocationActionState state, String failure) {
        return new InvocationActionResult(target, state, failure, START, START.plusSeconds(41), null, null,
                "EU", null, "SELECT 1");
    }

    private static List<String> titles(JBTabbedPane tabs) {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));
        return titles;
    }

    private static List<String> links(Component root) {
        List<String> texts = new ArrayList<>();
        collectLinks(root, texts, null);
        return texts;
    }

    private static void click(Component root, String text) {
        List<LinkLabel<?>> found = new ArrayList<>();
        collectLinks(root, new ArrayList<>(), found);
        found.stream().filter(link -> text.equals(link.getText())).findFirst().orElseThrow().doClick();
    }

    private static void collectLinks(Component root, List<String> texts, List<LinkLabel<?>> links) {
        if (root instanceof LinkLabel<?> link && link.isVisible()) {
            texts.add(link.getText());
            if (links != null) links.add(link);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collectLinks(child, texts, links);
        }
    }

    private static void collectBrowserLinks(Component root, List<BrowserLink> links) {
        if (root instanceof BrowserLink link) links.add(link);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collectBrowserLinks(child, links);
        }
    }

    private static void collectViewers(Component root, List<SqlViewer> viewers) {
        if (root instanceof SqlViewer viewer) viewers.add(viewer);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collectViewers(child, viewers);
        }
    }

    private static void collectScrollPanes(Component root, List<JScrollPane> scrollPanes) {
        if (root instanceof JScrollPane scrollPane) scrollPanes.add(scrollPane);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collectScrollPanes(child, scrollPanes);
        }
    }

    private static void collect(Component root, List<String> groups) {
        if (root instanceof TitledSeparator separator) groups.add(separator.getText());
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, groups);
        }
    }
}
