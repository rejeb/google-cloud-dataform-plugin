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

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.intellij.ui.content.Content;
import io.github.rejeb.dataform.language.lineage.model.LineageModel;
import io.github.rejeb.dataform.language.lineage.view.DataplexViewFixtures;
import io.github.rejeb.dataform.language.lineage.view.LineageViewFixtures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class LineageToolWindowImplTest extends BasePlatformTestCase {

    private RecordingWindow window;
    private LineageToolWindowImpl toolWindow;
    private final List<String> catalogShown = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        window = new RecordingWindow(getProject());
        toolWindow = new LineageToolWindowImpl(() -> window, DataplexViewFixtures.ScriptedService::new,
                new DataplexViewFixtures.ManualExecutor(), Clock.fixed(DataplexViewFixtures.LOADED_AT, ZoneOffset.UTC),
                () -> catalogShown.add("shown"), url -> {
        }, node -> {
        }, message -> {
        });
    }

    private String selectedTab() {
        Content selected = window.getContentManager().getSelectedContent();
        return selected == null ? null : selected.getDisplayName();
    }

    public void testTheToolWindowIsNotAvailableBeforeTheFirstShow() {
        toolWindow.attach(LineageViewFixtures.model());

        assertFalse(new LineageToolWindowFactory().shouldBeAvailable(getProject()));
        assertFalse(window.isAvailable());
        assertFalse(window.isVisible());
    }

    public void testShowMakesItAvailableAndVisibleWithoutActivatingIt() {
        toolWindow.show(null);

        assertTrue(window.isAvailable());
        assertTrue(window.isVisible());
        assertFalse(window.activated);
    }

    public void testShowWithATabSelectsThatContent() {
        toolWindow.show(LineageToolWindowTab.RUNS);

        assertEquals("Runs", selectedTab());
    }

    public void testShowWithTheTraceTabSelectsTheTrace() {
        toolWindow.show(LineageToolWindowTab.TRACE);

        assertEquals("Trace", selectedTab());
    }

    public void testTheTraceFollowsTheModelToo() {
        LineageModel model = LineageViewFixtures.model();
        toolWindow.attach(model);
        toolWindow.show(null);
        assertEquals(LineageToolWindowState.DATAFORM_SOURCE, toolWindow.tracePanel().state());

        toolWindow.detach(model);

        assertEquals(LineageToolWindowState.NO_EDITOR, toolWindow.tracePanel().state());
    }

    public void testTheDifferencesTabCarriesTheCount() {
        LineageModel model = LineageViewFixtures.model();
        io.github.rejeb.dataform.language.lineage.dataplex.DataplexView view = DataplexViewFixtures.view(model.graph(),
                "p.a.alpha", "other.landing.raw", "p.a.alpha");
        model.setSource(io.github.rejeb.dataform.language.lineage.model.LineageSource.DATAPLEX);
        model.setDataplexView(view);
        model.setGraph(view.graph());
        toolWindow.attach(model);
        toolWindow.show(LineageToolWindowTab.DIFFERENCES);
        assertEquals("Differences", selectedTab());

        model.setDifferences(List.of(new io.github.rejeb.dataform.language.lineage.dataplex.Difference(
                io.github.rejeb.dataform.language.lineage.dataplex.Difference.Kind.RECORDED_NOT_DECLARED,
                io.github.rejeb.dataform.language.lineage.graph.LineageNode.idOf("other.landing.raw"),
                LineageViewFixtures.ALPHA.id())));

        assertEquals(LineageToolWindowTab.differencesTitle(1), selectedTab());
        assertTrue(selectedTab().contains("Differences") && selectedTab().contains("1"));
    }

    public void testShowWithoutATabKeepsTheCurrentContent() {
        toolWindow.show(LineageToolWindowTab.TRACE);
        Content shown = window.getContentManager().getSelectedContent();

        toolWindow.show(null);

        assertSame(shown, window.getContentManager().getSelectedContent());
    }

    public void testHideHidesItAndKeepsItAvailable() {
        toolWindow.show(null);

        toolWindow.hide();

        assertFalse(window.isVisible());
        assertTrue(window.isAvailable());
    }

    public void testADetachedModelLeavesTheNoEditorEmptyState() {
        LineageModel model = LineageViewFixtures.model();
        toolWindow.attach(model);
        toolWindow.show(null);
        assertEquals(LineageToolWindowState.DATAFORM_SOURCE, toolWindow.runsPanel().state());

        toolWindow.detach(model);

        assertEquals(LineageToolWindowState.NO_EDITOR, toolWindow.runsPanel().state());
        assertEquals("Runs, trace and differences come from Knowledge Catalog", EmptyStatusTexts.primary(toolWindow.runsPanel().emptyStatus()));
        assertEquals("Show the Knowledge Catalog lineage", EmptyStatusTexts.link(toolWindow.runsPanel().emptyStatus()));
    }

    public void testTheModelChangedLastIsTheOneShown() {
        LineageModel first = LineageViewFixtures.model();
        LineageModel second = LineageViewFixtures.model();
        toolWindow.attach(first);
        toolWindow.attach(second);
        toolWindow.show(null);
        assertSame(second, toolWindow.shownModel());

        first.select(LineageViewFixtures.ALPHA.id());
        assertSame(first, toolWindow.shownModel());

        toolWindow.detach(first);
        assertSame(second, toolWindow.shownModel());
    }

    public void testADetachedModelIsNoLongerListenedTo() {
        LineageModel first = LineageViewFixtures.model();
        LineageModel second = LineageViewFixtures.model();
        toolWindow.attach(first);
        toolWindow.attach(second);
        toolWindow.detach(first);

        first.select(LineageViewFixtures.ALPHA.id());

        assertSame(second, toolWindow.shownModel());
    }

    public void testTheFactoryFillsTheToolWindowWithTheRunsTab() {
        RecordingWindow real = new RecordingWindow(getProject());

        new LineageToolWindowFactory().createToolWindowContent(getProject(), real);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals(List.of("Runs", "Trace", "Differences"), java.util.Arrays.stream(real.getContentManager().getContents())
                .map(Content::getDisplayName).toList());
        assertFalse(real.getContentManager().getContents()[2].isCloseable());
        assertFalse(real.getContentManager().getContents()[0].isCloseable());
        assertFalse(real.getContentManager().getContents()[1].isCloseable());
    }

    public void testShowDoesNothingWithoutAToolWindow() {
        LineageToolWindowImpl missing = new LineageToolWindowImpl(() -> null, DataplexViewFixtures.ScriptedService::new,
                new DataplexViewFixtures.ManualExecutor(), Clock.systemUTC(), () -> {
        }, url -> {
        }, node -> {
        }, message -> {
        });

        missing.show(LineageToolWindowTab.RUNS);
        missing.hide();

        assertNull(missing.runsPanel());
    }

    private static final class RecordingWindow extends ToolWindowHeadlessManagerImpl.MockToolWindow {
        private boolean available;
        private boolean visible;
        private boolean activated;

        private RecordingWindow(@NotNull Project project) {
            super(project);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void setAvailable(boolean value) {
            available = value;
        }

        @Override
        public void setAvailable(boolean value, @Nullable Runnable runnable) {
            available = value;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void show(@Nullable Runnable runnable) {
            visible = true;
        }

        @Override
        public void hide(@Nullable Runnable runnable) {
            visible = false;
        }

        @Override
        public void activate(@Nullable Runnable runnable) {
            activated = true;
        }

        @Override
        public void activate(@Nullable Runnable runnable, boolean autoFocusContents) {
            activated = true;
        }

        @Override
        public void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced) {
            activated = true;
        }
    }
}
