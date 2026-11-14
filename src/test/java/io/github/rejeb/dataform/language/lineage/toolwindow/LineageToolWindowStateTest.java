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

import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException.Kind;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexLineageException;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.graph.LineageNode;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;
import io.github.rejeb.dataform.language.lineage.model.LinkRef;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures;
import io.github.rejeb.dataform.language.lineage.view.LineageViewFixtures;
import org.junit.jupiter.api.Test;

import static io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowState.CANNOT_ANSWER;
import static io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowState.DATAFORM_SOURCE;
import static io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowState.NO_EDITOR;
import static io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowState.NO_SELECTION;
import static io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowState.READY;
import static io.github.rejeb.dataform.language.lineage.toolwindow.LineageToolWindowState.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LineageToolWindowStateTest {

    private static LineageModel recorded() {
        LineageModel model = LineageViewFixtures.model();
        DataplexView view = DataplexViewFixtures.view(model.graph(), "p.a.alpha", "other.landing.raw", "p.a.alpha");
        model.setSource(LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
        return model;
    }

    @Test
    void noLineageEditorIsNoEditor() {
        assertEquals(NO_EDITOR, of(null, null));
    }

    @Test
    void theDataformSourceHasNothingToShow() {
        LineageModel model = LineageViewFixtures.model();
        model.select(LineageViewFixtures.ALPHA.id());

        assertEquals(DATAFORM_SOURCE, of(model, null));
    }

    @Test
    void aFailureComesBeforeTheSelection() {
        LineageModel model = recorded();
        model.select(LineageViewFixtures.ALPHA.id());

        assertEquals(CANNOT_ANSWER, of(model, Kind.PERMISSION_DENIED));
    }

    @Test
    void knowledgeCatalogWithoutASelectionAsksForOne() {
        assertEquals(NO_SELECTION, of(recorded(), null));
    }

    @Test
    void knowledgeCatalogBeforeAnythingIsLoadedAsksForASelection() {
        LineageModel model = LineageViewFixtures.model();
        model.select(LineageViewFixtures.ALPHA.id());
        model.setSource(LineageSource.DATAPLEX);

        assertEquals(NO_SELECTION, of(model, null));
    }

    @Test
    void aSelectedTableIsReady() {
        LineageModel model = recorded();
        model.select(LineageViewFixtures.ALPHA.id());

        assertEquals(READY, of(model, null));
    }

    @Test
    void aSelectedLinkIsReady() {
        LineageModel model = recorded();
        model.selectLink(new LinkRef(LineageNode.idOf("other.landing.raw"), LineageViewFixtures.ALPHA.id()));

        assertEquals(READY, of(model, null));
    }

    @Test
    void theReasonOfAFailureIsSaidInWords() {
        assertEquals("Knowledge Catalog cannot answer: the Data Lineage API is not enabled",
                LineageToolWindowState.cannotAnswer(new DataplexLineageException(Kind.API_DISABLED, "403", null)));
        assertEquals("Knowledge Catalog cannot answer: your account cannot read the lineage",
                LineageToolWindowState.cannotAnswer(new DataplexLineageException(Kind.PERMISSION_DENIED, "403", null)));
        assertEquals("Knowledge Catalog cannot answer: you are not signed in",
                LineageToolWindowState.cannotAnswer(new DataplexLineageException(Kind.NOT_SIGNED_IN, "401", null)));
        assertEquals("Knowledge Catalog cannot answer: connection reset",
                LineageToolWindowState.cannotAnswer(new IllegalStateException("connection reset")));
        assertEquals("Knowledge Catalog cannot answer: the request failed",
                LineageToolWindowState.cannotAnswer(new IllegalStateException()));
    }
}
