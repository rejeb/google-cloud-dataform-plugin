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
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BqGridRowTest {

    private static final FieldList SCHEMA = FieldList.of(
            Field.of("id", StandardSQLTypeName.INT64),
            Field.of("name", StandardSQLTypeName.STRING));

    @Test
    public void valuesAreExtractedOnceAtConstruction() {
        BqGridRow row = new BqGridRow(4, row("1", "alice"), StructFlattener.buildExtractors(SCHEMA));
        assertEquals(4, row.getRowNum());
        assertEquals(2, row.getSize());
        assertEquals("1", row.getValue(0));
        assertEquals("alice", row.getValue(1));
    }

    @Test
    public void outOfRangeColumnsReadAsNullAndAreIgnoredOnWrite() {
        BqGridRow row = new BqGridRow(0, row("1", "alice"), StructFlattener.buildExtractors(SCHEMA));
        assertNull(row.getValue(-1));
        assertNull(row.getValue(2));
        row.setValue(5, "ignored");
        row.setValue(-1, "ignored");
        assertEquals("1", row.getValue(0));
    }

    @Test
    public void setValueOverridesAColumn() {
        BqGridRow row = new BqGridRow(0, row("1", "alice"), StructFlattener.buildExtractors(SCHEMA));
        row.setValue(1, "bob");
        assertEquals("bob", row.getValue(1));
    }

    private static FieldValueList row(String... values) {
        return FieldValueList.of(java.util.Arrays.stream(values)
                .map(v -> FieldValue.of(FieldValue.Attribute.PRIMITIVE, v))
                .toList(), SCHEMA);
    }
}
