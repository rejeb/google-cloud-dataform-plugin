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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostEstimateTest {

    @Test
    void theMinimumIsTenMebibytes() {
        assertEquals(10_485_760L, CostEstimate.MINIMUM_BILLED_BYTES);
    }

    @Test
    void aSmallQueryIsBilledTheMinimum() {
        assertEquals(CostEstimate.MINIMUM_BILLED_BYTES,
                CostEstimate.success(593, "EU", CostEstimate.MINIMUM_BILLED_BYTES).billedBytes());
    }

    @Test
    void aLargeQueryIsBilledItsBytes() {
        long bytes = 5 * CostEstimate.MINIMUM_BILLED_BYTES;
        assertEquals(bytes, CostEstimate.success(bytes, "EU", CostEstimate.MINIMUM_BILLED_BYTES).billedBytes());
    }

    @Test
    void aQueryWithoutMinimumIsBilledItsBytes() {
        assertEquals(593, CostEstimate.success(593, "EU").billedBytes());
    }

    @Test
    void theBytesProcessedStayWhatTheDryRunReported() {
        assertEquals(593L, CostEstimate.success(593, "EU", CostEstimate.MINIMUM_BILLED_BYTES).bytesProcessed());
    }
}
