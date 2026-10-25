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

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreviewQueriesTest {

    private static final String ORDERS = "definitions/orders.sqlx";

    private static CompiledGraph graph(String json) {
        return new Gson().fromJson(json, CompiledGraph.class);
    }

    @Test
    void testExecutableReturnsTheCompiledQueriesOfTheFile() {
        CompiledGraph graph = graph("{\"tables\":[{\"type\":\"table\",\"fileName\":\"" + ORDERS + "\","
                + "\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"orders\"},\"query\":\"SELECT 1\"}]}");

        List<FormattedCompiledQuery> queries = PreviewQueries.executable(graph, ORDERS, false);

        assertEquals(1, queries.size());
        assertEquals("SELECT 1", queries.getFirst().query());
    }

    @Test
    void testExecutableIsEmptyForAFileWithoutAction() {
        CompiledGraph graph = graph("{\"tables\":[]}");

        assertTrue(PreviewQueries.executable(graph, ORDERS, false).isEmpty());
    }

    @Test
    void testErrorsOfJoinsTheMessagesOfTheFile() {
        CompiledGraph graph = graph("{\"graphErrors\":{\"compilationErrors\":["
                + "{\"fileName\":\"" + ORDERS + "\",\"message\":\"a\"},"
                + "{\"fileName\":\"" + ORDERS + "\",\"message\":\"b\"}]}}");

        assertEquals("a\nb", PreviewQueries.errorsOf(graph, ORDERS));
        assertNull(PreviewQueries.errorsOf(graph, "definitions/other.sqlx"));
    }
}
