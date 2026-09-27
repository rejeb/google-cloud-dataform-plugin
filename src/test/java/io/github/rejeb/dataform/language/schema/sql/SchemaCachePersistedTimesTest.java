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

import java.util.Set;

/**
 * A refresh is planned against the modification times the last completed run recorded: those read
 * back from the persisted state, then those written by each persist, never the working cache.
 */
public class SchemaCachePersistedTimesTest extends BasePlatformTestCase {

    private static final String COLUMNS = "\"columns\":[{\"name\":\"id\",\"type\":\"STRING\",\"mode\":\"NULLABLE\",\"subFields\":[]}]";

    private static DataformTableSchemaService.State state(String json) {
        DataformTableSchemaService.State state = new DataformTableSchemaService.State();
        state.schemaCacheJson = json;
        return state;
    }

    private static String entry(String fqn, long lastModified) {
        return "\"" + fqn + "\":{" + COLUMNS + ",\"lastModified\":" + lastModified
                + ",\"fileName\":\"definitions/" + fqn.substring(fqn.lastIndexOf('.') + 1) + ".sqlx\"}";
    }

    public void testTheTimesOfALoadedStateAreThoseItRecorded() {
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.load(state("{" + entry("p.d.orders", 1_700L) + "," + entry("p.d.users", 0L) + "}"));

        assertEquals(Long.valueOf(1_700L), cache.persistedModificationTime("p.d.orders"));
        assertNull("an unknown time is not trusted", cache.persistedModificationTime("p.d.users"));
        assertNull(cache.persistedModificationTime("p.d.missing"));
    }

    public void testAPersistReplacesTheTimesWithThoseOfTheWorkingCache() {
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.load(state("{" + entry("p.d.orders", 1_700L) + "," + entry("p.d.users", 1_800L) + "}"));

        cache.retainOnly(Set.of("p.d.users"));
        assertEquals("the working cache is not read before it is persisted",
                Long.valueOf(1_700L), cache.persistedModificationTime("p.d.orders"));

        cache.persist();
        assertNull(cache.persistedModificationTime("p.d.orders"));
        assertEquals(Long.valueOf(1_800L), cache.persistedModificationTime("p.d.users"));
    }

    public void testAStateThatCannotBeReadRecordsNoTime() {
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.load(state("{" + entry("p.d.orders", 1_700L) + "}"));

        cache.load(state("{not json"));

        assertNull(cache.persistedModificationTime("p.d.orders"));
    }
}
