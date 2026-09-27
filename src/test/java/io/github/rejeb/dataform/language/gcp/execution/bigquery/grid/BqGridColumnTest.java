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
import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BqGridColumnTest {

    @Test
    public void exposesIndexNameAndBigQueryTypeName() {
        BqGridColumn column = new BqGridColumn(3, "address.city", Field.of("city", StandardSQLTypeName.STRING));
        assertEquals(3, column.getColumnNumber());
        assertEquals("address.city", column.getName());
        assertEquals("STRING", column.getTypeName());
        assertTrue(column.getAttributes().isEmpty());
    }

    @Test
    public void mapsBigQueryTypesToJdbcTypes() {
        assertEquals(Types.BIGINT, sqlType(StandardSQLTypeName.INT64));
        assertEquals(Types.DOUBLE, sqlType(StandardSQLTypeName.FLOAT64));
        assertEquals(Types.BOOLEAN, sqlType(StandardSQLTypeName.BOOL));
        assertEquals(Types.DATE, sqlType(StandardSQLTypeName.DATE));
        assertEquals(Types.TIMESTAMP, sqlType(StandardSQLTypeName.DATETIME));
        assertEquals(Types.TIMESTAMP, sqlType(StandardSQLTypeName.TIMESTAMP));
        assertEquals(Types.NUMERIC, sqlType(StandardSQLTypeName.NUMERIC));
        assertEquals(Types.NUMERIC, sqlType(StandardSQLTypeName.BIGNUMERIC));
        assertEquals(Types.BINARY, sqlType(StandardSQLTypeName.BYTES));
        assertEquals(Types.STRUCT, new BqGridColumn(0, "s",
                Field.of("s", StandardSQLTypeName.STRUCT, Field.of("x", StandardSQLTypeName.STRING))).getType());
        assertEquals(Types.VARCHAR, sqlType(StandardSQLTypeName.STRING));
        assertEquals(Types.VARCHAR, sqlType(StandardSQLTypeName.GEOGRAPHY));
        assertEquals(Types.VARCHAR, sqlType(StandardSQLTypeName.JSON));
    }

    private static int sqlType(StandardSQLTypeName type) {
        return new BqGridColumn(0, "c", Field.of("c", type)).getType();
    }
}
