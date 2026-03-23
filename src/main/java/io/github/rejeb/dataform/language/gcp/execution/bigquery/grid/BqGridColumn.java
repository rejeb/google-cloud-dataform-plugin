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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.grid;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.intellij.database.datagrid.GridColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.Collections;
import java.util.Set;

public class BqGridColumn implements GridColumn {

    private final int index;
    private final String name;
    private final int sqlType;
    private final String typeName;

    public BqGridColumn(int index, @NotNull String qualifiedName, @NotNull Field field) {
        this.index = index;
        this.name = qualifiedName;
        StandardSQLTypeName bqType = field.getType().getStandardType();
        this.sqlType = toSqlType(bqType);
        this.typeName = bqType.name();
    }

    private static int toSqlType(StandardSQLTypeName bqType) {
        return switch (bqType) {
            case INT64 -> Types.BIGINT;
            case FLOAT64 -> Types.DOUBLE;
            case BOOL -> Types.BOOLEAN;
            case DATE -> Types.DATE;
            case DATETIME, TIMESTAMP -> Types.TIMESTAMP;
            case NUMERIC, BIGNUMERIC -> Types.NUMERIC;
            case BYTES -> Types.BINARY;
            case STRUCT -> Types.STRUCT;
            case ARRAY -> Types.ARRAY;
            default -> Types.VARCHAR;
        };
    }

    @Override
    public int getColumnNumber() {
        return index;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getType() {
        return sqlType;
    }

    @Override
    public @Nullable String getTypeName() {
        return typeName;
    }

    @Override
    public @NotNull Set<Attribute> getAttributes() {
        return Collections.emptySet();
    }
}