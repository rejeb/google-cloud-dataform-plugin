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

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryRun;
import org.jetbrains.annotations.NotNull;

/**
 * A query run in the Services view, the same node from the start of the run to its result.
 */
public final class QueryResultNode {

    private final Project project;
    private final QueryRun run;

    public QueryResultNode(@NotNull Project project, @NotNull QueryRun run) {
        this.project = project;
        this.run = run;
    }

    /**
     * The row of the run in the tree and its detail panel.
     */
    public @NotNull ServiceViewDescriptor getViewDescriptor() {
        return new DataformServiceViewDescriptor(project, run);
    }

    /**
     * The Dataform Queries node the run is listed under.
     */
    public @NotNull QueryGroup group() {
        return new QueryGroup(project);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryResultNode other)) return false;
        return run.tableName().equals(other.run.tableName());
    }

    @Override
    public int hashCode() {
        return run.tableName().hashCode();
    }
}
