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
import com.intellij.database.model.properties.PropertyConverter;
import com.intellij.database.types.DasType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataformDasColumn implements DasColumn {
    private final DataformDasTable myParent;
    private final ColumnInfo myInfo;

    public DataformDasColumn(DataformDasTable parent, ColumnInfo info) {
        this.myParent = parent;
        this.myInfo = info;
    }

    @Override
    public @NotNull String getName() {
        return myInfo.name();
    }

    @Override
    public @Nullable DasObject getDasParent() {
        return myParent;
    }

    @Override
    public @NotNull ObjectKind getKind() {
        return ObjectKind.COLUMN;
    }

    @Override
    public boolean isQuoted() {
        return false;
    }

    @Override
    public boolean isNotNull() {
        return "REQUIRED".equals(myInfo.mode());
    }

    @Override
    public @Nullable String getDefault() {
        return myInfo.name();
    }

    @Override
    public @NotNull DasType getDasType() {
        return PropertyConverter.importDasType(myInfo.type());
    }

    @Override
    public short getPosition() {
        return 0;
    }

    @Override
    public @Nullable DasTable getTable() {
        return null;
    }
}

