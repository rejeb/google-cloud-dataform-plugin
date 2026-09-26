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

import java.util.List;
import java.util.Map;

/**
 * A partial refresh dry-runs only the modified actions and their dependents. Their upstream tables
 * must still be stubbed from the cache, or the dry-run reads the real BigQuery table, which in a
 * development dataset is often missing or carries an older schema.
 */
public class SchemaCacheStubSchemasTest extends BasePlatformTestCase {

    private static List<ColumnInfo> columns(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> new ColumnInfo(name, "STRING", "NULLABLE", null))
                .toList();
    }

    public void testAnUpstreamTableNotRefreshedInThisRunIsStubbedFromTheCache() {
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.put("p.d.customers", "customers", columns("customer_id", "name"), null);

        Map<String, List<ColumnInfo>> stubs = cache.stubSchemas(Map.of());

        assertEquals(columns("customer_id", "name"), stubs.get("p.d.customers"));
    }

    public void testASchemaResolvedInThisRunWinsOverTheCachedOne() {
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.put("p.d.customers", "customers", columns("customer_id"), null);

        Map<String, List<ColumnInfo>> stubs = cache.stubSchemas(
                Map.of("p.d.customers", columns("customer_id", "email")));

        assertEquals(columns("customer_id", "email"), stubs.get("p.d.customers"));
    }

    public void testCachedAndFreshlyResolvedTablesAreBothStubbed() {
        SchemaCacheStore cache = new SchemaCacheStore(getProject());
        cache.put("p.d.customers", "customers", columns("customer_id"), null);

        Map<String, List<ColumnInfo>> stubs = cache.stubSchemas(
                Map.of("p.d.orders", columns("order_id")));

        assertEquals(2, stubs.size());
        assertEquals(columns("order_id"), stubs.get("p.d.orders"));
    }
}
