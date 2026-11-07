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
package io.github.rejeb.dataform.language.fileEditor.cost;

import com.google.cloud.bigquery.TableId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BigQueryDryRunBytesRunnerTest {

    @Test
    void aQueryReadingATableIsBilledTheMinimum() {
        assertEquals(CostEstimate.MINIMUM_BILLED_BYTES,
                BigQueryDryRunBytesRunner.minimumBilledBytes(List.of(TableId.of("p", "d", "t"))));
    }

    @Test
    void theMinimumIsAppliedOncePerQuery() {
        assertEquals(CostEstimate.MINIMUM_BILLED_BYTES, BigQueryDryRunBytesRunner.minimumBilledBytes(
                List.of(TableId.of("p", "d", "a"), TableId.of("p", "d", "b"))));
    }

    @Test
    void aQueryReadingNoTableHasNoMinimum() {
        assertEquals(0, BigQueryDryRunBytesRunner.minimumBilledBytes(List.of()));
        assertEquals(0, BigQueryDryRunBytesRunner.minimumBilledBytes(null));
    }
}
