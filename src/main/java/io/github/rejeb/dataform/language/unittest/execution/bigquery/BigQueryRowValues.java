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
package io.github.rejeb.dataform.language.unittest.execution.bigquery;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import io.github.rejeb.dataform.language.unittest.execution.engine.UnitTestRows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BigQueryRowValues {

    private static final Set<StandardSQLTypeName> NUMBERS = Set.of(
            StandardSQLTypeName.INT64, StandardSQLTypeName.FLOAT64,
            StandardSQLTypeName.NUMERIC, StandardSQLTypeName.BIGNUMERIC);

    private BigQueryRowValues() {
    }

    /**
     * Converts BigQuery rows to canonical values: numbers as {@code BigDecimal}, booleans, strings,
     * arrays as lists and records as ordered maps.
     */
    @NotNull
    public static UnitTestRows toRows(@NotNull Schema schema, @NotNull List<FieldValueList> rows) {
        FieldList fields = schema.getFields();
        List<String> columns = new ArrayList<>();
        fields.forEach(field -> columns.add(field.getName()));
        List<List<Object>> values = new ArrayList<>();
        for (FieldValueList row : rows) {
            List<Object> converted = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                converted.add(canonical(row.get(i), fields.get(i)));
            }
            values.add(converted);
        }
        return new UnitTestRows(columns, values);
    }

    @Nullable
    private static Object canonical(@Nullable FieldValue value, @NotNull Field field) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.getAttribute() == FieldValue.Attribute.REPEATED) {
            List<Object> items = new ArrayList<>();
            for (FieldValue item : value.getRepeatedValue()) {
                items.add(element(item, field));
            }
            return items;
        }
        return element(value, field);
    }

    @Nullable
    private static Object element(@Nullable FieldValue value, @NotNull Field field) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.getAttribute() == FieldValue.Attribute.RECORD) {
            FieldList subFields = field.getSubFields();
            FieldValueList values = value.getRecordValue();
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < subFields.size(); i++) {
                record.put(subFields.get(i).getName(), canonical(values.get(i), subFields.get(i)));
            }
            return record;
        }
        StandardSQLTypeName type = field.getType().getStandardType();
        if (type == StandardSQLTypeName.TIMESTAMP) {
            return value.getTimestampInstant().toString();
        }
        if (type == StandardSQLTypeName.BOOL) {
            return value.getBooleanValue();
        }
        if (NUMBERS.contains(type)) {
            try {
                return new BigDecimal(value.getStringValue());
            } catch (NumberFormatException e) {
                return value.getStringValue();
            }
        }
        return value.getStringValue();
    }
}
