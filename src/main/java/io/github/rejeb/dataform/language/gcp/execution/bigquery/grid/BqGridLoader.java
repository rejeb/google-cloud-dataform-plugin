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

import com.google.cloud.bigquery.FieldValueList;
import com.intellij.database.datagrid.GridLoader;
import com.intellij.database.datagrid.GridRequestSource;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryPagedResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BqGridLoader implements GridLoader {

    private final BigQueryPagedResult pagedResult;
    private final BqGridModel model;
    private BqDataHookUp hookUp;
    private final List<StructFlattener.RowExtractor> extractors;

    public BqGridLoader(@NotNull BigQueryPagedResult pagedResult, @NotNull BqGridModel model,
                        @NotNull List<StructFlattener.RowExtractor> extractors) {
        this.pagedResult = pagedResult;
        this.model       = model;
        this.extractors  = extractors;
    }


    public void setHookUp(@NotNull BqDataHookUp hookUp) {
        this.hookUp = hookUp;
    }

    @Override
    public void loadFirstPage(@NotNull GridRequestSource source) {
        applyRows(pagedResult.loadFirstPage(), source);
    }

    @Override
    public void reloadCurrentPage(@NotNull GridRequestSource source) {
        applyRows(pagedResult.loadFirstPage(), source);
    }

    @Override
    public void loadNextPage(@NotNull GridRequestSource source) {
        applyRows(pagedResult.loadNextPage(), source);
    }

    @Override
    public void loadPreviousPage(@NotNull GridRequestSource source) {
        applyRows(pagedResult.loadPreviousPage(), source);
    }

    @Override
    public void loadLastPage(@NotNull GridRequestSource source) {
        applyRows(pagedResult.loadPageAtOffset(pagedResult.getTotalRows() - 1), source);
    }

    @Override
    public void load(@NotNull GridRequestSource source, int offset) {
        applyRows(pagedResult.loadPageAtOffset(offset), source);
    }

    @Override
    public void updateTotalRowCount(@NotNull GridRequestSource source) {
    }

    @Override
    public void applyFilterAndSorting(@NotNull GridRequestSource source) {
    }

    @Override
    public void updateIsTotalRowCountUpdateable() {
    }

    private void applyRows(@NotNull List<FieldValueList> rows, @NotNull GridRequestSource source) {
        List<BqGridRow> gridRows = new ArrayList<>();
        long offset = pagedResult.getPageStart();
        for (int i = 0; i < rows.size(); i++) {
            gridRows.add(new BqGridRow((int) (offset + i + 1), rows.get(i), extractors));
        }
        model.replaceRows(gridRows);
        source.getActionCallback().setDone();
        if (hookUp != null) hookUp.firePagingChanged();
    }
}

