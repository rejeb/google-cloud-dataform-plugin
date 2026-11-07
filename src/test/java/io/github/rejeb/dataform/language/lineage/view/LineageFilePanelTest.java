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

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.lineage.graph.LineageGraph;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.ui.PreviewHeader;

import javax.swing.JComponent;
import java.util.List;

public class LineageFilePanelTest extends BasePlatformTestCase {

    private static final String FILE = "definitions/analytics/orders_enriched.sqlx";
    private static final LineageNode MAIN = LineageViewFixtures.node("p.analytics.orders_enriched", "analytics", "table", List.of(), FILE);
    private static final LineageNode SOURCE = LineageViewFixtures.node("p.staging.stg_orders", "staging", "view", List.of(), "definitions/staging/stg_orders.sqlx");
    private static final LineageNode TARGET = LineageViewFixtures.node("p.analytics.daily_revenue", "analytics", "incremental", List.of(), "definitions/analytics/daily_revenue.sqlx");
    private static final LineageNode FAR = LineageViewFixtures.node("p.reporting.kpis", "reporting", "view", List.of(), "definitions/reporting/kpis.sqlx");

    private LineageFilePanel panel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LightVirtualFile file = new LightVirtualFile("orders_enriched.sqlx") {
            @Override
            public String getPath() {
                return "/project/" + FILE;
            }
        };
        panel = new LineageFilePanel(getProject(), file);
        panel.setLineage(LineageGraph.builder()
                .addNode(SOURCE).addNode(MAIN).addNode(TARGET).addNode(FAR)
                .addEdge(SOURCE.id(), MAIN.id())
                .addEdge(MAIN.id(), TARGET.id())
                .addEdge(TARGET.id(), FAR.id())
                .build());
    }

    public void testHeaderSaysWhoseDependenciesAreShown() {
        List<PreviewHeader> headers = LineageViewFixtures.all(panel, PreviewHeader.class);
        assertEquals(1, headers.size());
        assertEquals("Direct dependencies of orders_enriched.sqlx", headers.get(0).summaryText());
    }

    public void testHeaderOffersTheLayoutDirectionAndTheFullLineage() {
        PreviewHeader header = LineageViewFixtures.all(panel, PreviewHeader.class).get(0);
        List<AnAction> actions = LineageViewFixtures.all(header, JComponent.class).stream()
                .filter(ActionToolbar.class::isInstance)
                .map(ActionToolbar.class::cast)
                .flatMap(toolbar -> List.of(toolbar.getActionGroup().getChildren(null)).stream())
                .toList();
        assertEquals(List.of("Layout direction", "Open full lineage"),
                actions.stream().map(a -> a.getTemplatePresentation().getText()).toList());
        assertEquals(Boolean.TRUE, actions.get(1).getTemplatePresentation()
                .getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR));
    }

    public void testTheActionOfTheFileIsSelected() {
        assertEquals(MAIN.id(), panel.model().selectedId());
    }

    public void testStatusCountsTheShownNodes() {
        List<String> texts = LineageViewFixtures.all(panel, JBLabel.class).stream().map(JBLabel::getText).toList();
        assertTrue(texts.toString(), texts.contains("3 nodes · double-click a node to open its file"));
    }

    public void testMinimapIsLeftOut() {
        GraphCanvas canvas = LineageViewFixtures.all(panel, GraphCanvas.class).get(0);
        assertFalse(canvas.isMinimapShown());
    }
}
