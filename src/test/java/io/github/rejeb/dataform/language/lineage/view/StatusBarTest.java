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
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import io.github.rejeb.dataform.language.lineage.model.LineageModel;

import java.util.List;

public class StatusBarTest extends BasePlatformTestCase {

    public void testFileStatusCountsTheNodesAndTellsHowToOpenOne() {
        StatusBar bar = StatusBar.forFile(LineageViewFixtures.model());
        List<String> texts = LineageViewFixtures.all(bar, JBLabel.class).stream().map(JBLabel::getText).toList();
        assertTrue(texts.toString(), texts.contains("3 nodes · double-click a node to open its file"));
    }

    public void testFileStatusShowsNoZoom() {
        StatusBar bar = StatusBar.forFile(LineageViewFixtures.model());
        bar.setZoom(80);
        for (JBLabel label : LineageViewFixtures.all(bar, JBLabel.class)) {
            assertFalse(label.getText(), label.getText().contains("%"));
        }
    }

    public void testProjectStatusCountsTheShownNodes() {
        StatusBar bar = new StatusBar(LineageViewFixtures.model());
        assertEquals("3 of 3 nodes", texts(bar).getFirst());
    }

    public void testProjectStatusTellsThatANodeIsSelected() {
        LineageModel model = LineageViewFixtures.model();
        StatusBar bar = new StatusBar(model);
        model.toggleSchema("b");
        model.select(LineageViewFixtures.ALPHA.id());
        assertEquals("2 of 3 nodes · 1 selected", texts(bar).getFirst());
    }

    public void testProjectStatusShowsOnlyTheZoomOnTheRight() {
        LineageModel model = LineageViewFixtures.model();
        StatusBar bar = new StatusBar(model);
        model.select(LineageViewFixtures.ALPHA.id());
        bar.setZoom(80);
        assertEquals("80%", texts(bar).getLast());
    }

    private static List<String> texts(StatusBar bar) {
        return LineageViewFixtures.all(bar, JBLabel.class).stream().map(JBLabel::getText).toList();
    }

    public void testProjectStatusKeepsTheZoom() {
        StatusBar bar = new StatusBar(LineageViewFixtures.model());
        bar.setZoom(80);
        assertTrue(LineageViewFixtures.all(bar, JBLabel.class).stream().anyMatch(l -> l.getText().endsWith("80%")));
    }

    public void testStatusUsesTheSmallFontOnAPrototypeHighRow() {
        StatusBar bar = StatusBar.forFile(LineageViewFixtures.model());
        for (JBLabel label : LineageViewFixtures.all(bar, JBLabel.class)) {
            assertEquals(JBFont.medium().getSize(), label.getFont().getSize());
        }
        assertEquals(JBUI.scale(24), bar.getPreferredSize().height);
    }
}
