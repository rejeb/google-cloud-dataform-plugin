/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import com.google.cloud.bigquery.Field;
import com.intellij.database.datagrid.*;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import io.github.rejeb.dataform.language.gcp.bigquery.BigQueryPagedResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BqDataHookUp implements GridDataHookUp<GridRow, GridColumn> {

    private final Project project;
    private final BqGridModel model;
    private final BqGridPagingModel pagingModel;
    private final BqGridLoader loader;
    private final EventDispatcher<RequestListener<GridRow, GridColumn>> requestDispatcher =
            EventDispatcher.create((Class<RequestListener<GridRow, GridColumn>>) (Class<?>) RequestListener.class);

    public BqDataHookUp(@NotNull Project project, @NotNull BigQueryPagedResult pagedResult) {
        this.project = project;

        List<BqGridColumn> columns = new ArrayList<>();
        List<Field> fields = pagedResult.getSchema().getFields();
        for (int i = 0; i < fields.size(); i++) {
            columns.add(new BqGridColumn(i, fields.get(i)));
        }

        this.model = new BqGridModel(columns, new ArrayList<>());
        this.pagingModel = new BqGridPagingModel(pagedResult, this.model);
        this.loader = new BqGridLoader(pagedResult, this.model);
        this.loader.setHookUp(this);
    }

    @Override
    public @NotNull GridModel<GridRow, GridColumn> getDataModel() {
        return model;
    }

    @Override
    public @NotNull GridModel<GridRow, GridColumn> getMutationModel() {
        return model;
    }

    @Override
    public @NotNull Project getProject() {
        return project;
    }

    @Override
    public @NotNull GridPagingModel<GridRow, GridColumn> getPageModel() {
        return pagingModel;
    }

    @Override
    public @Nullable GridMutator<GridRow, GridColumn> getMutator() {
        return null;
    }

    @Override
    public @Nullable GridSortingModel<GridRow, GridColumn> getSortingModel() {
        return null;
    }

    @Override
    public @Nullable GridFilteringModel getFilteringModel() {
        return null;
    }

    @Override
    public @NotNull GridLoader getLoader() {
        return loader;
    }

    @Override
    public int getBusyCount() {
        return 0;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isFilterApplicable() {
        return false;
    }

    @Override
    public @NotNull String getFilterPrefix() {
        return "";
    }

    @Override
    public @NotNull String getFilterEmptyText() {
        return "Filter...";
    }

    @Override
    public @NotNull String getSortingPrefix() {
        return "";
    }

    @Override
    public @NotNull String getSortingEmptyText() {
        return "";
    }

    @Override
    public @NotNull Language getFilterSortLanguage() {
        return Language.ANY;
    }

    @Override
    public void updateFilterSortFully() {
    }

    @Override
    public void addRequestListener(@NotNull RequestListener<GridRow, GridColumn> l, @NotNull Disposable d) {
        requestDispatcher.addListener(l, d);
    }

    /**
     * Notifies all RequestListeners that a page has been loaded.
     * Must be called after reloadCurrentPage completes.
     */
    public void firePagingChanged() {
        pagingModel.firePageChanged();
    }
}
