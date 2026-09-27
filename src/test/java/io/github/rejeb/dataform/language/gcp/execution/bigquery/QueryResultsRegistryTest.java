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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueryResultsRegistryTest {

    @Test
    void removingAFailedResultDoesNotThrow() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        registry.put(new BigQueryJobResult("orders", null, null, "Syntax error"));

        registry.remove("orders");

        assertNull(registry.get("orders"));
    }

    @Test
    void removingASuccessfulResultDisposesItsPages() {
        QueryResultsRegistry registry = new QueryResultsRegistry();
        BigQueryPagedResult pages = mock(BigQueryPagedResult.class);
        registry.put(new BigQueryJobResult("orders", null, pages, null));

        registry.remove("orders");

        verify(pages).dispose();
        assertNull(registry.get("orders"));
    }
}
