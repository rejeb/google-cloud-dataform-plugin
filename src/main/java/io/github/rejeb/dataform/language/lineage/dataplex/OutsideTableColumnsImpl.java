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
package io.github.rejeb.dataform.language.lineage.dataplex;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.intellij.openapi.progress.ProcessCanceledException;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import io.github.rejeb.dataform.language.gcp.auth.AuthTrigger;
import io.github.rejeb.dataform.language.gcp.auth.GcpCalls;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.util.GcpClientsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Reads the schema of outside tables with BigQuery {@code tables.get}, all tables together. A
 * table that is denied, missing or fails for any other reason is reported as unreadable, so one
 * table never hides the columns of the others.
 */
public final class OutsideTableColumnsImpl implements OutsideTableColumns {

    private final TableSchemas schemas;
    private final ParallelCalls calls;

    public OutsideTableColumnsImpl() {
        this(OutsideTableColumnsImpl::read, ParallelCalls.shared());
    }

    OutsideTableColumnsImpl(@NotNull TableSchemas schemas) {
        this(schemas, ParallelCalls.direct());
    }

    OutsideTableColumnsImpl(@NotNull TableSchemas schemas, @NotNull ParallelCalls calls) {
        this.schemas = schemas;
        this.calls = calls;
    }

    @Override
    public @NotNull Result load(@NotNull String projectId, @NotNull Collection<String> fullNames) {
        Map<String, List<ColumnRef>> columns = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        Set<String> unreadable = new LinkedHashSet<>();
        List<String> asked = new ArrayList<>();
        List<Callable<Schema>> reads = new ArrayList<>();
        for (String fullName : new LinkedHashSet<>(fullNames)) {
            TableId table = tableOf(fullName);
            if (table == null) continue;
            asked.add(fullName);
            reads.add(() -> schemaOf(projectId, table));
        }
        List<Schema> read = calls.all(reads);
        for (int i = 0; i < asked.size(); i++) {
            String fullName = asked.get(i);
            Schema schema = read.get(i);
            if (schema == null) {
                unreadable.add(fullName);
                continue;
            }
            List<ColumnRef> found = new ArrayList<>();
            for (Field field : schema.getFields()) {
                found.add(new ColumnRef(fullName, field.getName()));
                types.put(LineageModel.columnTypeKey(fullName, field.getName()), typeOf(field));
            }
            columns.put(fullName, List.copyOf(found));
        }
        return new Result(columns, types, unreadable);
    }

    private @Nullable Schema schemaOf(@NotNull String projectId, @NotNull TableId table) {
        try {
            return schemas.of(projectId, table);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable TableId tableOf(@NotNull String fullName) {
        int tableDot = fullName.lastIndexOf('.');
        int datasetDot = tableDot <= 0 ? -1 : fullName.lastIndexOf('.', tableDot - 1);
        if (datasetDot <= 0 || tableDot == fullName.length() - 1 || tableDot == datasetDot + 1) return null;
        return TableId.of(fullName.substring(0, datasetDot), fullName.substring(datasetDot + 1, tableDot),
                fullName.substring(tableDot + 1));
    }

    private static @NotNull String typeOf(@NotNull Field field) {
        String type = field.getType().getStandardType().name();
        return field.getMode() == Field.Mode.REPEATED ? "ARRAY<" + type + ">" : type;
    }

    private static @Nullable Schema read(@NotNull String projectId, @NotNull TableId tableId) {
        Table table = GcpCalls.execute(AuthTrigger.BACKGROUND, () -> GcpClientsUtils.bigQuery(projectId).getTable(tableId));
        return table == null ? null : table.getDefinition().getSchema();
    }

    /**
     * Gives the schema of a table.
     */
    @FunctionalInterface
    interface TableSchemas {

        /**
         * @param projectId the project the request is billed to
         * @return the schema, or {@code null} when the table does not exist or has none
         */
        @Nullable Schema of(@NotNull String projectId, @NotNull TableId table);
    }
}
