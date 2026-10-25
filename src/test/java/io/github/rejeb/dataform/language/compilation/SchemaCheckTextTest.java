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
package io.github.rejeb.dataform.language.compilation;

import io.github.rejeb.dataform.language.schema.sql.SchemaRefreshOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaCheckTextTest {

    @Test
    void cleanCheckCountsTheQueries() {
        assertEquals("31 action queries checked in BigQuery",
                SchemaCheckText.of(new SchemaRefreshOutcome(true, 31), 0));
    }

    @Test
    void failuresAreCountedAgainstTheQueriesChecked() {
        assertEquals("3 of 31 action queries failed in BigQuery",
                SchemaCheckText.of(new SchemaRefreshOutcome(true, 31), 3));
    }

    @Test
    void anExtractionThatDidNotRunSaysSo() {
        assertEquals("action queries not checked in BigQuery",
                SchemaCheckText.of(SchemaRefreshOutcome.NOT_RUN, 0));
    }
}
