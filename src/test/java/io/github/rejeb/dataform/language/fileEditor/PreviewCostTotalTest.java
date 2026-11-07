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
package io.github.rejeb.dataform.language.fileEditor;

import io.github.rejeb.dataform.language.fileEditor.cost.CostEstimate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewCostTotalTest {

    @Test
    void failuresAreNotCounted() {
        PreviewCostTotal total = PreviewCostTotal.of(List.of(
                CostEstimate.success(10, "EU"), CostEstimate.failure("x"), CostEstimate.success(5, "EU")), 3);

        assertEquals(15, total.bytes());
        assertEquals(2, total.estimated());
        assertEquals(3, total.actions());
        assertTrue(total.visible());
    }

    @Test
    void aSingleEstimateIsATotal() {
        assertTrue(PreviewCostTotal.of(List.of(CostEstimate.success(10, "EU")), 1).visible());
        assertTrue(PreviewCostTotal.of(List.of(CostEstimate.success(10, "EU")), 3).visible());
    }

    @Test
    void noEstimateHasNoTotal() {
        assertFalse(PreviewCostTotal.of(List.of(CostEstimate.failure("x")), 2).visible());
        assertFalse(PreviewCostTotal.of(List.of(), 2).visible());
    }
}
