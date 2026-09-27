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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.grid;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.openapi.util.Disposer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BqGridModelTest {

    private static final FieldList SCHEMA = FieldList.of(
            Field.of("id", StandardSQLTypeName.INT64),
            Field.of("name", StandardSQLTypeName.STRING));

    @Test
    public void exposesRowsAndColumns() {
        BqGridModel model = model(List.of(row(0, "1", "a"), row(1, "2", "b")));
        assertEquals(2, model.getColumnCount());
        assertEquals(2, model.getRowCount());
        assertEquals("name", model.getColumns().get(1).getName());
        assertEquals("b", model.getValueAt(ModelIndex.forRow(model, 1), ModelIndex.forColumn(model, 1)));
        assertEquals(2, model.getColumnsAsIterable().size());
        assertFalse(model.isUpdatingNow());
    }

    @Test
    public void indicesOutsideTheModelAreInvalid() {
        BqGridModel model = model(List.of(row(0, "1", "a")));
        assertTrue(model.isValidRowIdx(ModelIndex.forRow(model, 0)));
        assertFalse(model.isValidRowIdx(ModelIndex.forRow(model, 1)));
        assertFalse(model.isValidRowIdx(ModelIndex.forRow(model, -1)));
        assertTrue(model.isValidColumnIdx(ModelIndex.forColumn(model, 1)));
        assertFalse(model.isValidColumnIdx(ModelIndex.forColumn(model, 2)));
        assertNull(model.getRow(ModelIndex.forRow(model, 9)));
        assertNull(model.getColumn(ModelIndex.forColumn(model, 9)));
        assertNull(model.getValueAt(ModelIndex.forRow(model, 9), ModelIndex.forColumn(model, 0)));
    }

    @Test
    public void indexSetsCoverTheWholeModelAndSkipMissingEntries() {
        BqGridModel model = model(List.of(row(0, "1", "a"), row(1, "2", "b")));
        assertEquals(2, model.getRowIndices().size());
        assertEquals(2, model.getColumnIndices().size());
        assertEquals(1, model.getRows(ModelIndexSet.forRows(model, 1, 7)).size());
        assertEquals(1, model.getColumns(ModelIndexSet.forColumns(model, 0, 7)).size());
        assertEquals(1, model.getColumnsAsIterable(ModelIndexSet.forColumns(model, 1)).size());
    }

    @Test
    public void allValuesEqualToChecksEveryCell() {
        BqGridModel model = model(List.of(row(0, "1", "x"), row(1, "1", "x")));
        assertTrue(model.allValuesEqualTo(ModelIndexSet.forRows(model, 0, 1), ModelIndexSet.forColumns(model, 0), "1"));
        assertFalse(model.allValuesEqualTo(ModelIndexSet.forRows(model, 0, 1), ModelIndexSet.forColumns(model, 0, 1), "1"));
    }

    @Test
    public void replaceRowsNotifiesListeners() {
        BqGridModel model = model(new ArrayList<>(List.of(row(0, "1", "a"))));
        AtomicInteger rowsAdded = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        assertFalse(model.hasListeners());
        var disposable = Disposer.newDisposable();
        try {
            model.addListener(new GridModel.Listener<>() {
                @Override
                public void columnsAdded(ModelIndexSet<GridColumn> columns) {
                }

                @Override
                public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
                }

                @Override
                public void rowsAdded(ModelIndexSet<GridRow> rows) {
                    rowsAdded.addAndGet(rows.size());
                }

                @Override
                public void rowsRemoved(ModelIndexSet<GridRow> rows) {
                }

                @Override
                public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns,
                                         GridRequestSource.RequestPlace place) {
                }

                @Override
                public void afterLastRowAdded() {
                    finished.incrementAndGet();
                }
            }, disposable);
            assertTrue(model.hasListeners());

            model.replaceRows(List.of(row(0, "5", "e"), row(1, "6", "f"), row(2, "7", "g")));
            assertEquals(3, model.getRowCount());
            assertEquals("7", model.getRow(ModelIndex.forRow(model, 2)).getValue(0));
            assertEquals(3, rowsAdded.get());
            assertEquals(1, finished.get());

            model.replaceRows(List.of());
            assertEquals(0, model.getRowCount());
            assertEquals(3, rowsAdded.get());
            assertEquals(2, finished.get());
        } finally {
            Disposer.dispose(disposable);
        }
    }

    private static BqGridModel model(List<BqGridRow> rows) {
        List<BqGridColumn> columns = new ArrayList<>();
        List<StructFlattener.FlatField> flat = StructFlattener.flattenFields(SCHEMA, "");
        for (int i = 0; i < flat.size(); i++) {
            columns.add(new BqGridColumn(i, flat.get(i).qualifiedName(), flat.get(i).field()));
        }
        return new BqGridModel(columns, rows);
    }

    private static BqGridRow row(int rowNum, String... values) {
        FieldValueList list = FieldValueList.of(java.util.Arrays.stream(values)
                .map(v -> FieldValue.of(FieldValue.Attribute.PRIMITIVE, v))
                .toList(), SCHEMA);
        return new BqGridRow(rowNum, list, StructFlattener.buildExtractors(SCHEMA));
    }
}
