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
package io.github.rejeb.dataform.language.gcp.execution.unittest.bigquery;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestRows;
import io.github.rejeb.dataform.language.gcp.execution.unittest.engine.UnitTestValues;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BigQueryRowValuesTest {

    private static FieldValue primitive(String value) {
        return FieldValue.of(FieldValue.Attribute.PRIMITIVE, value);
    }

    @Test
    void convertsScalarsToCanonicalValues() {
        Schema schema = Schema.of(
                Field.of("id", StandardSQLTypeName.INT64),
                Field.of("amount", StandardSQLTypeName.FLOAT64),
                Field.of("ok", StandardSQLTypeName.BOOL),
                Field.of("name", StandardSQLTypeName.STRING),
                Field.of("missing", StandardSQLTypeName.STRING));
        FieldValueList row = FieldValueList.of(List.of(
                primitive("1"), primitive("1.0"), primitive("true"), primitive("a"),
                FieldValue.of(FieldValue.Attribute.PRIMITIVE, null)), schema.getFields());

        UnitTestRows rows = BigQueryRowValues.toRows(schema, List.of(row));

        assertEquals(List.of("id", "amount", "ok", "name", "missing"), rows.columns());
        List<Object> values = rows.rows().getFirst();
        assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) values.get(0)));
        assertEquals("1", UnitTestValues.render(values.get(1)));
        assertEquals(Boolean.TRUE, values.get(2));
        assertEquals("a", values.get(3));
        assertNull(values.get(4));
    }

    @Test
    void convertsRecordsAndRepeatedValues() {
        Field tags = Field.newBuilder("tags", StandardSQLTypeName.STRING).setMode(Field.Mode.REPEATED).build();
        Field address = Field.of("address", LegacySQLTypeName.RECORD,
                Field.of("city", StandardSQLTypeName.STRING), Field.of("zip", StandardSQLTypeName.INT64));
        Schema schema = Schema.of(tags, address);
        FieldValue repeated = FieldValue.of(FieldValue.Attribute.REPEATED, List.of(primitive("a"), primitive("b")));
        FieldValue record = FieldValue.of(FieldValue.Attribute.RECORD,
                FieldValueList.of(List.of(primitive("Paris"), primitive("75001")), address.getSubFields()));

        UnitTestRows rows = BigQueryRowValues.toRows(schema,
                List.of(FieldValueList.of(List.of(repeated, record), FieldList.of(tags, address))));

        assertEquals("[\"a\",\"b\"]", UnitTestValues.render(rows.rows().getFirst().get(0)));
        assertEquals("{\"city\":\"Paris\",\"zip\":75001}", UnitTestValues.render(rows.rows().getFirst().get(1)));
    }
}
