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

import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * Keeps one detail panel per listed query run, so the Services view shows the same panel each
 * time it asks for it and the data grid of a run is loaded once.
 */
public interface QueryResultPanels {

    static @NotNull QueryResultPanels getInstance(@NotNull Project project) {
        return project.getService(QueryResultPanels.class);
    }

    /**
     * The panel of the given run, created on the first call. Must be called on the EDT.
     */
    @NotNull JComponent panelFor(@NotNull QueryRun run);

    /**
     * Disposes the panels of the runs that are no longer listed. Must be called on the EDT.
     */
    void prune();
}
