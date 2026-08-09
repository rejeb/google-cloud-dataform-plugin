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

import com.google.gson.Gson;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompilationFailuresTest {

    private static final Gson GSON = new Gson();

    private CompiledGraph graph(String json) {
        return GSON.fromJson(json, CompiledGraph.class);
    }

    @Test
    void cleanGraphReportsNoFailure() {
        CompiledGraph graph = graph("{\"tables\":[{\"target\":{\"database\":\"p\",\"schema\":\"d\","
                + "\"name\":\"t\"},\"query\":\"SELECT 1\",\"fileName\":\"definitions/t.sqlx\"}]}");

        assertTrue(CompilationFailures.fileNamesOf(graph).isEmpty());
        assertTrue(CompilationFailures.errorsOf(graph).isEmpty());
        assertFalse(CompilationFailures.isTotalFailure(graph));
    }

    @Test
    void failingFilesAreListedIndividually() {
        CompiledGraph graph = graph("{\"tables\":[{\"target\":{\"database\":\"p\",\"schema\":\"d\","
                + "\"name\":\"ok\"},\"query\":\"SELECT 1\",\"fileName\":\"definitions/ok.sqlx\"}],"
                + "\"graphErrors\":{\"compilationErrors\":["
                + "{\"fileName\":\"definitions/broken.sqlx\",\"message\":\"boom\"}]}}");

        assertEquals(Set.of("definitions/broken.sqlx"), CompilationFailures.fileNamesOf(graph),
                "only the failing file must be excluded from the schema refresh");
        assertFalse(CompilationFailures.isTotalFailure(graph),
                "actions that compiled must still be refreshable");
    }

    @Test
    void errorWithoutFileNameIdentifiesNoAction() {
        CompiledGraph graph = graph("{\"tables\":[{\"target\":{\"database\":\"p\",\"schema\":\"d\","
                + "\"name\":\"ok\"},\"query\":\"SELECT 1\",\"fileName\":\"definitions/ok.sqlx\"}],"
                + "\"graphErrors\":{\"compilationErrors\":[{\"stack\":\"stderr dump\"}]}}");

        assertTrue(CompilationFailures.fileNamesOf(graph).isEmpty());
        assertEquals(1, CompilationFailures.errorsOf(graph).size());
    }

    @Test
    void graphWithErrorsAndNoActionIsATotalFailure() {
        CompiledGraph graph = graph("{\"graphErrors\":{\"compilationErrors\":["
                + "{\"stack\":\"dataform compile crashed\"}]}}");

        assertTrue(CompilationFailures.isTotalFailure(graph),
                "a graph with errors and no surviving action cannot refresh anything");
    }

    @Test
    void nullGraphIsATotalFailure() {
        assertTrue(CompilationFailures.isTotalFailure(null));
        assertTrue(CompilationFailures.fileNamesOf(null).isEmpty());
        assertTrue(CompilationFailures.errorsOf(null).isEmpty());
    }
}
