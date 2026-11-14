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
package io.github.rejeb.dataform.language.lineage.dataplex;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceGroupsTest {

    private static TraceEntry table(String fullName, int level) {
        return new TraceEntry(EntityNames.fqnOf(fullName), level);
    }

    @Test
    void entriesAreGroupedByProjectWithTheRepositoryProjectFirst() {
        LinkedHashMap<String, List<TraceEntry>> groups = TraceGroups.group(List.of(
                table("other.landing.raw", 2), table("p.staging.stg_orders", 1), table("abc.ingest.files", 3)), "p");

        assertEquals(List.of("p", "abc", "other"), List.copyOf(groups.keySet()));
        assertEquals(List.of(table("p.staging.stg_orders", 1)), groups.get("p"));
    }

    @Test
    void nonBigQueryEntriesAreGroupedByKind() {
        LinkedHashMap<String, List<TraceEntry>> groups = TraceGroups.group(List.of(
                new TraceEntry("gs://bucket/exports/orders.csv", 1), new TraceEntry("looker:dashboards.revenue", 2),
                new TraceEntry("gs://bucket/exports/customers.csv", 2)), "p");

        assertEquals(List.of("Cloud Storage", "looker"), List.copyOf(groups.keySet()));
        assertEquals(2, groups.get("Cloud Storage").size());
    }

    @Test
    void rowsOfAGroupAreOrderedByLevelThenName() {
        LinkedHashMap<String, List<TraceEntry>> groups = TraceGroups.group(List.of(
                table("p.d.zeta", 1), table("p.d.beta", 2), table("p.d.alpha", 2), table("p.d.alpha_first", 1)), "p");

        assertEquals(List.of(table("p.d.alpha_first", 1), table("p.d.zeta", 1), table("p.d.alpha", 2), table("p.d.beta", 2)),
                groups.get("p"));
    }

    @Test
    void noEntryGivesNoGroup() {
        assertTrue(TraceGroups.group(List.of(), "p").isEmpty());
    }
}
