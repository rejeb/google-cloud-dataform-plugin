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
package io.github.rejeb.dataform.language.schema.sql;

import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DataformTableSchemaRefreshFutureTest extends BasePlatformTestCase {

    private void installCurrentGraph(@NotNull CompiledGraph graph) {
        ServiceContainerUtil.replaceService(getProject(), DataformCompilationService.class,
                new StubCompilationService(graph), getTestRootDisposable());
    }

    public void testRefreshFutureCompletesForAnEmptyGraph() throws Exception {
        CompiledGraph graph = new CompiledGraph();
        installCurrentGraph(graph);

        SchemaRefreshOutcome outcome = DataformTableSchemaService.getInstance(getProject())
                .refresh(graph, false, Set.of())
                .get(30, TimeUnit.SECONDS);

        assertFalse("a graph without project config is not extracted", outcome.extracted());
    }

    public void testStaleGraphCompletesWithoutWaitingForAnExtraction() throws Exception {
        installCurrentGraph(new CompiledGraph());
        CountDownLatch extracted = new CountDownLatch(1);
        getProject().getMessageBus().connect(getTestRootDisposable())
                .subscribe(DataformSchemaEvent.TOPIC, (DataformSchemaEvent) extracted::countDown);

        CompletableFuture<SchemaRefreshOutcome> future = DataformTableSchemaService.getInstance(getProject())
                .refresh(new CompiledGraph(), false, Set.of());

        assertTrue(future.isDone());
        assertEquals(SchemaRefreshOutcome.NOT_RUN, future.get());
        assertFalse("a graph older than the current one is not extracted",
                extracted.await(3, TimeUnit.SECONDS));
    }

    private record StubCompilationService(CompiledGraph graph) implements DataformCompilationService {

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

        @Override
        public long getModificationCount() {
            return 0;
        }
    }
}
