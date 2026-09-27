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
package io.github.rejeb.dataform.language.schema.sql;

import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataformActionColumnsTest {

    private static final ColumnInfo CITY = new ColumnInfo("city", "STRING", "NULLABLE", null);
    private static final ColumnInfo GEO = new ColumnInfo("geo", "RECORD", "NULLABLE", null, List.of(CITY));
    private static final ColumnInfo ADDRESS = new ColumnInfo("address", "RECORD", "NULLABLE", null, List.of(GEO));
    private static final ColumnInfo ID = new ColumnInfo("id", "INT64", "REQUIRED", null);
    private static final List<ColumnInfo> COLUMNS = List.of(ID, ADDRESS);

    @Test
    public void findIgnoresCase() {
        assertSame(ID, DataformActionColumns.find(COLUMNS, "ID").orElseThrow());
        assertSame(ADDRESS, DataformActionColumns.find(COLUMNS, "Address").orElseThrow());
        assertTrue(DataformActionColumns.find(COLUMNS, "missing").isEmpty());
    }

    @Test
    public void emptyPathReturnsTheColumnsUnchanged() {
        assertSame(COLUMNS, DataformActionColumns.descend(COLUMNS, List.of()));
    }

    @Test
    public void descendWalksNestedRecords() {
        assertEquals(List.of(GEO), DataformActionColumns.descend(COLUMNS, List.of("address")));
        assertEquals(List.of(CITY), DataformActionColumns.descend(COLUMNS, List.of("ADDRESS", "geo")));
    }

    @Test
    public void descendBelowALeafOrUnknownColumnIsEmpty() {
        assertTrue(DataformActionColumns.descend(COLUMNS, List.of("id", "x")).isEmpty());
        assertTrue(DataformActionColumns.descend(COLUMNS, List.of("nope")).isEmpty());
        assertTrue(DataformActionColumns.descend(COLUMNS, List.of("address", "geo", "city", "deeper")).isEmpty());
    }
}
