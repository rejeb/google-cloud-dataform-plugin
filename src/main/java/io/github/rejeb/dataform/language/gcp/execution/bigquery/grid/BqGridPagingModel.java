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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.grid;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.MultiPageModel;
import com.intellij.util.EventDispatcher;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryPagedResult;
import org.jetbrains.annotations.NotNull;

public class BqGridPagingModel implements MultiPageModel<GridRow, GridColumn> {

    private final BigQueryPagedResult pagedResult;
    private final BqGridModel model;
    private final EventDispatcher<PageModelListener> pageModelListeners =
            EventDispatcher.create(PageModelListener.class);

    public BqGridPagingModel(@NotNull BigQueryPagedResult pagedResult, @NotNull BqGridModel model) {
        this.pagedResult = pagedResult;
        this.model = model;
    }

    @Override
    public void addPageModelListener(@NotNull PageModelListener listener) {
        pageModelListeners.addListener(listener);
    }

    @Override
    public void setPageStart(int pageStart) {
        pageModelListeners.getMulticaster().pageStartChanged();
    }

    @Override
    public void setPageEnd(int pageEnd) {
    }

    @Override
    public void setTotalRowCount(long totalRowCount, boolean precise) {
    }

    @Override
    public void setTotalRowCountUpdateable(boolean updateable) {
    }

    @Override
    public void setPageSize(int pageSize) {
        pagedResult.setPageSize(pageSize);
        pageModelListeners.getMulticaster().pageSizeChanged();
    }

    @Override
    public boolean isFirstPage() {
        return pagedResult.isFirstPage();
    }

    @Override
    public boolean isLastPage() {
        return pagedResult.isLastPage();
    }

    @Override
    public int getPageSize() {
        return pagedResult.getPageSize();
    }

    @Override
    public long getTotalRowCount() {
        return pagedResult.getTotalRows();
    }

    @Override
    public boolean isTotalRowCountPrecise() {
        return true;
    }

    @Override
    public boolean isTotalRowCountUpdateable() {
        return false;
    }

    @Override
    public int getPageStart() {
        return (int) pagedResult.getPageStart();
    }

    @Override
    public int getPageEnd() {
        return (int) pagedResult.getPageEnd();
    }

    @Override
    public boolean pageSizeSet() {
        return true;
    }

    @Override
    public @NotNull ModelIndex<GridRow> findRow(int rowDataIdx) {
        return ModelIndex.forRow(model, rowDataIdx);
    }

    /**
     * Notifie TableResultPanel que la page a changé → met à jour les boutons Prev/Next.
     */
    public void firePageChanged() {
        pageModelListeners.getMulticaster().pageStartChanged();
    }
}
