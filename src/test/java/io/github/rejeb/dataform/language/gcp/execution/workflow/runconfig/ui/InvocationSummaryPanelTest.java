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

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.BrowserLink;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionResult;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationActionState;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.InvocationSummary;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationProgress;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.WorkflowInvocationState;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InvocationSummaryPanelTest extends BasePlatformTestCase {

    private static final String INVOCATION =
            "projects/my-gcp-project/locations/europe-west1/repositories/sales-dataform/workflowInvocations/1764432244-7632";
    private static final Instant START = Instant.parse("2026-11-02T20:52:49Z");

    private InvocationSummaryPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new InvocationSummaryPanel("Full workflow");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(panel);
        } finally {
            super.tearDown();
        }
    }

    public void testItLoadsUntilASummaryArrives() {
        assertTrue(panel.isLoading());
        assertFalse(panel.failureBanner().isVisible());
    }

    public void testAFailureWithoutSummaryShowsABanner() {
        panel.update(new WorkflowInvocationProgress("inv", WorkflowInvocationState.FAILED, List.of(), null,
                "Compilation failed: no such action"));

        assertFalse(panel.isLoading());
        assertTrue(panel.failureBanner().isVisible());
        assertTrue(panel.failureBanner().getToolTipText().contains("Compilation failed: no such action"));
    }

    public void testTheFormIsGroupedLikeThePrototype() {
        List<String> groups = new ArrayList<>();
        collect(panel, groups);

        assertEquals(List.of("Invocation", "Actions", "Repository", "BigQuery"), groups);
    }

    public void testTheHeaderNamesTheInvocationAndCopiesItsId() {
        panel.update(progress());

        assertSame(DataformIcons.STATUS_FAILED, panel.header().icon());
        assertEquals("Workflow invocation", panel.header().title());
        assertEquals("1764432244-7632", panel.header().detail());
        List<String> actions = panel.header().actions().stream()
                .map(action -> action.getTemplatePresentation().getText()).toList();
        assertEquals(List.of("Copy Invocation ID"), actions);
    }

    public void testTheInvocationRowsDescribeTheRun() {
        panel.update(progress());

        assertEquals("Failed", panel.value("State"));
        assertEquals("22.5 s", panel.value("Duration"));
        assertEquals("Full workflow", panel.value("Run configuration"));
        assertEquals("3", panel.value("Included actions"));
    }

    public void testTheHeaderIdLinksTheInvocationInTheConsole() {
        panel.update(progress());

        BrowserLink link = panel.header().detailLink();
        assertNotNull(link);
        assertEquals("1764432244-7632", link.getText());
        assertEquals("https://console.cloud.google.com/bigquery/dataform/locations/europe-west1/repositories/sales-dataform"
                + "/workflows/1764432244-7632?project=my-gcp-project", link.getUrl());
        IdLinksTest.assertIdStyle(link);
    }

    public void testTheHeaderIdIsTheOnlyLink() {
        panel.update(progress());

        List<BrowserLink> links = new ArrayList<>();
        collectBrowserLinks(panel, links);

        assertEquals(List.of(panel.header().detailLink()), links);
    }

    public void testBeforeTheSummaryTheHeaderHasNoLink() {
        assertNull(panel.header().detailLink());
    }

    public void testTheRepositoryRowsComeFromTheInvocationName() {
        panel.update(progress());

        assertEquals("sales-dataform", panel.value("Repository"));
        assertEquals("my-gcp-project", panel.value("Project"));
        assertEquals("europe-west1", panel.value("Location"));
        assertEquals("abc123", panel.value("Compilation"));
        assertEquals("runner@my-gcp-project.iam.gserviceaccount.com", panel.value("Service account"));
    }

    public void testTheActionRowsCountTheOutcomes() {
        panel.update(progress());

        assertEquals("1", panel.value("Succeeded"));
        assertEquals("1", panel.value("Failed"));
        assertEquals("1", panel.value("Skipped"));
        assertEquals("0", panel.value("Cancelled"));
        assertEquals("0", panel.value("Remaining"));
        assertEquals(RunStatusHeader.FAILED_COUNT.getFgColor(), panel.valueField("Failed").getForeground());
    }

    private static WorkflowInvocationProgress progress() {
        InvocationSummary summary = new InvocationSummary(INVOCATION,
                "projects/my-gcp-project/locations/europe-west1/repositories/sales-dataform/compilationResults/abc123",
                "WORKSPACE", null, null, START, START.plusMillis(22_500),
                "runner@my-gcp-project.iam.gserviceaccount.com");
        return new WorkflowInvocationProgress(INVOCATION, WorkflowInvocationState.FAILED, List.of(
                action("a", InvocationActionState.SUCCEEDED), action("b", InvocationActionState.FAILED),
                action("c", InvocationActionState.SKIPPED)), summary);
    }

    private static InvocationActionResult action(String name, InvocationActionState state) {
        return new InvocationActionResult("p.ds." + name, state, null, null, null, null, null, null, null, null);
    }

    private static void collectBrowserLinks(Component root, List<BrowserLink> links) {
        if (root instanceof BrowserLink link) links.add(link);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collectBrowserLinks(child, links);
        }
    }

    private static void collect(Component root, List<String> groups) {
        if (root instanceof TitledSeparator separator) groups.add(separator.getText());
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, groups);
        }
    }
}
