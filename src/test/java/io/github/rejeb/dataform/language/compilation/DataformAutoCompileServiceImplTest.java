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
package io.github.rejeb.dataform.language.compilation;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import io.github.rejeb.dataform.language.schema.sql.DataformTableSchemaService;
import io.github.rejeb.dataform.language.schema.sql.model.DataformDasTable;
import io.github.rejeb.dataform.language.settings.DataformToolsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DataformAutoCompileServiceImplTest extends BasePlatformTestCase {

    public void testServiceIsRegisteredAndResolvable() {
        assertNotNull(DataformAutoCompileService.getInstance(getProject()));
    }

    public void testScheduleIsANoOpWhenCompileOnSaveIsDisabled() {
        DataformToolsSettings settings = DataformToolsSettings.getInstance();
        boolean previous = settings.isCompileOnSave();
        try {
            settings.setCompileOnSave(false);
            DataformAutoCompileService.getInstance(getProject()).scheduleCompile();
        } finally {
            settings.setCompileOnSave(previous);
        }
    }

    public void testCompileOnSaveDefaultsToEnabled() {
        assertTrue(DataformToolsSettings.getInstance().isCompileOnSave());
    }

    public void testTableSchemasAreRefreshedAfterASaveTriggeredCompilation() throws Exception {
        CompiledGraph graph = new CompiledGraph();
        RecordingSchemaService schemas = installServices(graph);

        DataformAutoCompileService.getInstance(getProject()).scheduleCompile();

        assertTrue("saving a file must refresh the table schemas",
                schemas.called.await(30, TimeUnit.SECONDS));
        assertSame("the freshly compiled graph must be the one extracted", graph, schemas.graph);
    }

    public void testASaveOnlyReExtractsTheActionsItChanged() throws Exception {
        RecordingSchemaService schemas = installServices(new CompiledGraph());

        DataformAutoCompileService.getInstance(getProject()).scheduleCompile();

        assertTrue(schemas.called.await(30, TimeUnit.SECONDS));
        assertFalse("a save must not force a full re-extraction", schemas.forceRefresh);
    }

    public void testNoSchemaRefreshIsRequestedWhenTheCompilationProducedNoGraph() throws Exception {
        RecordingSchemaService schemas = installServices(null);

        DataformAutoCompileService.getInstance(getProject()).scheduleCompile();

        assertFalse("a failed compilation has no graph to extract schemas from",
                schemas.called.await(2, TimeUnit.SECONDS));
    }

    public void testAnEditDoesNotCompileWhileTheUserIsStillTyping() throws Exception {
        RecordingSchemaService schemas = installServices(new CompiledGraph());
        DataformAutoCompileService service = DataformAutoCompileService.getInstance(getProject());

        service.scheduleCompileAfterEdit();

        assertFalse("a compilation must wait for the user to stop typing",
                schemas.called.await(1, TimeUnit.SECONDS));
    }

    public void testAnEditCompilesOnceTheQuietPeriodHasElapsed() throws Exception {
        RecordingSchemaService schemas = installServices(new CompiledGraph());
        DataformAutoCompileService service = DataformAutoCompileService.getInstance(getProject());

        service.scheduleCompileAfterEdit();

        assertTrue("a compilation must follow the quiet period",
                schemas.called.await(DataformAutoCompileServiceImpl.QUIET_PERIOD_MS * 4,
                        TimeUnit.MILLISECONDS));
    }

    private RecordingSchemaService installServices(@Nullable CompiledGraph graph) {
        ServiceContainerUtil.replaceService(getProject(), DataformCompilationService.class,
                new StubCompilationService(graph), getTestRootDisposable());
        RecordingSchemaService schemas = new RecordingSchemaService();
        ServiceContainerUtil.replaceService(getProject(), DataformTableSchemaService.class,
                schemas, getTestRootDisposable());
        return schemas;
    }

    private static final class RecordingSchemaService implements DataformTableSchemaService {

        private final CountDownLatch called = new CountDownLatch(1);
        private volatile CompiledGraph graph;
        private volatile boolean forceRefresh;

        @Override
        public void refreshAsync(@NotNull CompiledGraph graph, boolean forceRefresh) {
            refreshAsync(graph, forceRefresh, Set.of());
        }

        @Override
        public void refreshAsync(@NotNull CompiledGraph graph,
                                 boolean forceRefresh,
                                 @NotNull Set<String> failedFileNames) {
            this.graph = graph;
            this.forceRefresh = forceRefresh;
            called.countDown();
        }

        @Override
        public @NotNull Map<String, DataformDasTable> getAllTables() {
            return Map.of();
        }

        @Override
        public @Nullable State getState() {
            return new State();
        }

        @Override
        public void loadState(@NotNull State state) {
        }

        @Override
        public long getModificationCount() {
            return 0;
        }
    }

    private static final class StubCompilationService implements DataformCompilationService {

        private final CompiledGraph graph;

        private StubCompilationService(@Nullable CompiledGraph graph) {
            this.graph = graph;
        }

        @Override
        public CompiledGraph compile(boolean forceRefresh) {
            return graph;
        }

        @Override
        public CompiledGraph getCompiledGraph() {
            return graph;
        }

        @Override
        public State getState() {
            return new State();
        }

        @Override
        public void loadState(@NotNull State state) {
        }

        @Override
        public void dispose() {
        }
    }
}
