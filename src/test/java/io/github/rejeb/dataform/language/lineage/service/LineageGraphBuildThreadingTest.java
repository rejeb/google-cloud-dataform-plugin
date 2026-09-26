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
package io.github.rejeb.dataform.language.lineage.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.rejeb.dataform.language.compilation.DataformCompilationService;
import io.github.rejeb.dataform.language.compilation.model.CompiledGraph;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A lineage build runs its analyses inside read actions whatever thread asks for it, and the graphs
 * one caller built are the ones the next caller gets.
 */
public class LineageGraphBuildThreadingTest extends BasePlatformTestCase {

    private ExecutorService executor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("lineage test", 3);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            executor.shutdownNow();
        } finally {
            super.tearDown();
        }
    }

    public void testTheParallelRunnerKeepsTheOrderOfTheTasksAndReadsUnderAReadAction() throws Exception {
        List<Supplier<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int index = i;
            tasks.add(() -> {
                assertTrue("an analysis reads PSI", ApplicationManager.getApplication().isReadAccessAllowed());
                return "unit-" + index;
            });
        }
        ParallelReadActionRunner runner = new ParallelReadActionRunner(getProject(), executor);

        List<String> results = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> runner.runAll(tasks))
                .get(10, TimeUnit.SECONDS);

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 20; i++) expected.add("unit-" + i);
        assertEquals(expected, results);
    }

    public void testAFailingAnalysisFailsTheRun() throws Exception {
        ParallelReadActionRunner runner = new ParallelReadActionRunner(getProject(), executor);
        List<Supplier<String>> tasks = List.of(() -> "fine", () -> {
            throw new IllegalStateException("broken SQL");
        });

        Throwable failure = ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                runner.runAll(tasks);
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        }).get(10, TimeUnit.SECONDS);

        assertTrue(String.valueOf(failure), failure instanceof IllegalStateException);
    }

    public void testGraphsBuiltWithoutAReadActionAreTheOnesTheNextCallerGets() throws Exception {
        CompiledGraph compiled = new CompiledGraph();
        setField(DataformCompilationService.getInstance(getProject()), "compiledGraph", compiled);
        LineageGraphService service = LineageGraphService.getInstance(getProject());

        LineageGraphService.Graphs built = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> service.graphs(compiled))
                .get(10, TimeUnit.SECONDS);

        assertNotNull(built.columnGraph());
        assertSame(built, service.graphs(compiled));
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
