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
package io.github.rejeb.dataform.language.gcp.execution.bigquery.serviceview;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.BigQueryJobResult;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;

import javax.swing.JComponent;

public class QueryResultPanelsTest extends BasePlatformTestCase {

    private static final String TABLE = "my-project.analytics.orders";

    @Override
    protected void tearDown() throws Exception {
        try {
            QueryResultsRegistry registry = QueryResultsRegistry.getInstance(getProject());
            registry.getAll().forEach(run -> registry.remove(run.tableName()));
            QueryResultPanels.getInstance(getProject()).prune();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testARunKeepsItsPanelWhileItIsListed() {
        QueryRun run = started();

        JComponent first = QueryResultPanels.getInstance(getProject()).panelFor(run);

        assertSame(first, QueryResultPanels.getInstance(getProject()).panelFor(run));
    }

    public void testTheFinishedRunGetsANewPanelAndTheRunningOneIsDisposed() {
        QueryResultsRegistry registry = QueryResultsRegistry.getInstance(getProject());
        QueryRun run = started();
        QueryExecutionPanel running = (QueryExecutionPanel) QueryResultPanels.getInstance(getProject()).panelFor(run);
        QueryRun finished = registry.finish(run, new BigQueryJobResult(TABLE, null, null, null), false);

        JComponent done = QueryResultPanels.getInstance(getProject()).panelFor(finished);

        assertNotSame(running, done);
        assertTrue(Disposer.isDisposed(running));
    }

    public void testPruningDisposesThePanelsOfRemovedRuns() {
        QueryRun run = started();
        QueryExecutionPanel panel = (QueryExecutionPanel) QueryResultPanels.getInstance(getProject()).panelFor(run);
        QueryResultsRegistry.getInstance(getProject()).remove(TABLE);

        QueryResultPanels.getInstance(getProject()).prune();

        assertTrue(Disposer.isDisposed(panel));
    }

    public void testTheServicesNodeShowsTheCachedPanel() {
        QueryRun run = started();

        assertSame(QueryResultPanels.getInstance(getProject()).panelFor(run),
                new DataformServiceViewDescriptor(getProject(), run).getContentComponent());
    }

    private QueryRun started() {
        QueryRun run = QueryRun.running(TABLE, "SELECT 1", null, 0, () -> {
        });
        QueryResultsRegistry.getInstance(getProject()).start(run);
        return run;
    }
}
