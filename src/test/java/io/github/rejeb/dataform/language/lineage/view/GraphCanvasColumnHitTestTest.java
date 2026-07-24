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

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphCanvasColumnHitTestTest {

    private static Map<String, Rectangle> bounds() {
        Map<String, Rectangle> b = new LinkedHashMap<>();
        b.put("p.d.src#amount", new Rectangle(0, 50, 180, 14));
        b.put("p.d.mid#total", new Rectangle(300, 150, 180, 14));
        return b;
    }

    @Test
    void pointInsideColumnRowReturnsItsId() {
        assertEquals("p.d.src#amount", GraphCanvas.columnHitTest(bounds(), 10, 55));
        assertEquals("p.d.mid#total", GraphCanvas.columnHitTest(bounds(), 320, 158));
    }

    @Test
    void pointInEmptySpaceReturnsNull() {
        assertNull(GraphCanvas.columnHitTest(bounds(), 250, 250));
    }
}
