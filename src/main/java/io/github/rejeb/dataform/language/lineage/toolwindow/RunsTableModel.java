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

import io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui.RunConfigUiUtils;
import io.github.rejeb.dataform.language.lineage.dataplex.RecordedRun;
import io.github.rejeb.dataform.language.lineage.view.FreshnessText;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.time.Clock;
import java.util.List;

/**
 * The rows of the Runs tab: state, start, duration, process, data moved and the link to the latest
 * BigQuery job.
 */
public final class RunsTableModel extends AbstractTableModel {

    public static final int STATE = 0;
    public static final int STARTED = 1;
    public static final int DURATION = 2;
    public static final int PROCESS = 3;
    public static final int MOVED = 4;
    public static final int JOB = 5;
    public static final String OPEN_JOB = "Open latest job";

    private static final List<String> COLUMNS = List.of("", "Started", "Duration", "Process", "Data moved", "BigQuery job");

    private final Clock clock;
    private List<RunRow> rows = List.of();

    /**
     * @param clock the current time and the zone the start times are shown in
     */
    public RunsTableModel(@NotNull Clock clock) {
        this.clock = clock;
    }

    /**
     * Replaces the rows.
     */
    public void setRows(@NotNull List<RunRow> newRows) {
        rows = List.copyOf(newRows);
        fireTableDataChanged();
    }

    /**
     * @return the run of a row
     */
    public @NotNull RunRow row(int index) {
        return rows.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.size();
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == STATE ? RecordedRun.State.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int column) {
        RunRow row = rows.get(rowIndex);
        return switch (column) {
            case STATE -> row.state();
            case STARTED -> FreshnessText.when(row.started(), clock);
            case DURATION -> row.duration() == null ? "" : RunConfigUiUtils.formatDuration(row.duration());
            case PROCESS -> row.process();
            case MOVED -> row.moved();
            default -> row.jobId() == null ? "" : OPEN_JOB;
        };
    }
}
