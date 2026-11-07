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
package io.github.rejeb.dataform.language.gcp.execution.workflow.repository;

import com.google.cloud.bigquery.TableId;
import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails.BigQueryChildJob;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatementFactsTest {

    private static final Instant T = Instant.parse("2026-11-03T10:42:00Z");

    @Test
    void ddlStatementsNameTheirTargetTable() {
        assertEquals("p.d.t", StatementFacts.target("CREATE_TABLE_AS_SELECT",
                TableId.of("p", "d", "t"), TableId.of("p", "_anon", "x")));
    }

    @Test
    void dmlStatementsNameTheirDestinationTable() {
        for (String type : List.of("INSERT", "UPDATE", "DELETE", "MERGE")) {
            assertEquals("p.d.t", StatementFacts.target(type, null, TableId.of("p", "d", "t")));
        }
    }

    @Test
    void selectsNeverShowTheirTemporaryDestination() {
        assertNull(StatementFacts.target("SELECT", null, TableId.of("p", "_script9", "anon")));
    }

    @Test
    void unknownStatementsHaveNoTarget() {
        assertNull(StatementFacts.target(null, null, TableId.of("p", "d", "t")));
    }

    @Test
    void aTableWithoutProjectIsNamedByDatasetAndTable() {
        assertEquals("d.t", StatementFacts.target("MERGE", null, TableId.of("d", "t")));
    }

    @Test
    void childJobsAreSortedOldestFirstWithUnstartedLast() {
        List<BigQueryChildJob> sorted = StatementFacts.inRunOrder(List.of(
                child("c", T.plusSeconds(3)), child("b", null), child("a", T.plusSeconds(1)),
                child("d", T.plusSeconds(3))));

        assertEquals(List.of("a", "c", "d", "b"), sorted.stream().map(BigQueryChildJob::jobId).toList());
    }

    private static BigQueryChildJob child(String id, Instant start) {
        return new BigQueryChildJob(id, "DONE", start, start, "SELECT 1", 1L);
    }
}
