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
package io.github.rejeb.dataform.language.diagnostics.sql.hint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NameSuggesterTest {

    @Test
    public void theClosestNamesComeFirst() {
        assertEquals(List.of("order_id", "order_ts"),
                NameSuggester.closest("order_i", List.of("customer_id", "order_ts", "order_id"), 3));
    }

    @Test
    public void aNameTooFarIsNotSuggested() {
        assertEquals(List.of(), NameSuggester.closest("zzz", List.of("order_id"), 3));
    }

    @Test
    public void theNameItselfIsNotSuggested() {
        assertEquals(List.of("order_ids"), NameSuggester.closest("ORDER_ID", List.of("order_id", "order_ids"), 3));
    }
}
