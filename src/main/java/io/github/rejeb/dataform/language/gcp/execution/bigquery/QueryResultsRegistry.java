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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-level service storing the latest BigQuery result per table.
 * Acts as the source of truth for the Services view nodes.
 */
@Service(Service.Level.PROJECT)
public final class QueryResultsRegistry {

    private final Map<String, BigQueryJobResult> results = new LinkedHashMap<>();

    public static QueryResultsRegistry getInstance(@NotNull Project project) {
        return project.getService(QueryResultsRegistry.class);
    }

    /** Upsert: replaces any existing result for the same tableName. */
    public synchronized void put(@NotNull BigQueryJobResult result) {
        results.put(result.tableName(), result);
    }

    public synchronized @NotNull List<BigQueryJobResult> getAll() {
        return new ArrayList<>(results.values());
    }

    public synchronized @Nullable BigQueryJobResult get(@NotNull String tableName) {
        return results.get(tableName);
    }

    public synchronized boolean isEmpty() {
        return results.isEmpty();
    }

    public synchronized void remove(@NotNull String tableName) {
        BigQueryJobResult removed = results.remove(tableName);
        if (removed != null) {
            removed.pagedResult().dispose();
        }
    }
}
