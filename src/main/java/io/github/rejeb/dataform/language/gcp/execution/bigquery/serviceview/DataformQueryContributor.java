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

import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Root contributor registered in plugin.xml.
 * Visible in the Services tool window only when results exist.
 */
public final class DataformQueryContributor
        implements ServiceViewContributor<QueryResultNode> {

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new SimpleServiceViewDescriptor("Dataform Queries", AllIcons.Nodes.DataTables);
    }

    @Override
    public @NotNull List<QueryResultNode> getServices(@NotNull Project project) {
        return QueryResultsRegistry.getInstance(project)
                .getAll()
                .stream()
                .map(QueryResultNode::new)
                .toList();
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(
            @NotNull Project project,
            @NotNull QueryResultNode node
    ) {
        return node.getViewDescriptor(project);
    }
}
