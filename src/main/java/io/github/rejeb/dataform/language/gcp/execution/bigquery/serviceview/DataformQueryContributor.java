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
import com.intellij.execution.services.ServiceViewGroupingContributor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import io.github.rejeb.dataform.language.DataformIcons;
import io.github.rejeb.dataform.language.gcp.execution.bigquery.QueryResultsRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Contributor of the query runs to the Services view, registered in plugin.xml. Every run is listed
 * under one Dataform Queries node, which the view shows whether or not it groups by service type.
 */
public final class DataformQueryContributor
        implements ServiceViewGroupingContributor<QueryResultNode, QueryGroup> {

    private static final String ROOT_NAME = "Dataform";

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new SimpleServiceViewDescriptor(ROOT_NAME, DataformIcons.QUERIES, QueryGroup.NAME) {

            @Override
            public @NotNull ActionGroup getToolbarActions() {
                return new DefaultActionGroup(new RemoveFinishedQueriesAction());
            }
        };
    }

    @Override
    public @NotNull List<QueryResultNode> getServices(@NotNull Project project) {
        return QueryResultsRegistry.getInstance(project)
                .getAll()
                .stream()
                .map(run -> new QueryResultNode(project, run))
                .toList();
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(
            @NotNull Project project,
            @NotNull QueryResultNode node
    ) {
        return node.getViewDescriptor();
    }

    @Override
    public @NotNull List<QueryGroup> getGroups(@NotNull QueryResultNode node) {
        return List.of(node.group());
    }

    @Override
    public @NotNull ServiceViewDescriptor getGroupDescriptor(@NotNull QueryGroup group) {
        return group.getViewDescriptor();
    }
}
