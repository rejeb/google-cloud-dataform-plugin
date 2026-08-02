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
import io.github.rejeb.dataform.language.schema.sql.model.ColumnInfo;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;

import java.util.List;
import java.util.Map;

/**
 * An action is re-extracted when its source is newer than the time recorded for it, so a time no
 * file can ever exceed would pin it to its cached schema for good.
 */
public class SchemaCacheEntriesTest extends BasePlatformTestCase {

    private DataformDasTable table(String name) {
        return new DataformDasTable(getPsiManager(), name,
                List.of(new ColumnInfo("id", "STRING", "NULLABLE", null)), null);
    }

    public void testAKnownModificationTimeIsPersistedAsIs() {
        Map<String, SchemaCacheEntry> entries = SchemaCacheStore.toCacheEntries(
                Map.of("p.d.orders", table("orders")),
                Map.of("p.d.orders", 1_700L),
                Map.of("p.d.orders", "definitions/orders.sqlx"));

        assertEquals(1_700L, entries.get("p.d.orders").lastModified());
    }

    public void testAnActionWithoutAKnownModificationTimeIsNotPinned() {
        Map<String, SchemaCacheEntry> entries = SchemaCacheStore.toCacheEntries(
                Map.of("p.d.orders", table("orders")),
                Map.of(),
                Map.of("p.d.orders", "definitions/orders.sqlx"));

        long persisted = entries.get("p.d.orders").lastModified();
        assertFalse("an unknown time must not be trusted",
                SchemaCacheStore.isKnownModificationTime(persisted));
        assertTrue("a pinned time would keep the action from ever being extracted again",
                persisted < System.currentTimeMillis());
    }

    public void testAMaxValueTimeLeftByAnOlderBuildIsTreatedAsUnknown() {
        assertFalse(SchemaCacheStore.isKnownModificationTime(Long.MAX_VALUE));
    }

    public void testAZeroTimeIsTreatedAsUnknown() {
        assertFalse(SchemaCacheStore.isKnownModificationTime(0L));
    }

    public void testARealTimeIsTreatedAsKnown() {
        assertTrue(SchemaCacheStore.isKnownModificationTime(1_700L));
    }
}
