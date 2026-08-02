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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.Map;

/**
 * The map returned by {@code getAllTables} feeds SQL reference resolution inside
 * {@code ResolveCache}, which reruns a computation and reports a non-idempotent result when the two
 * runs disagree. It must therefore be an immutable snapshot, never a view of the working cache.
 */
public class DataformTableSchemaSnapshotTest extends BasePlatformTestCase {

    private static final String CACHE_JSON = """
            {"p.d.orders":{"columns":[{"name":"order_id","type":"STRING","mode":"NULLABLE",\
            "subFields":[]}],"lastModified":0,"fileName":"definitions/orders.sqlx"}}""";

    public void testRestoredStateIsVisibleThroughTheSnapshot() {
        DataformTableSchemaService service = DataformTableSchemaService.getInstance(getProject());
        service.loadState(state(CACHE_JSON));

        Map<String, DataformDasTable> tables = service.getAllTables();
        assertTrue("a restored schema must be visible to resolution",
                tables.containsKey("p.d.orders"));
        assertEquals("orders", tables.get("p.d.orders").getName());
    }

    public void testSnapshotHandedOutBeforeAReloadDoesNotChange() {
        DataformTableSchemaService service = DataformTableSchemaService.getInstance(getProject());
        service.loadState(state(CACHE_JSON));
        Map<String, DataformDasTable> before = service.getAllTables();

        service.loadState(state("{}"));

        assertTrue("a snapshot already handed out must not observe a later reload",
                before.containsKey("p.d.orders"));
        assertFalse("the new snapshot must reflect the reload",
                service.getAllTables().containsKey("p.d.orders"));
    }

    public void testSnapshotIsUnmodifiable() {
        DataformTableSchemaService service = DataformTableSchemaService.getInstance(getProject());
        service.loadState(state(CACHE_JSON));
        try {
            service.getAllTables().clear();
            fail("the snapshot must not be modifiable by callers");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static DataformTableSchemaService.State state(String json) {
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = json;
        return state;
    }
}
