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
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryRunCostsImplTest {

    private final QueryRunCostsImpl costs = new QueryRunCostsImpl();

    @Test
    void anActionThatNeverRanHasNoCost() {
        assertNull(costs.of("p.d.orders"));
    }

    @Test
    void theCostOfARunIsKeptUnderItsAction() {
        RunCost cost = new RunCost("select 1", 593, 10_485_760, "EU");

        costs.record("p.d.orders", cost);

        assertEquals(cost, costs.of("p.d.orders"));
        assertNull(costs.of("p.d.customers"));
    }

    @Test
    void theLastRunReplacesTheOneBefore() {
        costs.record("p.d.orders", new RunCost("select 1", 593, 10_485_760, "EU"));
        RunCost last = new RunCost("select 2", 1_024, 10_485_760, "EU");

        costs.record("p.d.orders", last);

        assertEquals(last, costs.of("p.d.orders"));
    }
}
