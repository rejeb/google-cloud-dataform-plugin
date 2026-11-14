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

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.lineage.dataplex.DataplexView;
import io.github.rejeb.dataform.language.lineage.dataplex.Freshness;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.model.LineageSource;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class FreshnessTipsTest extends BasePlatformTestCase {

    private LineageModel model;
    private DataplexViewFixtures.ScriptedService service;
    private DataplexViewFixtures.ManualExecutor executor;
    private FreshnessTips tips;
    private final List<String> ready = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        model = LineageViewFixtures.model();
        service = new DataplexViewFixtures.ScriptedService();
        executor = new DataplexViewFixtures.ManualExecutor();
        tips = new FreshnessTips(model, service, executor,
                Clock.fixed(DataplexViewFixtures.LOADED_AT, ZoneId.of("UTC")));
        show(DataplexViewFixtures.view(model.graph(), "p.a.alpha", "other.landing.raw", "p.a.alpha"));
    }

    private void show(DataplexView view) {
        model.setSource(LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
    }

    private void settle() {
        executor.runAll();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    public void testTheFirstHoverSaysLoadingAndAsksOnce() {
        String alpha = LineageViewFixtures.ALPHA.id();

        assertEquals("Loading…", tips.text(alpha, () -> ready.add(alpha)));
        assertEquals("Loading…", tips.text(alpha, () -> ready.add(alpha)));

        assertEquals(1, executor.tasks.size());
    }

    public void testTheAnswerReplacesLoadingAndTellsTheCaller() {
        String alpha = LineageViewFixtures.ALPHA.id();
        service.freshness = new Freshness(DataplexViewFixtures.LOADED_AT.minusSeconds(7_200), "Scheduled query", false);
        tips.text(alpha, () -> ready.add(alpha));

        settle();

        assertEquals(List.of(alpha), ready);
        assertEquals("Last write: Today 08:00 · 2 hours ago\nWritten by: Scheduled query\n"
                + "Knowledge Catalog can lag by up to 24 hours", tips.text(alpha, () -> ready.add("again")));
        assertTrue(executor.tasks.isEmpty());
    }

    public void testANewViewForgetsTheAnswers() {
        String alpha = LineageViewFixtures.ALPHA.id();
        tips.text(alpha, () -> { });
        settle();

        show(DataplexViewFixtures.view(LineageViewFixtures.model().graph(), "p.a.alpha", "other.landing.raw", "p.a.alpha"));

        assertEquals("Loading…", tips.text(alpha, () -> { }));
    }

    public void testAFailureIsShownInTheTooltip() {
        String alpha = LineageViewFixtures.ALPHA.id();
        service.freshnessFailure = new RuntimeException("boom");
        tips.text(alpha, () -> { });

        settle();

        assertEquals("Knowledge Catalog cannot answer: boom", tips.text(alpha, () -> { }));
    }

    public void testWithoutARecordedLineageThereIsNoTooltip() {
        model.setSource(LineageSource.DATAFORM);

        assertNull(tips.text(LineageViewFixtures.ALPHA.id(), () -> { }));
    }
}
