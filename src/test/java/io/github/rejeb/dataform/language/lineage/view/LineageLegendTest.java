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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;

import java.util.List;
import java.util.Set;

public class LineageLegendTest extends BasePlatformTestCase {

    public void testTypesFollowThePrototypeOrderThenTheOthers() {
        assertEquals(List.of("table", "view", "incremental", "declaration", "assertion", "operation", "custom"),
                LineageLegend.orderedTypes(Set.of("operation", "custom", "assertion", "declaration", "incremental", "view", "table")));
    }

    public void testTheLegendListsTheNodeTypesThenTheColourHints() {
        List<JBLabel> rows = LineageViewFixtures.all(LineageLegend.panel(List.of("view", "table")), JBLabel.class);

        assertEquals(List.of("Node types", "view", "table",
                        "Blue edges: upstream and downstream of the selected node",
                        "Yellow outline: search match, or upstream and downstream of the selected node"),
                rows.stream().map(JBLabel::getText).toList());
        assertInstanceOf(rows.get(1).getIcon(), TypeGlyphIcon.class);
        assertEquals("view", ((TypeGlyphIcon) rows.get(1).getIcon()).type());
    }
}
