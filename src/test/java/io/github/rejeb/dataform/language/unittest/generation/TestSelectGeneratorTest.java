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
package io.github.rejeb.dataform.language.unittest.generation;

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.unittest.UnitTestSchemaFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSelectGeneratorTest {

    private static ColumnInfo column(String name, String type) {
        return new ColumnInfo(name, type, "NULLABLE", null);
    }

    @Test
    void eachScalarTypeGetsATypedPlaceholder() {
        assertEquals("''", BigQueryPlaceholderValues.of(column("a", "STRING")));
        assertEquals("0", BigQueryPlaceholderValues.of(column("a", "INT64")));
        assertEquals("0.0", BigQueryPlaceholderValues.of(column("a", "FLOAT64")));
        assertEquals("NUMERIC '0'", BigQueryPlaceholderValues.of(column("a", "NUMERIC")));
        assertEquals("BIGNUMERIC '0'", BigQueryPlaceholderValues.of(column("a", "BIGNUMERIC")));
        assertEquals("FALSE", BigQueryPlaceholderValues.of(column("a", "BOOL")));
        assertEquals("b''", BigQueryPlaceholderValues.of(column("a", "BYTES")));
        assertEquals("DATE '1970-01-01'", BigQueryPlaceholderValues.of(column("a", "DATE")));
        assertEquals("DATETIME '1970-01-01 00:00:00'", BigQueryPlaceholderValues.of(column("a", "DATETIME")));
        assertEquals("TIME '00:00:00'", BigQueryPlaceholderValues.of(column("a", "TIME")));
        assertEquals("TIMESTAMP '1970-01-01 00:00:00 UTC'", BigQueryPlaceholderValues.of(column("a", "TIMESTAMP")));
        assertEquals("JSON 'null'", BigQueryPlaceholderValues.of(column("a", "JSON")));
        assertEquals("ST_GEOGPOINT(0, 0)", BigQueryPlaceholderValues.of(column("a", "GEOGRAPHY")));
        assertEquals("INTERVAL 0 DAY", BigQueryPlaceholderValues.of(column("a", "INTERVAL")));
        assertEquals("CAST(NULL AS RANGE<DATE>)", BigQueryPlaceholderValues.of(column("a", "RANGE<DATE>")));
    }

    @Test
    void recordsAndRepeatedColumnsAreBuiltRecursively() {
        assertEquals("STRUCT('' AS city, STRUCT(0.0 AS lat) AS geo)",
                BigQueryPlaceholderValues.of(UnitTestSchemaFixture.ORDERS.get(2)));
        assertEquals("[STRUCT('' AS sku, 0 AS qty)]",
                BigQueryPlaceholderValues.of(UnitTestSchemaFixture.ORDERS.get(3)));
        assertEquals("['']", BigQueryPlaceholderValues.of(new ColumnInfo("tags", "STRING", "REPEATED", null)));
    }

    @Test
    void reservedWordsAreQuoted() {
        assertEquals("`order`", BigQueryPlaceholderValues.identifier("order"));
        assertEquals("amount", BigQueryPlaceholderValues.identifier("amount"));
    }

    @Test
    void theSelectListsEveryColumnOnItsOwnLine() {
        assertEquals("SELECT\n    '' AS id,\n    0.0 AS amount,\n    FALSE AS `from`",
                TestSelectGenerator.select(List.of(
                        column("id", "STRING"), column("amount", "FLOAT64"), column("from", "BOOL")), "  "));
    }
}
