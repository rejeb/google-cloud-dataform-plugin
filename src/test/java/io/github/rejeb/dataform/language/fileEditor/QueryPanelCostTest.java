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
package io.github.rejeb.dataform.language.fileEditor;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.icons.AllIcons;
import io.github.rejeb.dataform.language.fileEditor.cost.CostEstimate;
import io.github.rejeb.dataform.language.fileEditor.cost.QueryRunCosts;
import io.github.rejeb.dataform.language.fileEditor.cost.RunCost;

import java.util.List;

/**
 * Cost estimates land in the header of the action they belong to, and the file total shows in the
 * preview header from the first estimate.
 */
public class QueryPanelCostTest extends BasePlatformTestCase {

    private static final long GIB = 1L << 30;

    private QueryPanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        panel = new QueryPanel(getProject(), PlainTextFileType.INSTANCE);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            panel.dispose();
        } finally {
            super.tearDown();
        }
    }

    public void testAnEstimateShowsInTheHeaderOfItsAction() {
        panel.setContent(List.of(query("a"), query("b")));

        panel.showEstimate("a", CostEstimate.success(Math.round(4.82 * GIB), "EU"));

        assertTrue(panel.costLabelOf("a").getText(), panel.costLabelOf("a").getText().contains("≈ $"));
        assertEquals("", panel.costLabelOf("b").getText());
        assertTrue(panel.costLabelOf("a").getToolTipText(),
                panel.costLabelOf("a").getToolTipText().startsWith("<html>Dry-run estimate of the query shown.<br>EU · "));
    }

    public void testASmallQueryIsPricedAtTheMinimumBilled() {
        panel.setContent(List.of(query("small"), query("minimum")));

        panel.showEstimate("small", CostEstimate.success(593, "EU", CostEstimate.MINIMUM_BILLED_BYTES));
        panel.showEstimate("minimum", CostEstimate.success(CostEstimate.MINIMUM_BILLED_BYTES, "EU"));

        assertTrue(panel.costLabelOf("small").getText(), panel.costLabelOf("small").getText().startsWith("593 B"));
        String small = panel.costLabelOf("small").getText();
        String minimum = panel.costLabelOf("minimum").getText();
        assertEquals(minimum.substring(minimum.indexOf("≈")), small.substring(small.indexOf("≈")));
        assertTrue(panel.costLabelOf("small").getToolTipText(),
                panel.costLabelOf("small").getToolTipText().contains("BigQuery's 10 MB minimum"));
    }

    public void testAnEstimateForAnActionNoLongerShownIsIgnored() {
        panel.setContent(List.of(query("a")));

        panel.showEstimate("gone", CostEstimate.success(GIB, "EU"));

        assertEquals("", panel.costLabelOf("a").getText());
        assertFalse(panel.costTotalBar().isVisible());
    }

    public void testTheTotalAppearsFromTheFirstEstimate() {
        panel.setContent(List.of(query("a"), query("b"), query("c")));
        panel.markEstimating("a");
        panel.markEstimating("b");
        panel.markEstimating("c");

        panel.showEstimate("a", CostEstimate.success(GIB, "EU"));
        assertTrue(panel.costTotalBar().isVisible());
        assertEquals("Estimated (1 of 3)", panel.costTotalBar().titleText());

        panel.showEstimate("b", CostEstimate.success(GIB, "EU"));
        panel.showEstimate("c", CostEstimate.success(GIB, "EU"));
        assertEquals("Estimated", panel.costTotalBar().titleText());
        assertTrue(panel.costTotalBar().valueText(), panel.costTotalBar().valueText().matches("3[.,]00 GB · ≈ \\$.*"));
    }

    public void testTheTooltipOfTheTotalSpeaksOfTheQueriesOfTheFile() {
        panel.setContent(List.of(query("a"), query("b")));
        panel.markEstimating("a");
        panel.markEstimating("b");

        panel.showEstimate("a", CostEstimate.success(GIB, "EU", CostEstimate.MINIMUM_BILLED_BYTES));
        panel.showEstimate("b", CostEstimate.success(GIB, "EU", CostEstimate.MINIMUM_BILLED_BYTES));

        String tooltip = panel.costTotalBar().tooltipText();
        assertTrue(tooltip, tooltip.startsWith("<html>Dry-run estimate of the queries of this file.<br>EU · "));
        assertFalse(tooltip, tooltip.contains("minimum"));
    }

    public void testTheTooltipOfTheTotalSaysWhenTheMinimumApplies() {
        panel.setContent(List.of(query("small"), query("large")));
        panel.markEstimating("small");
        panel.markEstimating("large");

        panel.showEstimate("small", CostEstimate.success(593, "EU", CostEstimate.MINIMUM_BILLED_BYTES));
        panel.showEstimate("large", CostEstimate.success(GIB, "EU", CostEstimate.MINIMUM_BILLED_BYTES));

        String tooltip = panel.costTotalBar().tooltipText();
        assertTrue(tooltip, tooltip.contains("BigQuery's 10 MB minimum"));
        assertTrue(tooltip, tooltip.contains("<br>EU · "));
    }

    public void testWhileEstimatingTheHeaderSaysSo() {
        panel.setContent(List.of(query("a"), query("b")));

        panel.markEstimating("a");
        panel.markEstimating("b");

        assertTrue(panel.costTotalBar().isVisible());
        assertEquals("Estimating…", panel.costTotalBar().titleText());
        assertEquals("", panel.costTotalBar().valueText());
    }

    public void testSignedOutTheHeaderOffersToSignIn() {
        panel.setContent(List.of(query("a"), query("b")));

        panel.markSignedOut();

        assertTrue(panel.costTotalBar().isVisible());
        assertTrue(panel.costTotalBar().signInLink().isVisible());
        assertEquals("Sign in to estimate the cost", panel.costTotalBar().signInLink().getText());
    }

    public void testANotCompiledActionStaysNotCompiledWhenSignedOut() {
        panel.setContent(List.of(query("a"), new FormattedCompiledQuery("b", null, null, null, null, "boom")));

        panel.markSignedOut();

        assertEquals("Sign in to estimate", panel.costLabelOf("a").getText());
        assertEquals("Not compiled", panel.costLabelOf("b").getText());
    }

    public void testTheHeaderCountsTheActions() {
        panel.setContent(List.of(query("a"), query("b")));
        assertEquals("2 actions", panel.header().summaryText());

        panel.setContent(List.of(query("a")));
        assertEquals("1 action", panel.header().summaryText());
    }

    public void testTheHeaderSaysWhenTheGraphWasCompiled() {
        long compiledAt = java.time.LocalDate.of(2026, 11, 2).atTime(14, 32)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        panel.setContent(List.of(query("a"), query("b")));

        panel.showCompilation(compiledAt, false);

        assertEquals("2 actions · compiled 14:32", panel.header().summaryText());
        assertFalse(panel.header().compileFailedLink().isVisible());
    }

    public void testTheHeaderSaysWhenTheLastCompileFailed() {
        panel.setContent(List.of(query("a")));

        panel.showCompilation(-1, true);

        assertEquals("1 action", panel.header().summaryText());
        assertTrue(panel.header().compileFailedLink().isVisible());
        assertEquals("last compile failed", panel.header().compileFailedLink().getText());
    }

    public void testAFailedEstimateSaysSoAndKeepsTheMessageForTheTooltip() {
        panel.setContent(List.of(query("a")));

        panel.showEstimate("a", CostEstimate.failure("Not found: Table p:d.upstream"));

        assertEquals("Can't estimate", panel.costLabelOf("a").getText());
        assertEquals("Not found: Table p:d.upstream", panel.costLabelOf("a").getToolTipText());
    }

    public void testSignedOutEveryActionAsksToSignIn() {
        panel.setContent(List.of(query("a"), query("b")));

        panel.markSignedOut();

        assertEquals("Sign in to estimate", panel.costLabelOf("a").getText());
        assertEquals("Sign in to estimate", panel.costLabelOf("b").getText());
    }

    public void testNewContentClearsTheTotal() {
        panel.setContent(List.of(query("a"), query("b")));
        panel.showEstimate("a", CostEstimate.success(GIB, "EU"));
        panel.showEstimate("b", CostEstimate.success(GIB, "EU"));

        panel.setContent(List.of(query("a"), query("b")));

        assertFalse(panel.costTotalBar().isVisible());
        assertEquals("", panel.costLabelOf("a").getText());
    }

    public void testAnAssertionInATabGetsItsEstimateAndCounts() {
        FormattedCompiledQuery uniqueKey = query("p.d_assertions.d_a_assertions_uniqueKey_0");
        panel.setContent(List.of(query("a").withAssertions(List.of(uniqueKey))));
        panel.markEstimating("a");
        panel.markEstimating(uniqueKey.tableName());

        panel.showEstimate(uniqueKey.tableName(), CostEstimate.success(GIB, "EU"));

        assertTrue(panel.costLabelOf(uniqueKey.tableName()).getText().contains("≈ $"));
        assertEquals("Estimated (1 of 2)", panel.costTotalBar().titleText());
        assertEquals("2 actions", panel.header().summaryText());
    }

    public void testAScriptAsksToRunTheQueryInsteadOfShowingBytes() {
        panel.setContent(List.of(query("script.unmeasured")));
        panel.markEstimating("script.unmeasured", "declare v int64;\nselect 1;");

        panel.showEstimate("script.unmeasured", CostEstimate.unevaluated("EU"));

        CostLabel label = panel.costLabelOf("script.unmeasured");
        assertEquals("Run to estimate", label.getText());
        assertSame(AllIcons.General.Warning, label.getIcon());
        assertTrue(label.getToolTipText(), label.getToolTipText().contains("cannot estimate"));
        assertTrue(label.getToolTipText(), label.getToolTipText().contains("Run Query"));
        assertTrue(panel.needsRun("script.unmeasured"));
    }

    public void testAScriptIsLeftOutOfTheEstimatedTotal() {
        panel.setContent(List.of(query("a"), query("script.total")));
        panel.markEstimating("a", "select 1");
        panel.markEstimating("script.total", "declare v int64;\nselect 1;");

        panel.showEstimate("a", CostEstimate.success(GIB, "EU"));
        panel.showEstimate("script.total", CostEstimate.unevaluated("EU"));

        assertEquals("Estimated (1 of 2)", panel.costTotalBar().titleText());
        assertTrue(panel.costTotalBar().valueText(), panel.costTotalBar().valueText().startsWith("1"));
    }

    public void testAFileOfScriptsOnlyHasNoEstimatedTotal() {
        panel.setContent(List.of(query("script.alone")));
        panel.markEstimating("script.alone", "declare v int64;\nselect 1;");

        panel.showEstimate("script.alone", CostEstimate.unevaluated("EU"));

        assertFalse(panel.costTotalBar().isVisible());
    }

    public void testAScriptShowsTheBytesAndCostOfItsLastRun() {
        String sql = "declare v int64;\nselect 1;";
        QueryRunCosts.getInstance(getProject()).record("script.measured", new RunCost(sql, 42 * GIB, 42 * GIB, "EU"));
        panel.setContent(List.of(query("script.measured")));
        panel.markEstimating("script.measured", sql);

        panel.showEstimate("script.measured", CostEstimate.unevaluated("EU"));

        CostLabel label = panel.costLabelOf("script.measured");
        assertTrue(label.getText(), label.getText().matches("42[.,]00 GB · \\$.*"));
        assertTrue(label.getToolTipText(),
                label.getToolTipText().startsWith("<html>Measured by the last run of this query.<br>EU · "));
        assertFalse(panel.needsRun("script.measured"));
    }

    public void testTheCostOfARunIsOutdatedOnceTheQueryChanged() {
        QueryRunCosts.getInstance(getProject())
                .record("script.outdated", new RunCost("declare v int64;\nselect 1;", 42 * GIB, 42 * GIB, "EU"));
        panel.setContent(List.of(query("script.outdated")));
        panel.markEstimating("script.outdated", "declare v int64;\nselect 2;");

        panel.showEstimate("script.outdated", CostEstimate.unevaluated("EU"));

        CostLabel label = panel.costLabelOf("script.outdated");
        assertTrue(label.getText(), label.getText().matches("42[.,]00 GB · \\$.*"));
        assertSame(AllIcons.General.Warning, label.getIcon());
        assertTrue(label.getToolTipText(), label.getToolTipText().contains("Run the query again to update it."));
        assertTrue(panel.needsRun("script.outdated"));
    }

    public void testTheCostShowsOnceTheRunOfAScriptEnds() {
        String sql = "declare v int64;\nselect 1;";
        panel.setContent(List.of(query("script.run")));
        panel.markEstimating("script.run", sql);
        panel.showEstimate("script.run", CostEstimate.unevaluated("EU"));

        panel.markMeasuring("script.run");
        assertEquals("Measuring…", panel.costLabelOf("script.run").getText());

        QueryRunCosts.getInstance(getProject()).record("script.run", new RunCost(sql, 42 * GIB, 42 * GIB, "EU"));
        panel.showRunCost("script.run");

        assertTrue(panel.costLabelOf("script.run").getText(),
                panel.costLabelOf("script.run").getText().matches("42[.,]00 GB · \\$.*"));
    }

    public void testAFailedRunOfAScriptAsksToRunAgain() {
        panel.setContent(List.of(query("script.failed")));
        panel.markEstimating("script.failed", "declare v int64;\nselect 1;");
        panel.showEstimate("script.failed", CostEstimate.unevaluated("EU"));
        panel.markMeasuring("script.failed");

        panel.showRunCost("script.failed");

        assertEquals("Run to estimate", panel.costLabelOf("script.failed").getText());
    }

    public void testAnEstimatedQueryKeepsItsEstimateAfterARun() {
        QueryRunCosts.getInstance(getProject()).record("plain.run", new RunCost("select 1", 42 * GIB, 42 * GIB, "EU"));
        panel.setContent(List.of(query("plain.run")));
        panel.markEstimating("plain.run", "select 1");
        panel.showEstimate("plain.run", CostEstimate.success(GIB, "EU"));

        panel.markMeasuring("plain.run");
        panel.showRunCost("plain.run");

        assertTrue(panel.costLabelOf("plain.run").getText(), panel.costLabelOf("plain.run").getText().contains("≈ $"));
        assertFalse(panel.needsRun("plain.run"));
    }

    public void testAnActionNotEstimatedYetNeedsNoRun() {
        panel.setContent(List.of(query("pending")));

        assertFalse(panel.needsRun("pending"));
        assertFalse(panel.needsRun("gone"));
    }

    public void testSectionsKeepTheirHeightWhenThePreviewIsTaller() {
        panel.setContent(List.of(query("a"), query("b")));
        panel.setSize(700, 2000);
        layOut(panel);

        TableQuerySection first = (TableQuerySection) panel.costLabelOf("a").getParent().getParent().getParent();

        assertEquals(first.getPreferredSize().height, first.getHeight());
    }

    private static void layOut(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) layOut(nested);
        }
    }

    private static FormattedCompiledQuery query(String name) {
        return new FormattedCompiledQuery(name, null, null, "SELECT 1", null, null);
    }
}
