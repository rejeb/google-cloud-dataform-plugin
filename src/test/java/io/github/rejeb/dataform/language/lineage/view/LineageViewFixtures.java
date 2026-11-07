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
package io.github.rejeb.dataform.language.lineage.view;

import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

final class LineageViewFixtures {

    static final LineageNode ALPHA = node("p.a.alpha", "a", "table", List.of("x"), "definitions/alpha.sqlx");
    static final LineageNode BETA = node("p.b.beta", "b", "view", List.of("y", "x"), "definitions/beta.sqlx");
    static final LineageNode GAMMA = node("p.a.gamma", "a", "table", List.of(), null);

    private LineageViewFixtures() {
    }

    static LineageNode node(String fullName, String schema, String type, List<String> tags, String file) {
        String name = fullName.substring(fullName.lastIndexOf('.') + 1);
        return new LineageNode(LineageNode.idOf(fullName), name, fullName, schema, type, tags, file);
    }

    static LineageModel model() {
        LineageModel model = new LineageModel(null);
        model.setGraph(LineageGraph.builder()
                .addNode(ALPHA).addNode(BETA).addNode(GAMMA)
                .addEdge(ALPHA.id(), BETA.id())
                .build());
        return model;
    }

    static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(all(container, type));
            }
        }
        return found;
    }
}
