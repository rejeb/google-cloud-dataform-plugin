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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import io.github.rejeb.dataform.language.lineage.dataplex.EntityNames;
import io.github.rejeb.dataform.language.lineage.dataplex.ProcessRun;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedProcess;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunsTableModelTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final RecordedProcess WORKFLOW = new RecordedProcess("processes/1", "Dataform workflow", "BIGQUERY", "job_1");

    private static ProcessRun run(RecordedRun.State state, Instant start, Instant end, RecordedProcess process, String... sources) {
        return new ProcessRun(new RecordedRun(process.name() + "/runs/1", state, start, end), process,
                List.of(sources).stream().map(EntityNames::fqnOf).toList(), EntityNames.fqnOf("p.d.orders_enriched"));
    }

    private static RunsTableModel model(ProcessRun... runs) {
        RunsTableModel model = new RunsTableModel(Clock.fixed(NOW, ZoneOffset.UTC));
        model.setRows(List.of(runs).stream().map(RunRow::of).toList());
        return model;
    }

    @Test
    void columnsAreStateStartedDurationProcessDataMovedJob() {
        RunsTableModel model = model();

        assertEquals(List.of("", "Started", "Duration", "Process", "Data moved", "BigQuery job"),
                IntStream.range(0, model.getColumnCount()).mapToObj(model::getColumnName).toList());
    }

    @Test
    void aRowShowsWhenItStartedHowLongItTookAndWhoRanIt() {
        RunsTableModel model = model(run(RecordedRun.State.COMPLETED, NOW.minusSeconds(3_600),
                NOW.minusSeconds(3_588), WORKFLOW, "p.d.stg_orders"));

        assertEquals(1, model.getRowCount());
        assertEquals(RecordedRun.State.COMPLETED, model.getValueAt(0, RunsTableModel.STATE));
        assertEquals("Today 09:00", model.getValueAt(0, RunsTableModel.STARTED));
        assertEquals("12s", model.getValueAt(0, RunsTableModel.DURATION));
        assertEquals("Dataform workflow", model.getValueAt(0, RunsTableModel.PROCESS));
        assertEquals("Open latest job", model.getValueAt(0, RunsTableModel.JOB));
        assertEquals("job_1", model.row(0).jobId());
    }

    @Test
    void aRunningRunHasNoDuration() {
        RunsTableModel model = model(run(RecordedRun.State.STARTED, NOW.minusSeconds(60), null, WORKFLOW, "p.d.stg_orders"));

        assertNull(model.row(0).duration());
        assertEquals("", model.getValueAt(0, RunsTableModel.DURATION));
    }

    @Test
    void dataMovedListsSourcesThenTheTarget() {
        RunRow row = RunRow.of(run(RecordedRun.State.COMPLETED, NOW.minusSeconds(60), NOW, WORKFLOW,
                "p.d.stg_orders", "p.d.stg_customers"));

        assertEquals("stg_orders, stg_customers → orders_enriched", row.moved());
        assertEquals(Duration.ofSeconds(60), row.duration());
    }

    @Test
    void aProcessWithoutAJobHasNoLink() {
        RunsTableModel model = model(run(RecordedRun.State.FAILED, NOW.minusSeconds(60), NOW,
                new RecordedProcess("processes/2", "Query job", "BIGQUERY", null), "p.d.stg_orders"));

        assertEquals("", model.getValueAt(0, RunsTableModel.JOB));
    }
}
