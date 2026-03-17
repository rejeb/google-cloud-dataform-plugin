/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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
package io.github.rejeb.dataform.language.gcp.execution.grid;

import com.intellij.database.datagrid.*;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BqGridModel implements GridModel<GridRow, GridColumn> {

    private final List<BqGridColumn> columns;
    private final List<BqGridRow> rows;
    private final EventDispatcher<Listener<GridRow, GridColumn>> dispatcher =
            EventDispatcher.create((Class<Listener<GridRow, GridColumn>>) (Class<?>) Listener.class);

    public BqGridModel(@NotNull List<BqGridColumn> columns, @NotNull List<BqGridRow> rows) {
        this.columns = columns;
        this.rows = rows;
    }


    @Override
    public boolean isValidRowIdx(@NotNull ModelIndex<GridRow> idx) {
        int i = idx.asInteger();
        return i >= 0 && i < rows.size();
    }

    @Override
    public boolean isValidColumnIdx(@NotNull ModelIndex<GridColumn> idx) {
        int i = idx.asInteger();
        return i >= 0 && i < columns.size();
    }

    @Override
    public @Nullable Object getValueAt(ModelIndex<GridRow> row, ModelIndex<GridColumn> col) {
        GridRow r = getRow(row);
        return r != null ? r.getValue(col.asInteger()) : null;
    }

    @Override
    public boolean allValuesEqualTo(@NotNull ModelIndexSet<GridRow> rowIndices,
                                    @NotNull ModelIndexSet<GridColumn> colIndices,
                                    @Nullable Object what) {
        for (ModelIndex<GridRow> r : rowIndices.asIterable()) {
            for (ModelIndex<GridColumn> c : colIndices.asIterable()) {
                if (!java.util.Objects.equals(getValueAt(r, c), what)) return false;
            }
        }
        return true;
    }

    @Override
    public @Nullable GridRow getRow(@NotNull ModelIndex<GridRow> idx) {
        int i = idx.asInteger();
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    @Override
    public @Nullable GridColumn getColumn(@NotNull ModelIndex<GridColumn> idx) {
        int i = idx.asInteger();
        return i >= 0 && i < columns.size() ? columns.get(i) : null;
    }

    @Override
    public @NotNull List<GridRow> getRows(@NotNull ModelIndexSet<GridRow> indices) {
        List<GridRow> result = new ArrayList<>();
        for (ModelIndex<GridRow> idx : indices.asIterable()) {
            GridRow r = getRow(idx);
            if (r != null) result.add(r);
        }
        return result;
    }

    @Override
    public @NotNull List<GridColumn> getColumns(@NotNull ModelIndexSet<GridColumn> indices) {
        List<GridColumn> result = new ArrayList<>();
        for (ModelIndex<GridColumn> idx : indices.asIterable()) {
            GridColumn c = getColumn(idx);
            if (c != null) result.add(c);
        }
        return result;
    }

    @Override
    public @NotNull List<GridRow> getRows() {
        return new ArrayList<>(rows);
    }

    @Override
    public @NotNull List<GridColumn> getColumns() {
        return new ArrayList<>(columns);
    }

    @Override
    public @NotNull JBIterable<GridColumn> getColumnsAsIterable() {
        return JBIterable.from(columns);
    }

    @Override
    public @NotNull JBIterable<GridColumn> getColumnsAsIterable(@NotNull ModelIndexSet<GridColumn> indices) {
        return JBIterable.from(getColumns(indices));
    }

    @Override
    public @NotNull ModelIndexSet<GridColumn> getColumnIndices() {
        int[] idx = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) idx[i] = i;
        return ModelIndexSet.forColumns(this, idx);
    }

    @Override
    public @NotNull ModelIndexSet<GridRow> getRowIndices() {
        int[] idx = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) idx[i] = i;
        return ModelIndexSet.forRows(this, idx);
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public boolean isUpdatingNow() {
        return false;
    }

    @Override
    public boolean hasListeners() {
        return dispatcher.hasListeners();
    }

    @Override
    public void addListener(@NotNull Listener<GridRow, GridColumn> l, @NotNull Disposable d) {
        dispatcher.addListener(l, d);
    }

    /**
     * Replaces all rows with the given list and notifies listeners.
     */
    public void replaceRows(@NotNull List<BqGridRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        Listener<GridRow, GridColumn> multicaster = dispatcher.getMulticaster();
        multicaster.columnsAdded(getColumnIndices());
        ModelIndexSet<GridRow> rowIndices = getRowIndices();
        if (rowIndices.size() > 0) {
            multicaster.rowsAdded(rowIndices);
        }
        multicaster.afterLastRowAdded();
    }

}
