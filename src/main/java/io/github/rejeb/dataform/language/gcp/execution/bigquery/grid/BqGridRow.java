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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.grid;

import com.google.cloud.bigquery.FieldValueList;
import com.intellij.database.datagrid.GridRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BqGridRow implements GridRow {

    private final int rowNum;
    private final Object[] values;

    public BqGridRow(int rowNum, @NotNull FieldValueList fieldValues, @NotNull List<StructFlattener.RowExtractor> extractors) {
        this.rowNum = rowNum;
        this.values = new Object[extractors.size()];
        for (int i = 0; i < extractors.size(); i++) {
            this.values[i] = extractors.get(i).extract(fieldValues);
        }
    }

    @Override
    public @Nullable Object getValue(int columnNum) {
        return columnNum >= 0 && columnNum < values.length ? values[columnNum] : null;
    }

    @Override
    public int getSize() {
        return values.length;
    }

    @Override
    public int getRowNum() {
        return rowNum;
    }

    @Override
    public void setValue(int i, @Nullable Object object) {
        if (i >= 0 && i < values.length) values[i] = object;
    }
}