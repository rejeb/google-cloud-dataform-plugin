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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.lineage.column.ColumnRef;
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasColumn;
import io.github.rejeb.dataform.language.schema.sql.model.StructColumnPath;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StructColumnPaths {

    private StructColumnPaths() {
    }

    /**
     * Returns the path of a column named by its dotted name, {@code address.city} for the field
     * {@code city} of the column {@code address}: its root column and the fields walked from it.
     * Empty when the root column or one of the fields is not in the schema.
     */
    @NotNull
    public static Optional<StructColumnPath> of(@NotNull Project project, @NotNull ColumnRef column) {
        String[] segments = column.columnName().split("\\.");
        DataformDasColumn root = ColumnOriginService.getInstance(project)
                .dasColumn(new ColumnRef(column.tableFullName(), segments[0]));
        if (root == null) {
            return Optional.empty();
        }
        List<ColumnInfo> trail = new ArrayList<>();
        List<ColumnInfo> fields = root.getColumnInfo().subFields();
        for (int i = 1; i < segments.length; i++) {
            Optional<ColumnInfo> field = DataformActionColumns.find(fields, segments[i]);
            if (field.isEmpty()) {
                return Optional.empty();
            }
            trail.add(field.get());
            fields = field.get().subFields();
        }
        return Optional.of(new StructColumnPath(root, trail));
    }
}
