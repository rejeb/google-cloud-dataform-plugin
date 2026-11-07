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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Caches the panels by run identity: a finished run is a new run, so it gets a new panel and the
 * panel of its running state is disposed.
 */
public final class QueryResultPanelsImpl implements QueryResultPanels, Disposable {

    private final Project project;
    private final Map<QueryRun, QueryExecutionPanel> panels = new IdentityHashMap<>();

    public QueryResultPanelsImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull JComponent panelFor(@NotNull QueryRun run) {
        prune();
        return panels.computeIfAbsent(run, listed -> new QueryExecutionPanel(project, listed));
    }

    @Override
    public void prune() {
        QueryResultsRegistry registry = QueryResultsRegistry.getInstance(project);
        panels.entrySet().removeIf(entry -> {
            if (registry.get(entry.getKey().tableName()) == entry.getKey()) {
                return false;
            }
            Disposer.dispose(entry.getValue());
            return true;
        });
    }

    @Override
    public void dispose() {
        panels.values().forEach(Disposer::dispose);
        panels.clear();
    }
}
