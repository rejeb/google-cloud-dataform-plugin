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

import io.github.rejeb.dataform.language.lineage.layout.NodePosition;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphCanvasHitTestTest {

    private static Map<String, NodePosition> positions() {
        Map<String, NodePosition> p = new LinkedHashMap<>();
        p.put("a", new NodePosition("a", 0, 0, 0));
        p.put("b", new NodePosition("b", 300, 100, 1));
        return p;
    }

    @Test
    void pointInsideNodeReturnsItsId() {
        assertEquals("b", GraphCanvas.hitTest(positions(), 180, 44, 320, 120));
        assertEquals("a", GraphCanvas.hitTest(positions(), 180, 44, 10, 10));
    }

    @Test
    void pointInEmptySpaceReturnsNull() {
        assertNull(GraphCanvas.hitTest(positions(), 180, 44, 250, 250));
    }
}
