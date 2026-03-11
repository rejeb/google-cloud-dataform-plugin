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
package io.github.rejeb.dataform.language.schema.sql.model;

import com.intellij.database.model.DasNamespace;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataformDasSchema implements DasNamespace {
    private final DataformDasCatalog myParent;
    private final String myDataset;
    private final List<DataformDasTable> myTables;

    public DataformDasSchema(DataformDasCatalog parent, String dataset,
                             List<Map.Entry<String, List<ColumnInfo>>> tables) {
        this.myParent = parent;
        this.myDataset = dataset;
        this.myTables = new ArrayList<>();
        for (Map.Entry<String, List<ColumnInfo>> e : tables) {
            String[] parts = e.getKey().split("\\.", 3);
            myTables.add(new DataformDasTable(this, parts[2], e.getValue()));
        }
    }

    @Override
    public @NotNull String getName() {
        return myDataset;
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.SCHEMA;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public @NotNull JBIterable<? extends DasObject> getDasChildren(@Nullable ObjectKind kind) {
        if (kind == ObjectKind.TABLE) {
            return JBIterable.from(myTables);
        }
        return JBIterable.empty();
    }
}

