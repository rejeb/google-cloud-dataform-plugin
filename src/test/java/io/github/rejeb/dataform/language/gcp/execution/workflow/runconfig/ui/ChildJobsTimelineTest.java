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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import io.github.rejeb.dataform.language.util.Utils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ChildJobsTimelineTest extends BasePlatformTestCase {

    private static final Instant START = Instant.parse("2026-11-03T10:00:00Z");

    private final AtomicInteger clock = new AtomicInteger();
    private ChildJobsTimeline timeline;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        timeline = new ChildJobsTimeline();
    }

    public void testTheFailedStatementIsSelectedFirst() {
        timeline.setJobs(details(done("a"), done("b"), failed("c")));

        assertEquals("c", selected().jobId());
    }

    public void testWithoutFailureTheLastStatementIsSelected() {
        timeline.setJobs(details(done("a"), done("b")));

        assertEquals("b", selected().jobId());
    }

    public void testTheSelectionIsKeptWhenTheSameJobsAreShownAgain() {
        BigQueryJobDetails details = details(done("a"), done("b"));

        timeline.setJobs(details);
        timeline.setRowSelectionInterval(0, 0);
        timeline.setJobs(details);

        assertEquals("a", selected().jobId());
    }

    public void testASelectionThatIsGoneFallsBackToTheDefault() {
        timeline.setJobs(details(done("a"), done("b")));
        timeline.setRowSelectionInterval(0, 0);

        timeline.setJobs(details(done("x"), failed("y")));

        assertEquals("y", selected().jobId());
    }

    public void testRowsShowTheNumberKindAndTable() {
        timeline.setJobs(details(merge("m")));

        String text = ((SimpleColoredComponent) timeline.prepareRenderer(timeline.getCellRenderer(0, 0), 0, 0))
                .getCharSequence(false).toString();

        assertTrue(text, text.contains("1"));
        assertTrue(text, text.contains("MERGE"));
        assertTrue(text, text.contains("orders_daily"));
    }

    public void testTheRowIconFollowsTheJobStatus() {
        timeline.setJobs(details(done("a"), failed("b")));

        SimpleColoredComponent row = (SimpleColoredComponent) timeline.prepareRenderer(timeline.getCellRenderer(1, 0), 1, 0);

        assertSame(DataformIcons.STATUS_FAILED, row.getIcon());
        assertSame(DataformIcons.STATUS_SUCCEEDED, ActionStateIcons.ofJob("DONE"));
        assertSame(DataformIcons.SPINNER, ActionStateIcons.ofJob("RUNNING"));
        assertSame(DataformIcons.STATUS_PENDING, ActionStateIcons.ofJob("PENDING"));
    }

    public void testTheBytesColumnUsesTheByteFormat() {
        timeline.setJobs(details(merge("m")));

        assertEquals(Utils.formatBytes(2_600_000_000L), timeline.getValueAt(0, 2));
    }

    public void testFittedHeightStopsAtEightRows() {
        timeline.setJobs(details(forty()));

        assertEquals(timeline.getTableHeader().getPreferredSize().height + 8 * timeline.getRowHeight(),
                timeline.fittedHeight(ChildJobsTimeline.FITTED_ROWS));
    }

    public void testFittedHeightFollowsAFewRows() {
        timeline.setJobs(details(done("a"), done("b"), done("c")));

        assertEquals(timeline.getTableHeader().getPreferredSize().height + 3 * timeline.getRowHeight(),
                timeline.fittedHeight(ChildJobsTimeline.FITTED_ROWS));
    }

    private BigQueryChildJob selected() {
        BigQueryChildJob job = timeline.selectedJob();
        assertNotNull(job);
        return job;
    }

    private BigQueryJobDetails details(BigQueryChildJob... children) {
        return new BigQueryJobDetails("script", "p", "EU", "DONE", null, null, null,
                START, START.plusMillis(21_400), children.length, List.of(children), null);
    }

    private BigQueryChildJob done(String id) {
        return child(id, "DONE");
    }

    private BigQueryChildJob failed(String id) {
        return child(id, "FAILED");
    }

    private BigQueryChildJob merge(String id) {
        Instant start = START.plusSeconds(clock.getAndIncrement());
        return new BigQueryChildJob(id, "DONE", start, start.plusSeconds(1), "MERGE t USING s ON true",
                2_600_000_000L, "MERGE", "p.d.orders_daily", null);
    }

    private BigQueryChildJob child(String id, String status) {
        Instant start = START.plusSeconds(clock.getAndIncrement());
        return new BigQueryChildJob(id, status, start, start.plusSeconds(1), "SELECT 1", null);
    }

    private BigQueryChildJob[] forty() {
        return IntStream.range(0, 40).mapToObj(i -> done("j" + i)).toArray(BigQueryChildJob[]::new);
    }
}
