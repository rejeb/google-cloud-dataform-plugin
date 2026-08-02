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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataformDisabledActionsImplTest {

    private static final Gson GSON = new Gson();

    private CompiledGraph graph(String json) {
        return GSON.fromJson(json, CompiledGraph.class);
    }

    private String table(String name, String fileName, boolean disabled) {
        return "{\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"" + name + "\"},"
                + "\"fileName\":\"" + fileName + "\",\"disabled\":" + disabled + "}";
    }

    @Test
    void disabledTableMarksItsFile() {
        CompiledGraph graph = graph("{\"tables\":["
                + table("off", "definitions/off.sqlx", true) + "]}");

        assertTrue(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/off.sqlx"));
    }

    @Test
    void enabledTableDoesNotMarkItsFile() {
        CompiledGraph graph = graph("{\"tables\":["
                + table("on", "definitions/on.sqlx", false) + "]}");

        assertFalse(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/on.sqlx"));
    }

    @Test
    void fileDefiningNoActionIsNotMarked() {
        CompiledGraph graph = graph("{\"tables\":["
                + table("off", "definitions/off.sqlx", true) + "]}");

        assertFalse(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/other.sqlx"),
                "a file the compiled graph does not know must not be marked disabled");
    }

    @Test
    void fileMixingDisabledAndEnabledActionsIsNotMarked() {
        CompiledGraph graph = graph("{\"tables\":["
                + table("off", "definitions/mixed.sqlx", true) + "],"
                + "\"assertions\":[{\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"a\"},"
                + "\"fileName\":\"definitions/mixed.sqlx\",\"disabled\":false}]}");

        assertFalse(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/mixed.sqlx"),
                "a file still running one of its actions is not disabled");
    }

    @Test
    void disabledOperationMarksItsFile() {
        CompiledGraph graph = graph("{\"operations\":[{"
                + "\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"op\"},"
                + "\"fileName\":\"definitions/op.sqlx\",\"disabled\":true}]}");

        assertTrue(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/op.sqlx"));
    }

    @Test
    void windowsFileNameMatchesAUnixPath() {
        CompiledGraph graph = graph("{\"tables\":["
                + table("off", "definitions\\\\off.sqlx", true) + "]}");

        assertTrue(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/off.sqlx"),
                "a graph compiled on Windows must still match the local path");
    }

    @Test
    void missingGraphMarksNothing() {
        assertFalse(DataformDisabledActionsImpl.allActionsDisabled(
                null, "/home/me/project/definitions/off.sqlx"));
    }

    @Test
    void similarlyNamedFileIsNotMarked() {
        CompiledGraph graph = graph("{\"tables\":["
                + table("off", "definitions/off.sqlx", true) + "]}");

        assertFalse(DataformDisabledActionsImpl.allActionsDisabled(
                graph, "/home/me/project/definitions/not_off.sqlx"),
                "suffix matching must not treat a longer name as the same file");
    }
}
