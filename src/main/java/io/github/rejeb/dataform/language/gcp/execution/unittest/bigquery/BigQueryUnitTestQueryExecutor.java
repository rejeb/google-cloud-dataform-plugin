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
package io.github.rejeb.dataform.language.gcp.execution.unittest.bigquery;

import com.google.cloud.bigquery.FieldValueList;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryExecutionService;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryPagedResult;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestQueryException;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestQueryExecutor;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestRows;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class BigQueryUnitTestQueryExecutor implements UnitTestQueryExecutor {

    static final int MAX_ROWS = 10_000;
    private static final String JOB_LABEL = "Dataform unit test";

    private final Project project;
    private final String projectId;

    public BigQueryUnitTestQueryExecutor(@NotNull Project project, @NotNull String projectId) {
        this.project = project;
        this.projectId = projectId;
    }

    @Override
    public @NotNull UnitTestRows execute(@NotNull String sql, @NotNull ProgressIndicator indicator)
            throws UnitTestQueryException {
        BigQueryJobResult result = BigQueryExecutionService.getInstance(project)
                .execute(sql, projectId, JOB_LABEL, indicator);
        if (!result.isSuccess()) {
            throw new UnitTestQueryException(result.errorMessage());
        }
        BigQueryPagedResult paged = result.pagedResult();
        if (paged == null) {
            return new UnitTestRows(List.of(), List.of());
        }
        try {
            if (paged.getTotalRows() > MAX_ROWS) {
                throw new UnitTestQueryException("The query returned " + paged.getTotalRows()
                        + " rows, more than the " + MAX_ROWS + " a unit test compares");
            }
            List<FieldValueList> rows = new ArrayList<>(paged.loadFirstPage());
            while (!paged.isLastPage()) {
                indicator.checkCanceled();
                rows.addAll(paged.loadNextPage());
            }
            return BigQueryRowValues.toRows(paged.getSchema(), rows);
        } finally {
            paged.dispose();
        }
    }
}
