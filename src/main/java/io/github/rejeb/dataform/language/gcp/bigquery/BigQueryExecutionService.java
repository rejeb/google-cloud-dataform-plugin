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
package io.github.rejeb.dataform.language.gcp.bigquery;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface BigQueryExecutionService {

    static BigQueryExecutionService getInstance(@NotNull Project project) {
        return project.getService(BigQueryExecutionService.class);
    }

    /**
     * Executes a SQL query against BigQuery synchronously.
     * Must be called off the EDT.
     *
     * @param sql       compiled SQL to execute
     * @param projectId GCP project ID
     * @param tableName Dataform table name (for display)
     * @return execution result, never null
     */
    @NotNull BigQueryJobResult execute(
            @NotNull String sql,
            @NotNull String projectId,
            @NotNull String tableName
    );
}
