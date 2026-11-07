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
package io.github.rejeb.dataform.language.gcp.execution.workflow.runconfig.ui;

import io.github.rejeb.dataform.language.gcp.execution.workflow.model.BigQueryJobDetails;
import io.github.rejeb.dataform.language.gcp.pricing.CostText;
import io.github.rejeb.dataform.language.gcp.pricing.PriceQuote;
import io.github.rejeb.dataform.language.gcp.pricing.PriceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionCostTest {

    private static final long GIB = 1L << 30;
    private static final PriceQuote LIVE = new PriceQuote("EU", PriceSource.LIVE, new BigDecimal("6.25"), "USD",
            Instant.parse("2026-11-01T07:00:00Z"), false);

    @Test
    void aFailedQueryIsNotBilled() {
        CostText cost = ActionCost.of(details("FAILED", 5 * GIB), LIVE);

        assertEquals("$0.00", cost.value());
        assertEquals("failed queries are not billed", cost.note());
        assertTrue(cost.tooltip().contains("EU · $6.25 per TiB billed · list price"), cost.tooltip());
    }

    @Test
    void aDoneQueryCostsItsBilledBytes() {
        CostText cost = ActionCost.of(details("DONE", Math.round(4.83 * GIB)), LIVE);

        assertEquals("$0.0295", cost.value());
        assertEquals("EU · $6.25 per TiB billed · list price", cost.note());
    }

    @Test
    void aJobOnAReservationIsNotBilledPerByte() {
        CostText cost = ActionCost.of(reserved("prod-etl"), LIVE);

        assertEquals("Not billed per byte", cost.value());
        assertEquals("capacity pricing · reservation prod-etl", cost.note());
        assertEquals("", cost.tooltip());
    }

    @Test
    void unknownBilledBytesHaveNoCost() {
        assertEquals("—", ActionCost.of(details("DONE", null), LIVE).value());
    }

    static BigQueryJobDetails details(String status, Long billed) {
        return new BigQueryJobDetails("job", "p", "EU", status, null, billed, billed, null, null, null, List.of(), null);
    }

    static BigQueryJobDetails reserved(String reservation) {
        return new BigQueryJobDetails("job", "p", "EU", "DONE", null, 1L << 40, 1L << 40, null, null, null, List.of(),
                reservation);
    }
}
