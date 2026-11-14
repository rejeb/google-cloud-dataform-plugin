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

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.columns.model.ColumnRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the columns of the tables the recorded lineage shows and the repository does not define,
 * from their BigQuery schema. Top-level columns only.
 */
public interface OutsideTableColumns {

    static @NotNull OutsideTableColumns getInstance(@NotNull Project project) {
        return project.getService(OutsideTableColumns.class);
    }

    /**
     * Reads the schema of each table. It blocks on the network and must not be called on the
     * event dispatch thread.
     *
     * @param projectId the project the requests are billed to
     * @param fullNames the tables, as {@code project.dataset.table}; any other name is left out
     * @return the columns of the tables that could be read, and the tables that could not
     */
    @NotNull Result load(@NotNull String projectId, @NotNull Collection<String> fullNames);

    /**
     * @param columns    the top-level columns of each readable table, by its full name, in schema order
     * @param types      the BigQuery type of those columns, by
     *                   {@link io.github.rejeb.dataform.language.lineage.model.LineageModel#columnTypeKey}
     * @param unreadable the full names of the tables whose schema could not be read
     */
    record Result(@NotNull Map<String, List<ColumnRef>> columns, @NotNull Map<String, String> types,
                  @NotNull Set<String> unreadable) {

        public static final Result NONE = new Result(Map.of(), Map.of(), Set.of());

        /**
         * @return this result with what the other one read added; a table the other one read
         * replaces what was known of it
         */
        public @NotNull Result with(@NotNull Result other) {
            Map<String, List<ColumnRef>> allColumns = new LinkedHashMap<>(columns);
            Map<String, String> allTypes = new LinkedHashMap<>(types);
            Set<String> allUnreadable = new LinkedHashSet<>(unreadable);
            allUnreadable.removeAll(other.columns.keySet());
            other.unreadable.forEach(allColumns::remove);
            allColumns.putAll(other.columns);
            allTypes.putAll(other.types);
            allUnreadable.addAll(other.unreadable);
            return new Result(allColumns, allTypes, allUnreadable);
        }
    }
}
