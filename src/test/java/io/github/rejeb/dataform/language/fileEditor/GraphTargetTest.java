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

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphTargetTest {

    private static final String FILE = "definitions/orders.sqlx";

    @Test
    void everyActionButAnOperationWithoutOutputHasATable() {
        CompiledGraph graph = new Gson().fromJson("{"
                + "\"tables\":[{\"type\":\"table\",\"fileName\":\"" + FILE + "\"," + target("orders") + "}],"
                + "\"operations\":[{\"fileName\":\"" + FILE + "\"," + target("cleanup") + ",\"hasOutput\":false},"
                + "{\"fileName\":\"" + FILE + "\"," + target("snapshot") + ",\"hasOutput\":true}],"
                + "\"assertions\":[{\"fileName\":\"" + FILE + "\"," + target("orders_assertions_uniqueKey_0") + "}],"
                + "\"declarations\":[{\"fileName\":\"" + FILE + "\"," + target("raw_orders") + "}]}",
                CompiledGraph.class);

        List<GraphTarget> targets = GraphTarget.targetsOf(graph, FILE);

        assertEquals(List.of("orders:true", "cleanup:false", "snapshot:true", "orders_assertions_uniqueKey_0:true",
                        "raw_orders:true"),
                targets.stream().map(t -> t.name() + ":" + t.hasTable()).toList());
    }

    @Test
    void aTableKeepsTheKindDataformCompiledItTo() {
        CompiledGraph graph = new Gson().fromJson("{\"tables\":["
                + "{\"type\":\"table\",\"fileName\":\"" + FILE + "\"," + target("orders") + "},"
                + "{\"type\":\"incremental\",\"fileName\":\"" + FILE + "\"," + target("orders_daily") + "},"
                + "{\"type\":\"view\",\"fileName\":\"" + FILE + "\"," + target("orders_view") + "}]}",
                CompiledGraph.class);

        List<GraphTarget> targets = GraphTarget.targetsOf(graph, FILE);

        assertEquals(List.of("orders:table", "orders_daily:incremental", "orders_view:view"),
                targets.stream().map(t -> t.name() + ":" + t.type()).toList());
    }

    private static String target(String name) {
        return "\"target\":{\"database\":\"p\",\"schema\":\"d\",\"name\":\"" + name + "\"}";
    }
}
