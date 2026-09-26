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
import com.google.cloud.bigquery.Range;
import com.google.cloud.bigquery.StandardSQLTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StructFlattenerTest {

    private static final Field ID = Field.of("id", StandardSQLTypeName.INT64);
    private static final Field CITY = Field.of("city", StandardSQLTypeName.STRING);
    private static final Field ZIP = Field.of("zip", StandardSQLTypeName.STRING);
    private static final Field ADDRESS = Field.of("address", StandardSQLTypeName.STRUCT, CITY, ZIP);
    private static final Field TAGS = Field.newBuilder("tags", StandardSQLTypeName.STRING)
            .setMode(Field.Mode.REPEATED).build();
    private static final Field TS = Field.of("ts", StandardSQLTypeName.TIMESTAMP);
    private static final FieldList SCHEMA = FieldList.of(ID, ADDRESS, TAGS, TS);

    @Test
    public void flattensStructsIntoDottedNames() {
        List<String> names = StructFlattener.flattenFields(SCHEMA, "").stream()
                .map(StructFlattener.FlatField::qualifiedName)
                .toList();
        assertEquals(List.of("id", "address.city", "address.zip", "tags", "ts"), names);
    }

    @Test
    public void flattenedFieldsKeepTheLeafField() {
        List<StructFlattener.FlatField> flat = StructFlattener.flattenFields(SCHEMA, "row");
        assertEquals("row.address.city", flat.get(1).qualifiedName());
        assertEquals(CITY, flat.get(1).field());
    }

    @Test
    public void extractorsFollowTheSameOrderAsTheFlattenedFields() {
        List<StructFlattener.RowExtractor> extractors = StructFlattener.buildExtractors(SCHEMA);
        assertEquals(5, extractors.size());

        FieldValueList row = row(
                primitive("7"),
                FieldValue.of(FieldValue.Attribute.RECORD,
                        FieldValueList.of(List.of(primitive("Paris"), primitive("75001")), CITY, ZIP)),
                FieldValue.of(FieldValue.Attribute.REPEATED, List.of(primitive("a"), primitive("b"))),
                primitive("1700000000.5"));

        assertEquals("7", extractors.get(0).extract(row));
        assertEquals("Paris", extractors.get(1).extract(row));
        assertEquals("75001", extractors.get(2).extract(row));
        assertEquals("[\"a\", \"b\"]", extractors.get(3).extract(row));
        String ts = (String) extractors.get(4).extract(row);
        assertTrue(ts.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.500000 .+"), ts);
    }

    @Test
    public void nullStructYieldsNullLeaves() {
        List<StructFlattener.RowExtractor> extractors = StructFlattener.buildExtractors(FieldList.of(ADDRESS));
        FieldValueList row = row(primitive(null));
        assertNull(extractors.get(0).extract(row));
        assertNull(extractors.get(1).extract(row));
    }

    @Test
    public void nullLeavesReadAsNull() {
        List<StructFlattener.RowExtractor> extractors = StructFlattener.buildExtractors(FieldList.of(ID, TAGS));
        FieldValueList row = row(primitive(null), FieldValue.of(FieldValue.Attribute.REPEATED, List.of()));
        assertNull(extractors.get(0).extract(row));
        assertEquals("[]", extractors.get(1).extract(row));
    }

    @Test
    public void itemsOfARepeatedLeafAreSerialisedAsJsonLikeText() {
        List<StructFlattener.RowExtractor> extractors = StructFlattener.buildExtractors(FieldList.of(TAGS));
        FieldValue record = FieldValue.of(FieldValue.Attribute.RECORD,
                FieldValueList.of(List.of(primitive("Lyon"), primitive(null))));
        FieldValue nested = FieldValue.of(FieldValue.Attribute.REPEATED, List.of(primitive("x"), record));
        FieldValueList row = row(FieldValue.of(FieldValue.Attribute.REPEATED,
                List.of(nested, primitive(null), primitive("y"))));

        assertEquals("[[\"x\", {\"Lyon\", null}], null, \"y\"]", extractors.get(0).extract(row));
    }

    @Test
    public void rangesShowTheirBounds() {
        Field range = Field.of("period", StandardSQLTypeName.RANGE);
        List<StructFlattener.RowExtractor> extractors = StructFlattener.buildExtractors(FieldList.of(range));
        FieldValueList bounded = row(FieldValue.of(FieldValue.Attribute.RANGE,
                Range.of("[2024-01-01, 2024-02-01)")));
        assertEquals("[2024-01-01, 2024-02-01)", extractors.get(0).extract(bounded));

        FieldValueList unbounded = row(FieldValue.of(FieldValue.Attribute.RANGE,
                Range.of("[UNBOUNDED, 2024-02-01)")));
        assertEquals("[UNBOUNDED, 2024-02-01)", extractors.get(0).extract(unbounded));
    }

    private static FieldValue primitive(String value) {
        return FieldValue.of(FieldValue.Attribute.PRIMITIVE, value);
    }

    private static FieldValueList row(FieldValue... values) {
        return FieldValueList.of(List.of(values));
    }
}
