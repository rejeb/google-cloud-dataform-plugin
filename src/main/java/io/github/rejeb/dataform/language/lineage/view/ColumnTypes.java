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
package io.github.rejeb.dataform.language.lineage.view;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The BigQuery types of the columns shown on the node cards, read from the table schemas the
 * plugin already knows.
 */
final class ColumnTypes {

    private ColumnTypes() {
    }

    /**
     * @return the type of every column of the known tables of the project, by
     * {@link LineageModel#columnTypeKey(String, String)}
     */
    static @NotNull Map<String, String> of(@NotNull Project project) {
        return of(DataformTableSchemaService.getInstance(project).getAllTables().values());
    }

    /**
     * @return the type of every column of the given tables, by
     * {@link LineageModel#columnTypeKey(String, String)}; a table whose full name is unknown gives none
     */
    static @NotNull Map<String, String> of(@NotNull Collection<DataformDasTable> tables) {
        Map<String, String> types = new HashMap<>();
        for (DataformDasTable table : tables) {
            String fullName = table.getFullName();
            if (fullName == null) continue;
            for (ColumnInfo column : table.getColumns()) {
                types.put(LineageModel.columnTypeKey(fullName, column.name()), column.type());
            }
        }
        return types;
    }
}
