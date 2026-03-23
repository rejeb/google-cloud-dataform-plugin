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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasColumn;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DataformDasTable implements DasTable {
    private final DataformDasSchema myParent;
    private final String myName;
    private final List<ColumnInfo> myColumns;
    public DataformDasTable(DataformDasSchema myParent,
                            String table, List<ColumnInfo> myColumns) {
        this.myColumns = myColumns;
        this.myParent = myParent;
        this.myName = table;
    }

    @Override
    public @NotNull String getName() {
        return myName;
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.TABLE;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public boolean isSystem() {
        return false;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public @NotNull Set<DasColumn.Attribute> getColumnAttrs(@Nullable DasColumn columnInfo) {
        return Set.of();
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.COLUMN) {
            return JBIterable.from(myColumns)
                    .map(col -> new DataformDasColumn(null, col));
        }
        return JBIterable.empty();
    }
}
