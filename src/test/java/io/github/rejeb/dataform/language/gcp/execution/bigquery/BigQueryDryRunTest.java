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
package io.github.rejeb.dataform.language.gcp.execution.bigquery;

import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigQueryDryRunTest {

    @Test
    void theJobIsAStandardSqlDryRunInTheGivenLocation() {
        JobInfo job = BigQueryDryRun.jobInfo("SELECT 1", "EU");

        QueryJobConfiguration config = job.getConfiguration();
        assertEquals("SELECT 1", config.getQuery());
        assertTrue(config.dryRun());
        assertEquals(Boolean.FALSE, config.useLegacySql());
        assertEquals("EU", job.getJobId().getLocation());
    }

    @Test
    void noLocationLeavesTheJobUnlocated() {
        assertNull(BigQueryDryRun.jobInfo("SELECT 1", null).getJobId().getLocation());
    }

    @Test
    void aBlankLocationLeavesTheJobUnlocated() {
        assertNull(BigQueryDryRun.jobInfo("SELECT 1", " ").getJobId().getLocation());
    }

    @Test
    void theMessageIsTheExceptionMessage() {
        assertEquals("boom", BigQueryDryRun.messageOf(new RuntimeException("boom")));
    }

    @Test
    void aBlankMessageFallsBackToTheCauseMessage() {
        assertEquals("inner", BigQueryDryRun.messageOf(new RuntimeException(" ", new IllegalStateException("inner"))));
    }

    @Test
    void noMessageAnywhereGivesTheExceptionName() {
        assertEquals("IllegalStateException", BigQueryDryRun.messageOf(new IllegalStateException()));
    }
}
