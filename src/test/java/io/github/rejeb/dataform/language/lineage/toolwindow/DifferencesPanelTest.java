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
package io.github.rejeb.dataform.language.lineage.toolwindow;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.Difference;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures;
import io.github.rejeb.dataform.language.lineage.view.LineageViewFixtures;

import javax.swing.JComponent;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class DifferencesPanelTest extends BasePlatformTestCase {

    private static final String RAW = LineageNode.idOf("other.landing.raw");
    private static final String ALPHA = LineageViewFixtures.ALPHA.id();
    private static final String BETA = LineageViewFixtures.BETA.id();

    private LineageModel model;
    private DifferencesPanel panel;
    private final List<String> opened = new ArrayList<>();
    private final List<String> told = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model = LineageViewFixtures.model();
        panel = new DifferencesPanel(() -> {
        }, node -> opened.add(node.id()), told::add);
        panel.setCountListener(counts::add);
        model.addListener(panel::show);
    }

    private void recorded(Difference... differences) {
        DataplexView view = DataplexViewFixtures.view(model.graph(), "p.a.alpha",
                "other.landing.raw", "p.a.alpha", "p.a.alpha", "p.b.beta");
        model.setSource(LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
        model.setDifferences(List.of(differences));
    }

    private List<String> texts(JComponent item) {
        List<String> texts = new ArrayList<>();
        LineageViewFixtures.all(item, JBLabel.class).forEach(label -> texts.add(label.getText()));
        return texts;
    }

    private List<String> links(JComponent item) {
        return LineageViewFixtures.all(item, ActionLink.class).stream().map(ActionLink::getText).toList();
    }

    private void follow(JComponent item, String link) {
        LineageViewFixtures.all(item, ActionLink.class).stream().filter(l -> link.equals(l.getText())).findFirst()
                .orElseThrow().doClick();
    }

    public void testWithTheDataformSourceItPointsAtKnowledgeCatalog() {
        panel.show(model);

        assertEquals(LineageToolWindowState.DATAFORM_SOURCE, panel.state());
        assertEquals("Show the Knowledge Catalog lineage", EmptyStatusTexts.link(panel.emptyStatus()));
        assertTrue(panel.items().isEmpty());
        assertEquals("", panel.header());
    }

    public void testBeforeAnythingIsLoadedItAsksForATable() {
        model.setSource(LineageSource.DATAPLEX);

        assertEquals(LineageToolWindowState.NO_SELECTION, panel.state());
        assertEquals("Select a table in the lineage view", EmptyStatusTexts.primary(panel.emptyStatus()));
    }

    public void testTheTabSaysDifferencesCoverTheLoadedGraphOnly() {
        recorded(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, RAW, ALPHA));

        assertEquals(LineageToolWindowState.READY, panel.state());
        assertEquals("Differences between Dataform and Knowledge Catalog", panel.header());
        assertEquals("On the loaded graph only", panel.note());
    }

    public void testItNeedsNoSelection() {
        recorded(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, RAW, ALPHA));

        assertNull(model.selectedId());
        assertEquals(1, panel.items().size());
    }

    public void testAgreeingGraphsSaySo() {
        recorded();

        assertTrue(panel.items().isEmpty());
        assertEquals("Dataform and Knowledge Catalog agree", EmptyStatusTexts.primary(panel.emptyStatus()));
        assertEquals("Differences between Dataform and Knowledge Catalog", panel.header());
    }

    public void testAnItemNamesItsKindItsLinkAndWhy() {
        recorded(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, RAW, ALPHA),
                new Difference(Difference.Kind.DECLARED_NOT_RECORDED, ALPHA, BETA));

        assertEquals(List.of("Recorded, not declared", "landing.raw → a.alpha",
                        "The runs of alpha read raw in the last 30 days, but no ref() and no declaration points to it."),
                texts(panel.items().get(0)));
        assertEquals(List.of("Declared, not recorded", "a.alpha → b.beta",
                        "beta declares alpha, but no run read it in the last 30 days."),
                texts(panel.items().get(1)));
    }

    public void testARecordedTargetNoActionDeclaresIsExplained() {
        DataplexView view = DataplexViewFixtures.view(model.graph(), "p.a.alpha", "p.a.alpha", "p.a.alpha_backup");
        model.setSource(LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
        model.setDifferences(List.of(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, ALPHA, LineageNode.idOf("p.a.alpha_backup"))));

        assertEquals("Something outside this repository writes alpha_backup from alpha, in a dataset this repository writes to.",
                texts(panel.items().getFirst()).get(2));
        assertEquals(List.of("Open SQLX"), links(panel.items().getFirst()));
    }

    public void testTheCountIsToldWhenItChanges() {
        recorded(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, RAW, ALPHA));
        model.setHover(ALPHA);
        model.setDifferences(List.of());
        model.setSource(LineageSource.DATAFORM);

        assertEquals(List.of(1, 0), counts);
    }

    public void testSelectingADifferenceSelectsItsLink() {
        recorded(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, RAW, ALPHA));
        JComponent item = panel.items().getFirst();

        item.dispatchEvent(new MouseEvent(item, MouseEvent.MOUSE_CLICKED, 0, InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false,
                MouseEvent.BUTTON1));

        assertEquals(new LinkRef(RAW, ALPHA), model.selectedLink());
    }

    public void testSelectingADeclaredOnlyDifferenceTurnsTheDifferencesOnFirst() {
        recorded(new Difference(Difference.Kind.DECLARED_NOT_RECORDED, ALPHA, BETA));
        assertFalse(model.differencesShown());

        panel.select(0);

        assertTrue(model.differencesShown());
    }

    public void testARecordedSourceNoActionDeclaresOffersDeclareTheSource() {
        recorded(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, RAW, ALPHA));
        JComponent item = panel.items().getFirst();
        assertEquals(List.of("Open SQLX", "Declare the source"), links(item));

        follow(item, "Declare the source");

        assertEquals("config {\n  type: \"declaration\",\n  database: \"other\",\n  schema: \"landing\",\n  name: \"raw\"\n}\n",
                CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
        assertEquals(List.of("Declaration of other.landing.raw copied. Paste it into a new SQLX file (New > SQLX File)."), told);
    }

    public void testARecordedSourceThatIsARepositoryTableOffersOnlyOpenSqlx() {
        DataplexView view = DataplexViewFixtures.view(model.graph(), "p.a.gamma", "p.a.alpha", "p.a.gamma");
        model.setSource(LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
        model.setDifferences(List.of(new Difference(Difference.Kind.RECORDED_NOT_DECLARED, ALPHA, LineageViewFixtures.GAMMA.id())));
        JComponent item = panel.items().getFirst();

        assertEquals(List.of("Open SQLX"), links(item));
        follow(item, "Open SQLX");

        assertEquals(List.of(LineageViewFixtures.GAMMA.id()), opened);
    }

    public void testOpenSqlxOfADeclaredOnlyDifferenceOpensTheTableThatDeclaresIt() {
        recorded(new Difference(Difference.Kind.DECLARED_NOT_RECORDED, ALPHA, BETA));

        follow(panel.items().getFirst(), "Open SQLX");

        assertEquals(List.of(BETA), opened);
    }
}
